package com.aquaassistant.place;

import com.aquaassistant.common.BusinessException;
import com.aquaassistant.profile.ProfileType;
import com.aquaassistant.profile.WaterProfile;
import com.aquaassistant.profile.WaterProfileRepository;
import com.aquaassistant.user.UserSettings;
import com.aquaassistant.user.UserSettingsRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who can do what in which place, and which place is active for a person. Everything that reads or writes
 * measurements goes through here, so the dashboard, history, analyses and alerts follow the place chosen.
 * Depends only on repositories, so the profile and measurement services can use it without cycles.
 */
@Component
public class PlaceAccess {

    public static final String DEFAULT_NAME = "Mi agua";

    private final PlaceRepository places;
    private final PlaceMemberRepository members;
    private final UserSettingsRepository settings;
    private final WaterProfileRepository profiles;
    private final PlaceProvisioner provisioner;

    public PlaceAccess(PlaceRepository places, PlaceMemberRepository members, UserSettingsRepository settings,
                       WaterProfileRepository profiles, @org.springframework.context.annotation.Lazy PlaceProvisioner provisioner) {
        this.places = places;
        this.members = members;
        this.settings = settings;
        this.profiles = profiles;
        this.provisioner = provisioner;
    }

    /** The role of the person in the place, or empty if they have no access. */
    public Optional<PlaceRole> roleIn(Long userId, Place place) {
        if (place.getOwnerId().equals(userId)) {
            return Optional.of(PlaceRole.OWNER);
        }
        return members.findByPlaceIdAndUserId(place.getId(), userId).map(PlaceMember::getRole);
    }

    /** The place if the person can see it; otherwise "not found" (it does not reveal that it exists). */
    public Place visible(Long userId, Long placeId) {
        return places.findById(placeId).filter(p -> roleIn(userId, p).isPresent())
            .orElseThrow(() -> BusinessException.notFound("El lugar no existe."));
    }

    /** The place if the person can measure and record in it. */
    public Place editable(Long userId, Long placeId) {
        Place place = visible(userId, placeId);
        if (!roleIn(userId, place).orElseThrow().canEdit()) {
            throw BusinessException.forbidden("Este lugar está compartido contigo solo para ver: no puedes cambiar nada en él.");
        }
        return place;
    }

    /**
     * The active place of the person. If none is set or they lost access to it, the first accessible one is used.
     * Every account gets "Mi agua" when it is created ({@link #provision}); only an account left without any place
     * gets a new one here, in its own transaction.
     */
    @Transactional(readOnly = true)
    public Place active(Long userId) {
        Long activeId = settings.findById(userId).map(UserSettings::getActivePlaceId).orElse(null);
        if (activeId != null) {
            Optional<Place> active = places.findById(activeId).filter(p -> roleIn(userId, p).isPresent());
            if (active.isPresent()) {
                return active.get();
            }
        }
        List<Place> accessible = places.findAccessibleTo(userId);
        return accessible.isEmpty() ? provisioner.provisionAlone(userId) : accessible.get(0);
    }

    /** Creates "Mi agua" for a new account and makes it its active place. */
    @Transactional
    public Place provision(Long userId) {
        List<Place> owned = places.findByOwnerIdOrderByCreatedAtAsc(userId);
        if (!owned.isEmpty()) {
            return owned.get(0);
        }
        UserSettings userSettings = settings.findById(userId).orElse(null);
        Place created = places.save(new Place(userId, DEFAULT_NAME, PlaceKind.GENERAL, null, initialProfileId(userSettings, userId)));
        if (userSettings != null) {
            userSettings.activatePlace(created.getId());
        }
        return created;
    }

    /** Everyone with access to the place: its owner and the people who joined. */
    @Transactional(readOnly = true)
    public List<Long> people(Long placeId) {
        return places.findById(placeId).map(p -> {
            List<Long> out = new java.util.ArrayList<>();
            out.add(p.getOwnerId());
            members.findByPlaceIdOrderByJoinedAtAsc(placeId).forEach(m -> out.add(m.getUserId()));
            return out;
        }).orElse(List.of());
    }

    public Long activeId(Long userId) {
        return active(userId).getId();
    }

    public boolean canEditActive(Long userId) {
        Place place = active(userId);
        return roleIn(userId, place).map(PlaceRole::canEdit).orElse(false);
    }

    /** The water profile used by the active place (it is shared with the people of the place). */
    public WaterProfile activeProfile(Long userId) {
        return profiles.findById(active(userId).getProfileId()).orElseGet(this::defaultProfile);
    }

    public WaterProfile defaultProfile() {
        return profiles.findFirstBySystemProfileTrueAndType(ProfileType.GENERAL)
            .orElseThrow(() -> new IllegalStateException("System profile GENERAL is missing"));
    }

    private Long initialProfileId(UserSettings userSettings, Long userId) {
        Long chosen = userSettings == null ? null : userSettings.getActiveProfileId();
        if (chosen != null && profiles.findById(chosen).filter(p -> p.isVisibleTo(userId)).isPresent()) {
            return chosen;
        }
        return defaultProfile().getId();
    }
}
