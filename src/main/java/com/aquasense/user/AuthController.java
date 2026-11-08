package com.aquasense.user;

import com.aquasense.common.BusinessException;
import com.aquasense.security.CurrentUser;
import com.aquasense.security.RateLimiter;
import com.aquasense.user.dto.AuthResponse;
import com.aquasense.user.dto.ForgotPasswordRequest;
import com.aquasense.user.dto.LoginRequest;
import com.aquasense.user.dto.MessageResponse;
import com.aquasense.user.dto.RegisterRequest;
import com.aquasense.user.dto.ResetPasswordRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RateLimiter rateLimiter;
    private final MailService mailService;

    public AuthController(AuthService authService, RateLimiter rateLimiter, MailService mailService) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
        this.mailService = mailService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        rateLimiter.check("register:" + http.getRemoteAddr());
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        rateLimiter.check("login:" + http.getRemoteAddr());
        return authService.login(request);
    }

    @PostMapping("/logout")
    public MessageResponse logout() {
        authService.logout(CurrentUser.id());
        return new MessageResponse("Sesión cerrada.");
    }

    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest http) {
        rateLimiter.check("forgot:" + http.getRemoteAddr());
        if (!mailService.isPasswordRecoveryAvailable()) {
            // Se responde igual para cualquier correo, así no se revela qué cuentas existen.
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "La recuperación de contraseña por correo no está"
                + " disponible porque el servidor no tiene un servicio de correo configurado. Contacta al administrador.");
        }
        authService.requestPasswordReset(request.email());
        return new MessageResponse("Si el correo está registrado, recibirás un enlace para restablecer la contraseña.");
    }

    @PostMapping("/verify-admin")
    public MessageResponse verifyAdmin(@Valid @RequestBody com.aquasense.user.dto.TokenRequest request, HttpServletRequest http) {
        rateLimiter.check("verify-admin:" + http.getRemoteAddr());
        authService.verifyAdmin(request.token());
        return new MessageResponse("Rol de administrador confirmado. Inicia sesión de nuevo para usarlo.");
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return new MessageResponse("Contraseña actualizada. Ya puedes iniciar sesión.");
    }
}
