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

    private final DemoAccounts demoAccounts;
    private final com.aquasense.security.ClientAddress clientAddress;
    static final int ACCOUNT_FAILURES = 5;
    static final java.time.Duration ACCOUNT_WINDOW = java.time.Duration.ofMinutes(15);

    public AuthController(AuthService authService, RateLimiter rateLimiter, MailService mailService, DemoAccounts demoAccounts,
                          com.aquasense.security.ClientAddress clientAddress) {
        this.demoAccounts = demoAccounts;
        this.clientAddress = clientAddress;
        this.authService = authService;
        this.rateLimiter = rateLimiter;
        this.mailService = mailService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        rateLimiter.check("register:" + clientAddress.of(http));
        return authService.register(request, http.getHeader("User-Agent"));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        rateLimiter.check("login:" + clientAddress.of(http));
        // Per account, only failures count: whoever tries passwords from many addresses is stopped after 5 in 15 minutes,
        // and the shared demo accounts keep working for everyone who uses them correctly.
        String account = "login-account:" + request.email().trim().toLowerCase(java.util.Locale.ROOT);
        rateLimiter.ensureAllowed(account, ACCOUNT_FAILURES, ACCOUNT_WINDOW);
        try {
            return authService.login(request, http.getHeader("User-Agent"));
        } catch (BusinessException ex) {
            if (ex.getStatus() == HttpStatus.UNAUTHORIZED) {
                rateLimiter.recordFailure(account);
            }
            throw ex;
        }
    }

    /** Demonstration accounts for the buttons of the login screen (one per role). Empty if they are disabled. */
    @org.springframework.web.bind.annotation.GetMapping("/demo-accounts")
    public java.util.List<DemoAccounts.DemoAccount> demoAccounts() {
        return demoAccounts.list();
    }

    @PostMapping("/logout")
    public MessageResponse logout() {
        authService.logout(CurrentUser.id(), CurrentUser.sessionId());
        return new MessageResponse("Sesión cerrada.");
    }

    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest http) {
        rateLimiter.check("forgot:" + clientAddress.of(http));
        if (!mailService.isPasswordRecoveryAvailable()) {
            // The response is the same for any email, so it does not reveal which accounts exist.
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "La recuperación de contraseña por correo no está"
                + " disponible porque el servidor no tiene un servicio de correo configurado. Contacta al administrador.");
        }
        authService.requestPasswordReset(request.email());
        return new MessageResponse("Si el correo está registrado, recibirás un enlace para restablecer la contraseña.");
    }

    @PostMapping("/verify-admin")
    public MessageResponse verifyAdmin(@Valid @RequestBody com.aquasense.user.dto.TokenRequest request, HttpServletRequest http) {
        rateLimiter.check("verify-admin:" + clientAddress.of(http));
        authService.verifyAdmin(request.token());
        return new MessageResponse("Rol de administrador confirmado. Inicia sesión de nuevo para usarlo.");
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return new MessageResponse("Contraseña actualizada. Ya puedes iniciar sesión.");
    }
}
