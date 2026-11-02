package com.aquasense.waterprofile.service;

import com.aquasense.billing.service.LimitGuard;
import com.aquasense.shared.DomainException;
import com.aquasense.waterprofile.api.ProfileDtos.ProfileRequest;
import com.aquasense.waterprofile.domain.WaterProfile;
import com.aquasense.waterprofile.domain.WaterType;
import com.aquasense.waterprofile.repository.WaterProfileRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WaterProfileService {

    private final WaterProfileRepository profiles;
    private final LimitGuard limits;

    public WaterProfileService(WaterProfileRepository profiles, LimitGuard limits) {
        this.profiles = profiles;
        this.limits = limits;
    }

    @Transactional(readOnly = true)
    public List<WaterProfile> list(UUID userId) {
        return profiles.findVisibleTo(userId);
    }

    @Transactional(readOnly = true)
    public WaterProfile get(UUID id, UUID userId) {
        return profiles.findVisibleById(id, userId).orElseThrow(this::notFound);
    }

    @Transactional
    public WaterProfile create(UUID userId, ProfileRequest r) {
        limits.requireProfileSlot(userId);
        return profiles.save(new WaterProfile(userId, WaterType.CUSTOM, r.name().trim(), r.description().trim(),
                r.optimalMin(), r.optimalMax(), r.cautionMin(), r.cautionMax(), null));
    }

    @Transactional
    public WaterProfile duplicate(UUID id, UUID userId, String name) {
        limits.requireProfileSlot(userId);
        return profiles.save(get(id, userId).copyFor(userId, name.trim()));
    }

    @Transactional
    public WaterProfile update(UUID id, UUID userId, ProfileRequest r) {
        WaterProfile profile = profiles.findByIdAndOwnerId(id, userId).orElseThrow(this::notFoundOrReadOnly);
        profile.update(r.name().trim(), r.description().trim(), r.optimalMin(), r.optimalMax(), r.cautionMin(),
                r.cautionMax());
        return profiles.save(profile);
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        profiles.delete(profiles.findByIdAndOwnerId(id, userId).orElseThrow(this::notFoundOrReadOnly));
    }

    private DomainException notFound() {
        return new DomainException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "El perfil no existe.");
    }

    private DomainException notFoundOrReadOnly() {
        return new DomainException(HttpStatus.NOT_FOUND, "PROFILE_NOT_EDITABLE",
                "El perfil no existe o es un perfil del sistema que no se puede modificar. Duplíquelo para personalizarlo.");
    }
}
