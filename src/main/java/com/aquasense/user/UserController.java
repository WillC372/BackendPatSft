package com.aquasense.user;

import com.aquasense.audit.AuditService;
import com.aquasense.common.BusinessException;
import com.aquasense.profile.ProfileService;
import com.aquasense.security.CurrentUser;
import com.aquasense.user.dto.SettingsDto;
import com.aquasense.user.dto.UserResponse;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserRepository userRepository;
    private final UserSettingsRepository settingsRepository;
    private final ProfileService profileService;
    private final AuditService auditService;
    private final AuthService authService;

    public UserController(UserRepository userRepository, UserSettingsRepository settingsRepository,
                          ProfileService profileService, AuditService auditService, AuthService authService) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.settingsRepository = settingsRepository;
        this.profileService = profileService;
        this.auditService = auditService;
    }

    @GetMapping
    public UserResponse me() {
        return userRepository.findById(CurrentUser.id()).map(UserResponse::from)
            .orElseThrow(() -> BusinessException.notFound("La cuenta no existe."));
    }

    @GetMapping("/settings")
    @Transactional
    public SettingsDto settings() {
        return SettingsDto.from(settingsFor(CurrentUser.id()));
    }

    @PutMapping("/settings")
    @Transactional
    public SettingsDto updateSettings(@Valid @RequestBody SettingsDto request) {
        Long userId = CurrentUser.id();
        UserSettings settings = settingsFor(userId);
        settings.update(request.theme(), request.notificationsEnabled(), request.autoSave(),
            request.language() == null ? "es" : request.language());
        if (request.activeProfileId() != null) {
            settings.activateProfile(profileService.getVisible(userId, request.activeProfileId()).getId());
        }
        auditService.record(userId, "SETTINGS_UPDATED", "Tema " + request.theme());
        return SettingsDto.from(settings);
    }

    /** {@code label}: readable name of the action, as the session history shows it. */
    public record ActivityItem(Long id, String action, String label, String detail, Instant createdAt, Long sessionId) {
    }

    @GetMapping("/activity")
    public List<ActivityItem> activity() {
        return auditService.latest(CurrentUser.id(), 50).stream()
            .map(e -> new ActivityItem(e.getId(), e.getAction(), com.aquasense.audit.ActionLabels.of(e.getAction()), e.getDetail(),
                e.getCreatedAt(), e.getSessionId()))
            .toList();
    }

    private UserSettings settingsFor(Long userId) {
        return settingsRepository.findById(userId).orElseGet(() ->
            settingsRepository.save(UserSettings.defaultsFor(userId, profileService.defaultProfile().getId())));
    }

    /** Permanently deletes the account of the current user and all their data. The password is required again. */
    @DeleteMapping
    public ResponseEntity<Void> deleteAccount(@Valid @RequestBody DeleteAccountRequest request) {
        authService.deleteOwnAccount(CurrentUser.id(), request.password());
        return ResponseEntity.noContent().build();
    }

    public record DeleteAccountRequest(@jakarta.validation.constraints.NotBlank(message = "Escribe tu contraseña.") String password) {
    }
}
