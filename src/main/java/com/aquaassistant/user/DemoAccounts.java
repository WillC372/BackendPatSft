package com.aquaassistant.user;

import com.aquaassistant.common.BusinessException;
import com.aquaassistant.profile.ProfileService;
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
 * plan, a Premium user and an administrator. They are real accounts of the database, shared by everyone who presses
 * the button, and they can do everything a normal account of their role does (pay in the PayU sandbox, change their
 * name, photo or password, share places, delete the account...). So the buttons always work, each account is put back
 * as it was (created again if it was deleted, with its password, name, role and plan) every time someone logs in with
 * it, and at startup.
 * <p>The demonstration administrator manages the demonstration accounts and the example accounts (@example.com) like
 * any administrator, but the accounts of real people stay private: it sees their names and emails masked and cannot
 * open or change them.
 * Disabled with {@code DEMO_ACCOUNTS_ENABLED=false}.
 */
@Component
public class DemoAccounts implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoAccounts.class);

    /** The accounts offered on the login screen. */
    public enum Kind {
        USER("usuario@demo.aquaassistant", "Usuario Demo", "Usuario",
            "Plan gratuito, con sus límites: 20 consultas de IA al día y análisis de los últimos 30 días.", Role.USER, Plan.FREE),
        PREMIUM("premium@demo.aquaassistant", "Usuario Premium Demo", "Premium",
            "Plan Premium: todas las funciones, 200 consultas de IA al día y análisis de hasta 365 días.", Role.USER, Plan.PREMIUM),
        ADMIN("admin@demo.aquaassistant", "Administrador Demo", "Administrador",
            "Trabaja para la empresa: cuentas, planes, pagos, consumo de IA, datos oficiales y sesiones. No mide pH.",
            Role.ADMIN, Plan.PREMIUM);

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

    /** Demonstration accounts no longer offered. Their password is public, so they are disabled; their data stays. */
    static final List<String> RETIRED = List.of();

    public record DemoAccount(String key, String label, String description, String email, String password) {
    }

    private final boolean enabled;
    private final String password;
    private final UserRepository users;
    private final UserSettingsRepository settings;
    private final PasswordEncoder encoder;
    private final ProfileService profiles;

    /** The address of each account: configurable, so the buttons can use real mailboxes (DEMO_USER_EMAIL...). */
    private final java.util.Map<Kind, String> emails = new java.util.EnumMap<>(Kind.class);

    public DemoAccounts(@Value("${aquasense.demo.enabled:true}") boolean enabled,
                        @Value("${aquasense.demo.password:AquaAssistant-Demo-2026}") String password,
                        @Value("${aquasense.demo.user-email:}") String userEmail,
                        @Value("${aquasense.demo.premium-email:}") String premiumEmail,
                        @Value("${aquasense.demo.admin-email:}") String adminEmail,
                        UserRepository users, UserSettingsRepository settings, PasswordEncoder encoder, ProfileService profiles) {
        emails.put(Kind.USER, blank(userEmail) ? Kind.USER.email : userEmail.trim().toLowerCase(java.util.Locale.ROOT));
        emails.put(Kind.PREMIUM, blank(premiumEmail) ? Kind.PREMIUM.email : premiumEmail.trim().toLowerCase(java.util.Locale.ROOT));
        emails.put(Kind.ADMIN, blank(adminEmail) ? Kind.ADMIN.email : adminEmail.trim().toLowerCase(java.util.Locale.ROOT));
        this.enabled = enabled;
        this.password = password;
        this.users = users;
        this.settings = settings;
        this.encoder = encoder;
        this.profiles = profiles;
    }

    /** Accounts for the login buttons. Their password is public on purpose: it only opens these shared accounts. */
    public List<DemoAccount> list() {
        if (!enabled) {
            return List.of();
        }
        return Arrays.stream(Kind.values())
            .map(k -> new DemoAccount(k.name(), k.label, k.description, emailOf(k), password)).toList();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        for (Kind kind : Kind.values()) {
            restore(kind);
        }
        for (String email : RETIRED) {
            users.findByEmailIgnoreCase(email).filter(u -> u.isDemo() && u.isEnabled()).ifPresent(u -> {
                u.disable("Cuenta de demostración retirada", java.time.Instant.now());
                log.info("{} is no longer a demonstration account and was disabled.", email);
            });
        }
        log.info("Demonstration accounts ready ({}).", Arrays.toString(Kind.values()));
    }

    /**
     * Puts the demonstration account back as the button offers it: created again if it was deleted, enabled, with the
     * public password, its name, role and plan. Its places, measurements and history stay.
     */
    @Transactional
    public void restore(Kind kind) {
        String email = emailOf(kind);
        Optional<User> found = users.findByEmailIgnoreCase(email);
        if (found.isPresent() && !found.get().isDemo()) {
            log.warn("{} belongs to a normal account; it is not used as a demonstration account.", email);
            return;
        }
        if (found.isEmpty() && !email.equalsIgnoreCase(kind.email)) {
            // The address changed in the configuration: the account keeps its places, measurements and history.
            found = users.findByEmailIgnoreCase(kind.email).filter(User::isDemo);
            found.ifPresent(u -> {
                u.changeEmail(email);
                log.info("Demonstration account {} now uses {}.", kind, email);
            });
        }
        User user = found.orElseGet(() -> {
            User created = users.save(new User(email, kind.fullName, encoder.encode(password), Role.USER));
            settings.save(UserSettings.defaultsFor(created.getId(), profiles.defaultProfile().getId()));
            profiles.provisionFirstPlace(created.getId());
            return created;
        });
        user.markDemo();
        if (!kind.fullName.equals(user.getFullName())) {
            user.rename(kind.fullName);
        }
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

    /** Restores the demonstration account with that email before someone logs in with it (no-op for other emails). */
    @Transactional
    public void restoreBeforeLogin(String email) {
        if (!enabled || email == null) {
            return;
        }
        String normalized = email.trim();
        for (Kind kind : Kind.values()) {
            if (emailOf(kind).equalsIgnoreCase(normalized)) {
                restore(kind);
            }
        }
    }

    /**
     * Whether the demonstration administrator may see and change this account: the demonstration accounts and the
     * example accounts (@example.com). The accounts of real people stay private.
     */
    public boolean isOpenToDemo(Long userId) {
        return userId != null && users.findById(userId)
            .map(u -> u.isDemo() || u.getEmail().toLowerCase(java.util.Locale.ROOT).endsWith("@example.com")).orElse(false);
    }

    public String emailOf(Kind kind) {
        return emails.get(kind);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
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
