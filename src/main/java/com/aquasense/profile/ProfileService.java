package com.aquasense.profile;

import com.aquasense.audit.AuditService;
import com.aquasense.common.BusinessException;
import com.aquasense.profile.dto.ProfileRequest;
import com.aquasense.user.UserSettings;
import com.aquasense.user.UserSettingsRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    private final WaterProfileRepository repository;
    private final UserSettingsRepository settingsRepository;
    private final AuditService auditService;

    public ProfileService(WaterProfileRepository repository, UserSettingsRepository settingsRepository,
                          AuditService auditService) {
        this.repository = repository;
        this.settingsRepository = settingsRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<WaterProfile> listFor(Long userId) {
        return repository.findVisibleTo(userId);
    }

    @Transactional(readOnly = true)
    public WaterProfile getVisible(Long userId, Long profileId) {
        return repository.findById(profileId)
            .filter(profile -> profile.isVisibleTo(userId))
            .orElseThrow(() -> BusinessException.notFound("El perfil no existe."));
    }

    @Transactional(readOnly = true)
    public WaterProfile activeProfile(Long userId) {
        Long activeId = settingsRepository.findById(userId).map(UserSettings::getActiveProfileId).orElse(null);
        if (activeId != null) {
            return repository.findById(activeId).filter(p -> p.isVisibleTo(userId)).orElseGet(this::defaultProfile);
        }
        return defaultProfile();
    }

    @Transactional(readOnly = true)
    public WaterProfile defaultProfile() {
        return repository.findFirstBySystemProfileTrueAndType(ProfileType.GENERAL)
            .orElseThrow(() -> new IllegalStateException("System profile GENERAL is missing"));
    }

    @Transactional
    public WaterProfile create(Long userId, ProfileRequest request) {
        WaterProfile profile = new WaterProfile(userId, request.name(), ProfileType.CUSTOM, request.toRange(),
            request.description(), request.source());
        WaterProfile saved = repository.save(profile);
        auditService.record(userId, "PROFILE_CREATED", "Perfil " + saved.getName());
        return saved;
    }

    @Transactional
    public WaterProfile duplicate(Long userId, Long sourceId) {
        WaterProfile source = getVisible(userId, sourceId);
        WaterProfile copy = repository.save(source.copyFor(userId, source.getName() + " (copia)"));
        auditService.record(userId, "PROFILE_DUPLICATED", source.getName() + " -> " + copy.getName());
        return copy;
    }

    @Transactional
    public WaterProfile update(Long userId, Long profileId, ProfileRequest request) {
        WaterProfile profile = ownedProfile(userId, profileId);
        profile.update(request.name(), request.toRange(), request.description(), request.source());
        auditService.record(userId, "PROFILE_UPDATED", "Perfil " + profile.getName());
        return profile;
    }

    @Transactional
    public void delete(Long userId, Long profileId) {
        WaterProfile profile = ownedProfile(userId, profileId);
        settingsRepository.findById(userId)
            .filter(settings -> profileId.equals(settings.getActiveProfileId()))
            .ifPresent(settings -> settings.activateProfile(defaultProfile().getId()));
        try {
            repository.delete(profile);
            repository.flush();
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw BusinessException.conflict("El perfil tiene mediciones asociadas y no se puede eliminar.");
        }
        auditService.record(userId, "PROFILE_DELETED", "Perfil " + profile.getName());
    }

    @Transactional
    public WaterProfile activate(Long userId, Long profileId) {
        WaterProfile profile = getVisible(userId, profileId);
        UserSettings settings = settingsRepository.findById(userId)
            .orElseGet(() -> settingsRepository.save(UserSettings.defaultsFor(userId, profile.getId())));
        settings.activateProfile(profile.getId());
        auditService.record(userId, "PROFILE_ACTIVATED", "Perfil " + profile.getName());
        return profile;
    }

    private WaterProfile ownedProfile(Long userId, Long profileId) {
        WaterProfile profile = getVisible(userId, profileId);
        if (profile.isSystemProfile()) {
            throw BusinessException.badRequest("Los perfiles del sistema no se pueden modificar. Duplícalo para personalizarlo.");
        }
        return profile;
    }
}
