package com.aquasense.user.dto;

import com.aquasense.user.User;
import java.time.Instant;

public record UserResponse(Long id, String email, String fullName, String role, String plan, Instant createdAt,
                           Instant premiumUntil, boolean demo, Long avatarVersion) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole().name(),
            user.getPlan().name(), user.getCreatedAt(), user.getPremiumUntil(), user.isDemo(),
            user.getAvatarUpdatedAt() == null ? null : user.getAvatarUpdatedAt().toEpochMilli());
    }
}
