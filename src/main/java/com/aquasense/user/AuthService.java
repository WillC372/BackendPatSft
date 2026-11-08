package com.aquasense.user;

import com.aquasense.audit.AuditService;
import com.aquasense.common.BusinessException;
import com.aquasense.config.AppProperties;
import com.aquasense.profile.ProfileService;
import com.aquasense.security.JwtService;
import com.aquasense.user.dto.AuthResponse;
import com.aquasense.user.dto.LoginRequest;
import com.aquasense.user.dto.RegisterRequest;
import com.aquasense.user.dto.UserResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Duration RESET_TOKEN_LIFETIME = Duration.ofMinutes(30);
    private static final Duration ADMIN_TOKEN_LIFETIME = Duration.ofHours(24);
    static final String ADMIN_NOTICE = "Para activar los permisos de administrador, abre el enlace que enviamos a tu correo."
        + " Mientras tanto la cuenta funciona como usuario normal.";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final UserSettingsRepository settingsRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ProfileService profileService;
    private final MailService mailService;
    private final AuditService auditService;
    private final String adminEmail;

    public AuthService(UserRepository userRepository, UserSettingsRepository settingsRepository,
                       PasswordResetTokenRepository resetTokenRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, ProfileService profileService, MailService mailService,
                       AuditService auditService, AppProperties properties) {
        this.userRepository = userRepository;
        this.settingsRepository = settingsRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.profileService = profileService;
        this.mailService = mailService;
        this.auditService = auditService;
        this.adminEmail = properties.security().adminEmail();
        if (adminEmail != null && !adminEmail.isBlank() && !mailService.isPasswordRecoveryAvailable()) {
            org.slf4j.LoggerFactory.getLogger(AuthService.class).warn("ADMIN_EMAIL is set but no email channel is configured:"
                + " the administrator role cannot be confirmed until email delivery is configured.");
        }
    }

    /** Si el correo es el de ADMIN_EMAIL (el administrador principal configurado en el servidor). */
    public boolean isAdminEmail(String email) {
        return adminEmail != null && !adminEmail.isBlank() && email.equalsIgnoreCase(adminEmail.trim());
    }

    /**
     * El rol de administrador nunca se asigna solo por escribir el correo de ADMIN_EMAIL: se envía un enlace a ese correo
     * y el rol se activa cuando su dueño lo abre. Así nadie puede registrarse antes con ese correo y quedarse con el rol.
     */
    private String requestAdminVerification(User user) {
        if (user.getRole() == Role.ADMIN || !isAdminEmail(user.getEmail())) {
            return null;
        }
        Instant now = Instant.now();
        boolean pending = resetTokenRepository.findByUserIdAndPurposeAndUsedAtIsNull(user.getId(),
            PasswordResetToken.Purpose.ADMIN_VERIFY).stream().anyMatch(t -> t.isUsable(now));
        if (!pending) {
            String rawToken = newToken();
            resetTokenRepository.save(new PasswordResetToken(user.getId(), sha256(rawToken), now.plus(ADMIN_TOKEN_LIFETIME),
                PasswordResetToken.Purpose.ADMIN_VERIFY));
            mailService.sendAdminVerification(user.getEmail(), rawToken);
            auditService.record(user.getId(), "ADMIN_VERIFICATION_SENT", "Enlace de confirmación de administrador enviado");
        }
        return ADMIN_NOTICE;
    }

    @Transactional
    public void verifyAdmin(String rawToken) {
        Instant now = Instant.now();
        PasswordResetToken token = resetTokenRepository.findByTokenHashAndPurpose(sha256(rawToken),
                PasswordResetToken.Purpose.ADMIN_VERIFY)
            .filter(found -> found.isUsable(now))
            .orElseThrow(() -> BusinessException.badRequest("El enlace no es válido o ya venció. Inicia sesión para recibir uno nuevo."));
        User user = userRepository.findById(token.getUserId())
            .orElseThrow(() -> BusinessException.notFound("La cuenta ya no existe."));
        token.markUsed(now);
        if (!isAdminEmail(user.getEmail())) {
            throw BusinessException.badRequest("Esta cuenta ya no corresponde al correo de administrador configurado.");
        }
        user.promoteToAdmin();
        auditService.record(user.getId(), "ADMIN_ROLE_CONFIRMED", "Rol de administrador confirmado por correo");
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw BusinessException.conflict("Ya existe una cuenta con ese correo.");
        }
        User user = userRepository.save(new User(email, request.fullName().trim(),
            passwordEncoder.encode(request.password()), Role.USER));
        settingsRepository.save(UserSettings.defaultsFor(user.getId(), profileService.defaultProfile().getId()));
        auditService.record(user.getId(), "USER_REGISTERED", "Cuenta creada");
        return tokenFor(user, false).withNotice(requestAdminVerification(user));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(normalize(request.email()))
            .filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()))
            .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos."));
        // Se comprueba después de la contraseña: así el aviso no revela a un tercero que la cuenta existe.
        if (!user.isEnabled()) {
            auditService.recordIndependently(user.getId(), "LOGIN_BLOCKED", "Intento de inicio de sesión con la cuenta desactivada");
            throw new BusinessException(HttpStatus.FORBIDDEN, "Esta cuenta está desactivada. Escribe al administrador para reactivarla.");
        }
        user.recordLogin(Instant.now());
        auditService.record(user.getId(), "USER_LOGIN", request.rememberMe() ? "Sesión recordada" : "Sesión normal");
        return tokenFor(user, request.rememberMe()).withNotice(requestAdminVerification(user));
    }

    @Transactional
    public void logout(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.revokeTokens();
            auditService.record(userId, "USER_LOGOUT", "Tokens revocados");
        });
    }

    @Transactional
    public boolean requestPasswordReset(String rawEmail) {
        // Una cuenta desactivada no recibe enlaces; la respuesta al usuario es la misma para no revelar su estado.
        return userRepository.findByEmailIgnoreCase(normalize(rawEmail)).filter(User::isEnabled).map(user -> {
            String rawToken = newToken();
            resetTokenRepository.save(new PasswordResetToken(user.getId(), sha256(rawToken),
                Instant.now().plus(RESET_TOKEN_LIFETIME)));
            auditService.record(user.getId(), "PASSWORD_RESET_REQUESTED", "Solicitud de restablecimiento");
            return mailService.sendPasswordReset(user.getEmail(), rawToken);
        }).orElse(false);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        Instant now = Instant.now();
        PasswordResetToken token = resetTokenRepository.findByTokenHashAndPurpose(sha256(rawToken),
                PasswordResetToken.Purpose.PASSWORD_RESET)
            .filter(found -> found.isUsable(now))
            .orElseThrow(() -> BusinessException.badRequest("El enlace no es válido o ya venció. Solicita uno nuevo."));
        User user = userRepository.findById(token.getUserId())
            .orElseThrow(() -> BusinessException.notFound("La cuenta ya no existe."));
        user.changePassword(passwordEncoder.encode(newPassword));
        // Cualquier otro enlace de recuperación pendiente de esta cuenta deja de servir.
        resetTokenRepository.findByUserIdAndPurposeAndUsedAtIsNull(user.getId(), PasswordResetToken.Purpose.PASSWORD_RESET)
            .forEach(t -> t.markUsed(now));
        token.markUsed(now);
        auditService.record(user.getId(), "PASSWORD_RESET", "Contraseña restablecida");
    }

    private AuthResponse tokenFor(User user, boolean rememberMe) {
        JwtService.IssuedToken issued = jwtService.issue(user.getId(), user.getEmail(), user.getRole().name(),
            user.getTokenVersion(), rememberMe);
        return new AuthResponse(issued.token(), issued.expiresAt(), UserResponse.from(user), null);
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
