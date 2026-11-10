package com.aquasense.user;

import com.aquasense.common.BusinessException;
import com.aquasense.profile.ProfileService;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demonstration accounts shown as buttons on the login screen, one per role of the case study: a user with the free
 * plan, a Premium user and an administrator. They are real accounts of the database (created at startup if missing),
 * shared by everyone who presses the button, so they have limits:
 * <ul>
 *   <li>They cannot be deleted, change or recover their password, or pay.</li>
 *   <li>The demonstration administrator can only look: it cannot change plans, roles or states, delete accounts or
 *       send mail, and it does not see the data of real accounts (their names and emails are masked and their detail
 *       is not shown).</li>
 *   <li>Real administrators cannot modify them either, so the buttons always work.</li>
 * </ul>
 * Disabled with {@code DEMO_ACCOUNTS_ENABLED=false}.
 */
@Component
public class DemoAccounts implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoAccounts.class);

    /** One account per role. */
    public enum Kind {
        USER("usuario@demo.aquasense", "Usuaria Demo", "Usuario", "Plan gratuito: mediciones, historial y 20 consultas de IA al día.",
            Role.USER, Plan.FREE),
        PREMIUM("premium@demo.aquasense", "Usuario Premium Demo", "Usuario Premium",
            "Plan Premium: 200 consultas de IA al día y análisis de hasta 365 días.", Role.USER, Plan.PREMIUM),
        ADMIN("admin@demo.aquasense", "Administración Demo", "Administrador",
            "Panel de administración en modo de solo lectura.", Role.ADMIN, Plan.FREE);

        final String email;
        final String fullName;
        final String label;
        final String description;
        final Role role;
        final Plan plan;

        Kind(String email, String fullName, String label, String description, Role role, Plan plan) {
            this.email = email;
            this.fullName = fullName;
            this.label = label;
            this.description = description;
            this.role = role;
            this.plan = plan;
        }
    }

    public record DemoAccount(String key, String label, String description, String email, String password) {
    }

    private final boolean enabled;
    private final String password;
    private final UserRepository users;
    private final UserSettingsRepository settings;
    private final PasswordEncoder encoder;
    private final ProfileService profiles;

    public DemoAccounts(@Value("${aquasense.demo.enabled:true}") boolean enabled,
                        @Value("${aquasense.demo.password:AquaSense-Demo-2026}") String password,
                        UserRepository users, UserSettingsRepository settings, PasswordEncoder encoder, ProfileService profiles) {
        this.enabled = enabled;
        this.password = password;
        this.users = users;
        this.settings = settings;
        this.encoder = encoder;
        this.profiles = profiles;
    }

    /** Accounts for the login buttons. Their password is public on purpose: it only opens these limited accounts. */
    public List<DemoAccount> list() {
        if (!enabled) {
            return List.of();
        }
        return Arrays.stream(Kind.values())
            .map(k -> new DemoAccount(k.name(), k.label, k.description, k.email, password)).toList();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        for (Kind kind : Kind.values()) {
            Optional<User> found = users.findByEmailIgnoreCase(kind.email);
            if (found.isPresent() && !found.get().isDemo()) {
                log.warn("{} belongs to a normal account; it is not used as a demonstration account.", kind.email);
                continue;
            }
            User user = found.orElseGet(() -> {
                User created = users.save(new User(kind.email, kind.fullName, encoder.encode(password), Role.USER));
                settings.save(UserSettings.defaultsFor(created.getId(), profiles.defaultProfile().getId()));
                return created;
            });
            user.markDemo();
            if (!encoder.matches(password, user.getPasswordHash())) {
                user.changePassword(encoder.encode(password));
            }
            if (!user.isEnabled()) {
                user.enable();
            }
            if (user.getRole() != kind.role) {
                if (kind.role == Role.ADMIN) {
                    user.promoteToAdmin();
                } else {
                    user.demoteToUser();
                }
            }
            if (user.getPlan() != kind.plan || user.getPremiumUntil() != null) {
                user.changePlan(kind.plan, null);
            }
        }
        log.info("Demonstration accounts ready ({}).", Arrays.toString(Kind.values()));
    }

    /** Rejects the operation if {@code userId} is a demonstration account. */
    public void forbidFor(Long userId, String message) {
        if (isDemo(userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, message);
        }
    }

    public boolean isDemo(Long userId) {
        return userId != null && users.findById(userId).map(User::isDemo).orElse(false);
    }

    /** "w***@gmail.com": how the demonstration administrator sees the email of a real account. */
    public static String maskEmail(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        return at <= 0 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }

    /** "William C.": first name and the initial of the next one. */
    public static String maskName(String name) {
        if (name == null || name.isBlank()) {
            return "***";
        }
        String[] parts = name.trim().split("\\s+");
        return parts.length == 1 ? parts[0] : parts[0] + " " + parts[1].charAt(0) + ".";
    }
}
