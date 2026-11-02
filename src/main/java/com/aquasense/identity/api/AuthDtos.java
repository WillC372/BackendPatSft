package com.aquasense.identity.api;

import com.aquasense.identity.domain.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public final class AuthDtos {

    private static final String PASSWORD_RULE = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";
    private static final String PASSWORD_MESSAGE = "debe tener entre 8 y 72 caracteres, con al menos una letra y un número";

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 80) String displayName,
            @NotBlank @Email @Size(max = 254) String email,
            @Pattern(regexp = "^[A-Za-z0-9_.-]{3,40}$", message = "solo letras, números, punto, guion y guion bajo (3 a 40)")
            String username,
            @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MESSAGE) String password,
            @NotBlank String confirmPassword) {
    }

    public record LoginRequest(
            @NotBlank String identifier,
            @NotBlank String password,
            boolean rememberMe) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record ForgotPasswordRequest(@NotBlank @Email String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank String code,
            @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MESSAGE) String newPassword,
            @NotBlank String confirmPassword) {
    }

    public record UserResponse(UUID id, String email, String username, String displayName, List<String> roles) {

        public static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getEmail(), user.getUsername(), user.getDisplayName(),
                    user.getRoles().stream().map(Enum::name).sorted().toList());
        }
    }

    public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresInSeconds,
                                UserResponse user) {
    }

    public record MessageResponse(String message) {
    }
}
