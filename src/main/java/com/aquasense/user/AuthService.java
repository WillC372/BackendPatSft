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
import java.util.List;
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
    /** A new admin link is not sent again before this time, so repeated logins do not flood the inbox. */
    private static final Duration ADMIN_RESEND_AFTER = Duration.ofMinutes(10);
    static final String ADMIN_MAIL_FAILED = "No pudimos enviar el correo para activar tus permisos de administrador. "
        + "Vuelve a iniciar sesión en unos minutos para reintentarlo; si sigue fallando, revisa la configuración de Mailjet del servidor.";
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
    private final AccountDeletionService accountDeletion;
    private final SessionHistoryService sessions;

    public AuthService(UserRepository userRepository, UserSettingsRepository settingsRepository,
                       PasswordResetTokenRepository resetTokenRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, ProfileService profileService, MailService mailService,
                       AuditService auditService, AppProperties properties, AccountDeletionService accountDeletion,
                       SessionHistoryService sessions) {
        this.accountDeletion = accountDeletion;
        this.sessions = sessions;
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

    /** Whether the email is the ADMIN_EMAIL one (the main administrator configured on the server). */
    public boolean isAdminEmail(String email) {
        return adminEmail != null && !adminEmail.isBlank() && email.equalsIgnoreCase(adminEmail.trim());
    }

    /**
     * The administrator role is never granted just by typing the ADMIN_EMAIL address: a link is sent to that email
     * and the role is activated when its owner opens it. This way nobody can register first with that email and keep the role.
     */
    private String requestAdminVerification(User user) {
        if (user.getRole() == Role.ADMIN || !isAdminEmail(user.getEmail())) {
            return null;
        }
        Instant now = Instant.now();
        List<PasswordResetToken> open = resetTokenRepository.findByUserIdAndPurposeAndUsedAtIsNull(user.getId(),
            PasswordResetToken.Purpose.ADMIN_VERIFY).stream().filter(t -> t.isUsable(now)).toList();
        Instant recentLimit = now.plus(ADMIN_TOKEN_LIFETIME).minus(ADMIN_RESEND_AFTER);
        if (open.stream().anyMatch(t -> t.getExpiresAt().isAfter(recentLimit))) {
            return ADMIN_NOTICE;
        }
        // Older links stop working: the new email replaces them.
        open.forEach(t -> t.markUsed(now));
        String rawToken = newToken();
        PasswordResetToken token = resetTokenRepository.save(new PasswordResetToken(user.getId(), sha256(rawToken),
            now.plus(ADMIN_TOKEN_LIFETIME), PasswordResetToken.Purpose.ADMIN_VERIFY));
        if (!mailService.sendAdminVerification(user.getEmail(), rawToken)) {
            // The link never reached the mailbox: it is discarded so the next login tries again, and the user is told.
            token.markUsed(now);
            auditService.record(user.getId(), "ADMIN_VERIFICATION_FAILED", "No se pudo enviar el enlace de confirmación de administrador");
            return ADMIN_MAIL_FAILED;
        }
        auditService.record(user.getId(), "ADMIN_VERIFICATION_SENT", "Enlace de confirmación de administrador enviado");
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
        sessions.endAll(user.getId(), UserSession.EndReason.ROLE_CHANGED, null);
        auditService.record(user.getId(), "ADMIN_ROLE_CONFIRMED", "Rol de administrador confirmado por correo");
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        return register(request, null);
    }

    /** {@code userAgent}: browser of the request, summarized in the session history ("Chrome en Windows"). */
    @Transactional
    public AuthResponse register(RegisterRequest request, String userAgent) {
        String email = normalize(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw BusinessException.conflict("Ya existe una cuenta con ese correo.");
        }
        User user = userRepository.save(new User(email, request.fullName().trim(),
            passwordEncoder.encode(request.password()), Role.USER));
        settingsRepository.save(UserSettings.defaultsFor(user.getId(), profileService.defaultProfile().getId()));
        return openSession(user, false, userAgent, "USER_REGISTERED", "Cuenta creada")
            .withNotice(requestAdminVerification(user));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        return login(request, null);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String userAgent) {
        User user = userRepository.findByEmailIgnoreCase(normalize(request.email()))
            .filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()))
            .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos."));
        // Checked after the password: this way the notice does not reveal to a third party that the account exists.
        if (!user.isEnabled()) {
            auditService.recordIndependently(user.getId(), "LOGIN_BLOCKED", "Intento de inicio de sesión con la cuenta desactivada");
            throw new BusinessException(HttpStatus.FORBIDDEN, "Esta cuenta está desactivada. Escribe al administrador para reactivarla.");
        }
        user.recordLogin(Instant.now());
        return openSession(user, request.rememberMe(), userAgent, "USER_LOGIN",
            request.rememberMe() ? "Sesión recordada" : "Sesión normal").withNotice(requestAdminVerification(user));
    }

    /** The user deletes their own account; the password is asked again so a stolen session cannot do it. */
    @Transactional
    public void deleteOwnAccount(Long userId, String password) {
        User user = userRepository.findById(userId).orElseThrow(() -> BusinessException.notFound("La cuenta ya no existe."));
        if (user.isDemo()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Las cuentas de demostración no se pueden eliminar.");
        }
        if (password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw BusinessException.badRequest("La contraseña no es correcta.");
        }
        String blocked = accountDeletion.blockedReason(user);
        if (blocked != null) {
            throw BusinessException.conflict(blocked);
        }
        accountDeletion.delete(user);
        org.slf4j.LoggerFactory.getLogger(AuthService.class).info("Account {} deleted by its owner.", userId);
    }

    /** Closes the current session: its history is kept and its token stops working. Other sessions stay open. */
    @Transactional
    public void logout(Long userId, Long sessionId) {
        auditService.record(userId, "USER_LOGOUT", "Sesión cerrada");
        sessions.end(sessionId, userId, UserSession.EndReason.LOGOUT);
    }

    /** Closes every other open session of the user (other browsers or devices), keeping the current one. */
    @Transactional
    public int closeOtherSessions(Long userId, Long sessionId) {
        int closed = sessions.endAll(userId, UserSession.EndReason.CLOSED_FROM_OTHER, sessionId);
        if (closed > 0) {
            auditService.record(userId, "SESSIONS_CLOSED", closed + (closed == 1 ? " sesión cerrada" : " sesiones cerradas"));
        }
        return closed;
    }

    @Transactional
    public boolean requestPasswordReset(String rawEmail) {
        // A disabled account receives no links; the response to the user is the same so its status is not revealed.
        return userRepository.findByEmailIgnoreCase(normalize(rawEmail)).filter(u -> u.isEnabled() && !u.isDemo()).map(user -> {
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
        // Any other pending recovery link of this account stops working.
        resetTokenRepository.findByUserIdAndPurposeAndUsedAtIsNull(user.getId(), PasswordResetToken.Purpose.PASSWORD_RESET)
            .forEach(t -> t.markUsed(now));
        token.markUsed(now);
        sessions.endAll(user.getId(), UserSession.EndReason.PASSWORD_CHANGED, null);
        auditService.record(user.getId(), "PASSWORD_RESET", "Contraseña restablecida");
    }

    /** Starts a new session (a new entry of the history) and issues its token. */
    private AuthResponse openSession(User user, boolean rememberMe, String userAgent, String action, String detail) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(jwtService.lifetime(rememberMe));
        UserSession session = sessions.start(user.getId(), now, expiresAt, rememberMe, userAgent);
        auditService.recordInSession(user.getId(), session.getId(), action, detail);
        JwtService.IssuedToken issued = jwtService.issue(user.getId(), user.getEmail(), user.getRole().name(),
            user.getTokenVersion(), session.getId(), now, expiresAt);
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
