package com.aquaassistant.place;

import com.aquaassistant.audit.AuditService;
import com.aquaassistant.common.BusinessException;
import com.aquaassistant.measurement.Measurement;
import com.aquaassistant.measurement.MeasurementRepository;
import com.aquaassistant.place.dto.PlaceDtos.MemberResponse;
import com.aquaassistant.place.dto.PlaceDtos.PlaceRequest;
import com.aquaassistant.place.dto.PlaceDtos.PlaceResponse;
import com.aquaassistant.profile.ProfileType;
import com.aquaassistant.profile.WaterProfile;
import com.aquaassistant.profile.WaterProfileRepository;
import com.aquaassistant.user.DemoAccounts;
import com.aquaassistant.user.User;
import com.aquaassistant.user.UserRepository;
import com.aquaassistant.user.UserSettings;
import com.aquaassistant.user.UserSettingsRepository;
import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Places of a person: create, edit, choose the active one, and share them with a code. */
@Service
public class PlaceService {

    /** Letters and digits that are not confused when read aloud or copied (no 0/O, 1/I/L). */
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_LENGTH = 8;
    private static final int MAX_OWN_PLACES = 30;

    private final PlaceRepository places;
    private final PlaceMemberRepository members;
    private final PlaceAccess access;
    private final WaterProfileRepository profiles;
    private final UserSettingsRepository settings;
    private final UserRepository users;
    private final MeasurementRepository measurements;
    private final AuditService audit;
    private final DemoAccounts demoAccounts;
    private final SecureRandom random = new SecureRandom();

    public PlaceService(PlaceRepository places, PlaceMemberRepository members, PlaceAccess access,
                        WaterProfileRepository profiles, UserSettingsRepository settings, UserRepository users,
                        MeasurementRepository measurements, AuditService audit, DemoAccounts demoAccounts) {
        this.places = places;
        this.members = members;
        this.access = access;
        this.profiles = profiles;
        this.settings = settings;
        this.users = users;
        this.measurements = measurements;
        this.audit = audit;
        this.demoAccounts = demoAccounts;
    }

    @Transactional(readOnly = true)
    public List<PlaceResponse> list(Long userId) {
        Long activeId = access.activeId(userId);
        return places.findAccessibleTo(userId).stream().map(p -> toResponse(userId, p, activeId)).toList();
    }

    @Transactional(readOnly = true)
    public PlaceResponse get(Long userId, Long placeId) {
        return toResponse(userId, access.visible(userId, placeId), access.activeId(userId));
    }

    @Transactional
    public PlaceResponse create(Long userId, PlaceRequest request) {
        if (places.countByOwnerId(userId) >= MAX_OWN_PLACES) {
            throw BusinessException.conflict("Puedes tener hasta " + MAX_OWN_PLACES + " lugares propios.");
        }
        Place place = places.save(new Place(userId, request.name().trim(), request.kind(), request.volumeLiters(),
            profileFor(userId, request)));
        audit.record(userId, "PLACE_CREATED", "Lugar " + place.getName());
        return activate(userId, place.getId());
    }

    @Transactional
    public PlaceResponse update(Long userId, Long placeId, PlaceRequest request) {
        Place place = access.editable(userId, placeId);
        place.update(request.name().trim(), request.kind(), request.volumeLiters(), profileFor(userId, request));
        audit.record(userId, "PLACE_UPDATED", "Lugar " + place.getName());
        return toResponse(userId, place, access.activeId(userId));
    }

    /** Deletes a place with its measurements. The owner keeps at least one place of their own. */
    @Transactional
    public void delete(Long userId, Long placeId) {
        Place place = access.visible(userId, placeId);
        if (!place.getOwnerId().equals(userId)) {
            throw BusinessException.forbidden("Solo quien creó el lugar puede borrarlo. Puedes salir de él.");
        }
        if (places.countByOwnerId(userId) <= 1) {
            throw BusinessException.conflict("Necesitas al menos un lugar propio. Crea otro antes de borrar este.");
        }
        places.delete(place);
        places.flush();
        audit.record(userId, "PLACE_DELETED", "Lugar " + place.getName());
    }

    /** Makes the place the active one: the app shows and records its measurements from now on. */
    @Transactional
    public PlaceResponse activate(Long userId, Long placeId) {
        Place place = access.visible(userId, placeId);
        UserSettings userSettings = settings.findById(userId).orElseGet(() ->
            settings.save(UserSettings.defaultsFor(userId, place.getProfileId())));
        userSettings.activatePlace(place.getId());
        userSettings.activateProfile(place.getProfileId());
        return toResponse(userId, place, place.getId());
    }

    // ---------------- Sharing with a code ----------------

    /** Creates (or renews) the code that lets other accounts join the place. Only the owner shares. */
    @Transactional
    public PlaceResponse share(Long userId, Long placeId, PlaceRole role) {
        demoAccounts.forbidFor(userId, "Las cuentas de demostración no pueden compartir lugares. Crea tu cuenta para hacerlo.");
        Place place = owned(userId, placeId);
        String code;
        do {
            code = newCode();
        } while (places.existsByShareCode(code));
        place.share(code, role);
        audit.record(userId, "PLACE_SHARED", "Lugar " + place.getName() + " (" + role.label() + ")");
        return toResponse(userId, place, access.activeId(userId));
    }

    /** Disables the code; the people who already joined keep their access until the owner removes them. */
    @Transactional
    public PlaceResponse stopSharing(Long userId, Long placeId) {
        Place place = owned(userId, placeId);
        place.stopSharing();
        audit.record(userId, "PLACE_SHARE_STOPPED", "Lugar " + place.getName());
        return toResponse(userId, place, access.activeId(userId));
    }

    /** Joins the place of the code with the role chosen by its owner, and makes it the active place. */
    @Transactional
    public PlaceResponse join(Long userId, String rawCode) {
        demoAccounts.forbidFor(userId, "Las cuentas de demostración no pueden unirse a lugares. Crea tu cuenta para hacerlo.");
        String code = rawCode.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        Place place = places.findByShareCode(code)
            .orElseThrow(() -> BusinessException.notFound("El código no existe o ya no está activo. Pídele uno nuevo a quien te lo compartió."));
        if (place.getOwnerId().equals(userId)) {
            throw BusinessException.conflict("Ese lugar ya es tuyo.");
        }
        PlaceMember member = members.findByPlaceIdAndUserId(place.getId(), userId)
            .orElseGet(() -> members.save(new PlaceMember(place.getId(), userId, place.getShareRole())));
        member.changeRole(place.getShareRole());
        audit.record(userId, "PLACE_JOINED", "Lugar " + place.getName() + " (" + place.getShareRole().label() + ")");
        return activate(userId, place.getId());
    }

    /** Leaves a place that someone else shared. */
    @Transactional
    public void leave(Long userId, Long placeId) {
        Place place = access.visible(userId, placeId);
        PlaceMember member = members.findByPlaceIdAndUserId(place.getId(), userId)
            .orElseThrow(() -> BusinessException.conflict("Ese lugar es tuyo: no puedes salir de él, puedes borrarlo."));
        members.delete(member);
        audit.record(userId, "PLACE_LEFT", "Lugar " + place.getName());
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> members(Long userId, Long placeId) {
        Place place = access.visible(userId, placeId);
        User owner = users.findById(place.getOwnerId()).orElseThrow();
        java.util.ArrayList<MemberResponse> out = new java.util.ArrayList<>();
        out.add(new MemberResponse(owner.getId(), owner.getFullName(), DemoAccounts.maskEmail(owner.getEmail()), PlaceRole.OWNER.name(),
            PlaceRole.OWNER.label(), place.getCreatedAt()));
        for (PlaceMember m : members.findByPlaceIdOrderByJoinedAtAsc(place.getId())) {
            users.findById(m.getUserId()).ifPresent(u -> out.add(new MemberResponse(u.getId(), u.getFullName(), DemoAccounts.maskEmail(u.getEmail()),
                m.getRole().name(), m.getRole().label(), m.getJoinedAt())));
        }
        return out;
    }

    /** The owner removes someone from the place. */
    @Transactional
    public void removeMember(Long userId, Long placeId, Long memberUserId) {
        Place place = owned(userId, placeId);
        PlaceMember member = members.findByPlaceIdAndUserId(place.getId(), memberUserId)
            .orElseThrow(() -> BusinessException.notFound("Esa persona no está en el lugar."));
        members.delete(member);
        audit.record(userId, "PLACE_MEMBER_REMOVED", "Lugar " + place.getName());
    }

    private Place owned(Long userId, Long placeId) {
        Place place = access.visible(userId, placeId);
        if (!place.getOwnerId().equals(userId)) {
            throw BusinessException.forbidden("Solo quien creó el lugar puede compartirlo o quitar personas.");
        }
        return place;
    }

    private String newCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]);
        }
        return code.toString();
    }

    /** The profile chosen, if the person can use it; otherwise the system profile that matches the kind of water. */
    private Long profileFor(Long userId, PlaceRequest request) {
        if (request.profileId() != null) {
            return profiles.findById(request.profileId()).filter(p -> p.isVisibleTo(userId))
                .orElseThrow(() -> BusinessException.badRequest("El perfil elegido no existe.")).getId();
        }
        ProfileType type = switch (request.kind()) {
            case POOL, SPA -> ProfileType.POOL;
            case AQUARIUM, POND -> ProfileType.AQUARIUM;
            case DRINKING -> ProfileType.DRINKING;
            case IRRIGATION, HYDROPONICS -> ProfileType.IRRIGATION;
            default -> ProfileType.GENERAL;
        };
        return profiles.findFirstBySystemProfileTrueAndType(type).orElseGet(access::defaultProfile).getId();
    }

    private PlaceResponse toResponse(Long userId, Place place, Long activeId) {
        PlaceRole role = access.roleIn(userId, place).orElseThrow();
        WaterProfile profile = profiles.findById(place.getProfileId()).orElseGet(access::defaultProfile);
        String ownerName = role == PlaceRole.OWNER ? null : users.findById(place.getOwnerId()).map(User::getFullName).orElse(null);
        boolean owner = role == PlaceRole.OWNER;
        return new PlaceResponse(place.getId(), place.getName(), place.getKind().name(), place.getKind().label(),
            place.getVolumeLiters(), profile.getId(), profile.getName(), profile.getIdealMin(), profile.getIdealMax(),
            role.name(), role.label(), role.canEdit(), place.getId().equals(activeId), ownerName,
            members.countByPlaceId(place.getId()), owner ? place.getShareCode() : null,
            owner && place.getShareRole() != null ? place.getShareRole().name() : null,
            measurements.countByPlaceId(place.getId()),
            measurements.findTopByPlaceIdOrderByMeasuredAtDesc(place.getId()).map(Measurement::getMeasuredAt).orElse(null));
    }
}
