package com.aquasense.identity.service;

import com.aquasense.identity.api.AuthDtos.ForgotPasswordRequest;
import com.aquasense.identity.api.AuthDtos.LoginRequest;
import com.aquasense.identity.api.AuthDtos.RegisterRequest;
import com.aquasense.identity.api.AuthDtos.ResetPasswordRequest;
import com.aquasense.identity.api.AuthDtos.TokenResponse;
import com.aquasense.identity.api.AuthDtos.UserResponse;
import com.aquasense.identity.domain.PasswordResetToken;
import com.aquasense.identity.domain.RefreshToken;
import com.aquasense.identity.domain.User;
import com.aquasense.identity.repository.PasswordResetTokenRepository;
import com.aquasense.identity.repository.RefreshTokenRepository;
import com.aquasense.identity.repository.UserRepository;
import com.aquasense.identity.security.JwtService;
import com.aquasense.identity.security.SecurityProperties;
import com.aquasense.identity.security.TokenHasher;
import com.aquasense.shared.DomainException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Credenciales inválidas.";

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SecurityProperties properties;
    private final PasswordResetNotifier notifier;
    private final String dummyHash;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens,
                       PasswordResetTokenRepository resetTokens, PasswordEncoder passwordEncoder,
                       JwtService jwtService, SecurityProperties properties, PasswordResetNotifier notifier) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.resetTokens = resetTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.properties = properties;
        this.notifier = notifier;
        this.dummyHash = passwordEncoder.encode(TokenHasher.newOpaqueToken());
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "PASSWORD_MISMATCH", "Las contraseñas no coinciden.");
        }
        String email = request.email().trim();
        if (users.existsByEmail(email)) {
            throw new DomainException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "El correo ya está registrado.");
        }
        String username = request.username() == null || request.username().isBlank() ? null : request.username().trim();
        if (username != null && users.existsByUsername(username)) {
            throw new DomainException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "El nombre de usuario ya está en uso.");
        }
        User user = new User(email, username, request.displayName().trim(),
                passwordEncoder.encode(request.password()));
        return UserResponse.from(users.save(user));
    }

    @Transactional(noRollbackFor = DomainException.class)
    public TokenResponse login(LoginRequest request) {
        Instant now = Instant.now();
        User user = users.findByIdentifier(request.identifier().trim()).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            throw invalidCredentials();
        }
        if (user.isLocked(now)) {
            throw new DomainException(HttpStatus.LOCKED, "ACCOUNT_LOCKED",
                    "La cuenta está bloqueada temporalmente por intentos fallidos. Intente más tarde.");
        }
        if (!user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            user.registerFailedLogin(now);
            users.save(user);
            throw invalidCredentials();
        }
        user.registerSuccessfulLogin();
        users.save(user);
        return issueTokens(user, request.rememberMe());
    }

    @Transactional
    public TokenResponse refresh(String rawRefreshToken) {
        Instant now = Instant.now();
        RefreshToken stored = refreshTokens.findByTokenHash(TokenHasher.sha256(rawRefreshToken))
                .orElseThrow(this::invalidSession);
        if (!stored.isUsable(now)) {
            throw invalidSession();
        }
        User user = users.findById(stored.getUserId()).filter(User::isEnabled).orElseThrow(this::invalidSession);
        stored.revoke(now);
        refreshTokens.save(stored);
        long remainingDays = Math.max(1, java.time.Duration.between(now, stored.getExpiresAt()).toDays());
        return issueTokens(user, remainingDays > properties.refreshTokenDays());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokens.findByTokenHash(TokenHasher.sha256(rawRefreshToken)).ifPresent(token -> {
            token.revoke(Instant.now());
            refreshTokens.save(token);
        });
    }

    @Transactional
    public void requestPasswordReset(ForgotPasswordRequest request) {
        users.findByIdentifier(request.email().trim()).filter(User::isEnabled).ifPresent(user -> {
            String code = TokenHasher.newResetCode();
            Instant expires = Instant.now().plusSeconds(properties.resetTokenMinutes() * 60);
            resetTokens.save(new PasswordResetToken(user.getId(), TokenHasher.sha256(code), expires));
            notifier.send(user.getEmail(), user.getDisplayName(), code, properties.resetTokenMinutes());
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "PASSWORD_MISMATCH", "Las contraseñas no coinciden.");
        }
        Instant now = Instant.now();
        PasswordResetToken token = resetTokens.findByTokenHash(TokenHasher.sha256(request.code().trim().toUpperCase()))
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> new DomainException(HttpStatus.BAD_REQUEST, "INVALID_RESET_CODE",
                        "El código de recuperación es inválido o ha expirado."));
        User user = users.findById(token.getUserId()).orElseThrow();
        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        users.save(user);
        token.markUsed(now);
        resetTokens.save(token);
        refreshTokens.revokeAllForUser(user.getId(), now);
    }

    public UserResponse currentUser(UUID userId) {
        return users.findById(userId).map(UserResponse::from).orElseThrow(this::invalidSession);
    }

    private TokenResponse issueTokens(User user, boolean remember) {
        String raw = TokenHasher.newOpaqueToken();
        long days = remember ? properties.refreshTokenRememberDays() : properties.refreshTokenDays();
        Instant expires = Instant.now().plusSeconds(days * 86400);
        refreshTokens.save(new RefreshToken(user.getId(), TokenHasher.sha256(raw), expires));
        return new TokenResponse(jwtService.createAccessToken(user), raw, "Bearer", jwtService.accessTokenSeconds(),
                UserResponse.from(user));
    }

    private DomainException invalidCredentials() {
        return new DomainException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", INVALID_CREDENTIALS);
    }

    private DomainException invalidSession() {
        return new DomainException(HttpStatus.UNAUTHORIZED, "INVALID_SESSION",
                "La sesión no es válida o ha expirado. Inicie sesión nuevamente.");
    }
}
