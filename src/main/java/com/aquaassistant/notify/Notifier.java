package com.aquaassistant.notify;

import com.aquaassistant.user.MailService;
import com.aquaassistant.user.User;
import com.aquaassistant.user.UserRepository;
import com.aquaassistant.user.UserSettings;
import com.aquaassistant.user.UserSettingsRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Notifications outside the app: to the phone or computer (Web Push) and by email. They are sent in the background
 * so saving a measurement does not wait for them. Emails of the same kind go at most once per hour per person.
 */
@Service
public class Notifier {

    private static final Logger log = LoggerFactory.getLogger(Notifier.class);
    private static final Duration EMAIL_GAP = Duration.ofHours(1);

    public record Message(String title, String body, String url, String emailSubject, List<String> emailParagraphs) {
    }

    private final PushService push;
    private final MailService mail;
    private final UserRepository users;
    private final UserSettingsRepository settings;
    private final Map<String, Instant> lastEmail = new ConcurrentHashMap<>();
    private final ExecutorService background = Executors.newVirtualThreadPerTaskExecutor();
    private com.aquaassistant.i18n.UserLanguage language;
    private com.aquaassistant.i18n.TranslationClient translations;

    /** Notices to the phone go in the person's language (optional: without it, in Spanish). */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setTranslation(com.aquaassistant.i18n.UserLanguage language,
                               @org.springframework.context.annotation.Lazy com.aquaassistant.i18n.TranslationClient translations) {
        this.language = language;
        this.translations = translations;
    }

    /** Title and text of a notice in the person's language; Spanish if the translation is not available now. */
    private String[] localized(Long userId, String title, String body) {
        if (language == null || translations == null) {
            return new String[] {title, body};
        }
        String lang = language.of(userId);
        if (com.aquaassistant.i18n.Languages.isOriginal(lang)) {
            return new String[] {title, body};
        }
        List<String> out = translations.translateAll(lang, List.of(title, body));
        return new String[] {out.get(0), out.get(1)};
    }

    public Notifier(PushService push, MailService mail, UserRepository users, UserSettingsRepository settings) {
        this.push = push;
        this.mail = mail;
        this.users = users;
        this.settings = settings;
    }

    /** Alert of a measurement: push if notifications are on, email if the person kept "alertas por correo". */
    public void alert(Long userId, Message message) {
        UserSettings s = settings.findById(userId).orElse(null);
        boolean pushOn = s == null || s.isNotificationsEnabled();
        boolean emailOn = s == null || s.isEmailAlerts();
        send(userId, message, pushOn, emailOn, "alert");
    }

    /** Reminder: through the channels the reminder asks for. */
    public void reminder(Long userId, Message message, boolean byPush, boolean byEmail) {
        send(userId, message, byPush, byEmail, "reminder:" + message.title());
    }

    /** Sends right away and tells how many devices got it (used by "Probar aviso"). */
    public int testPush(Long userId) {
        String[] text = localized(userId, "Aqua Assistant AI", "Así se verán los avisos en este dispositivo.");
        return push.send(userId, text[0], text[1], "/#/settings");
    }

    private void send(Long userId, Message message, boolean byPush, boolean byEmail, String kind) {
        background.submit(() -> {
            try {
                if (byPush) {
                    String[] text = localized(userId, message.title(), message.body());
                    push.send(userId, text[0], text[1], message.url());
                }
                if (byEmail && allowEmail(userId, kind)) {
                    users.findById(userId).filter(User::isEnabled).filter(u -> !u.isDemo())
                        .ifPresent(u -> mail.sendNotice(u.getEmail(), message.emailSubject(), message.title(), message.emailParagraphs()));
                }
            } catch (Exception e) {
                log.warn("Notification for user {} failed: {}", userId, e.getMessage());
            }
        });
    }

    private boolean allowEmail(Long userId, String kind) {
        String key = userId + "|" + kind;
        Instant now = Instant.now();
        Instant previous = lastEmail.get(key);
        if (previous != null && previous.plus(EMAIL_GAP).isAfter(now)) {
            return false;
        }
        lastEmail.put(key, now);
        if (lastEmail.size() > 5000) {
            lastEmail.entrySet().removeIf(e -> e.getValue().plus(EMAIL_GAP).isBefore(now));
        }
        return true;
    }
}
