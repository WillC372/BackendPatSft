package com.aquasense.identity.api;

import com.aquasense.identity.api.AuthDtos.ForgotPasswordRequest;
import com.aquasense.identity.api.AuthDtos.LoginRequest;
import com.aquasense.identity.api.AuthDtos.MessageResponse;
import com.aquasense.identity.api.AuthDtos.RefreshRequest;
import com.aquasense.identity.api.AuthDtos.RegisterRequest;
import com.aquasense.identity.api.AuthDtos.ResetPasswordRequest;
import com.aquasense.identity.api.AuthDtos.TokenResponse;
import com.aquasense.identity.api.AuthDtos.UserResponse;
import com.aquasense.identity.service.AuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticación")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.requestPasswordReset(request);
        return new MessageResponse("Si el correo está registrado, recibirá un código de recuperación.");
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return new MessageResponse("Contraseña actualizada. Inicie sesión con la nueva contraseña.");
    }
}
