package com.aquasense.profile.dto;

import com.aquasense.profile.WaterProfile;

public record ProfileResponse(Long id, String name, String type, String typeLabel, double idealMin, double idealMax,
                              double cautionMin, double cautionMax, String description, String source,
                              boolean systemProfile, boolean active) {

    public static ProfileResponse from(WaterProfile profile, Long activeId) {
        return new ProfileResponse(profile.getId(), profile.getName(), profile.getType().name(),
            profile.getType().label(), profile.getIdealMin(), profile.getIdealMax(), profile.getCautionMin(),
            profile.getCautionMax(), profile.getDescription(), profile.getSource(), profile.isSystemProfile(),
            profile.getId().equals(activeId));
    }
}
