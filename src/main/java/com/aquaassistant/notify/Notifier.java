package com.aquaassistant.notify;

import com.aquaassistant.user.MailService;
import com.aquaassistant.user.UserRepository;
import com.aquaassistant.user.UserSettings;
import com.aquaassistant.user.UserSettingsRepository;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Service;

/**
 * Notifications outside the app: to the phone or computer (Web Push) and by email. They are sent in the background
 * so saving a measurement does not wait for them.
 *
 * <p>Client of two patterns: Bridge ({@link Notice} says what is notified and through which channels;
 * {@link NoticeChannel} says how it is delivered: Web Push, email or SMS) and Decorator (push and SMS are translated
 * into the person's language; email and SMS let through one notice of each kind per hour).
 */
@Service
public class Notifier {

    /** {@code alarm}: an alteration of the pH; the notice is urgent, stays on screen and vibrates. */
    public record Message(String title, String body, String url, String emailSubject, List<String> emailParagraphs,
                          boolean alarm) {

        public Message(String title, String body, String url, String emailSubject, List<String> emailParagraphs) {
            this(title, body, url, emailSubject, emailParagraphs, false);
        }
    }

    private final PushService push;
    private final UserSettingsRepository settings;
    private final NoticeChannel pushChannel;
    private final NoticeChannel emailChannel;
    private final NoticeChannel smsChannel;
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

    public Notifier(PushService push, MailService mail, UserRepository users, UserSettingsRepository settings,
                    SmsService sms) {
        this.push = push;
        this.settings = settings;
        this.pushChannel = new TranslatedChannel(new WebPushChannel(push), this::localized);
        this.emailChannel = new OncePerHourChannel(new EmailChannel(mail, users));
        // Each SMS costs: at most one per hour of each kind, translated like the push notice.
        this.smsChannel = new OncePerHourChannel(new TranslatedChannel(new SmsChannel(sms, users), this::localized));
    }

    /** Texts in the person's language, in the same order; Spanish if the translation is not available now. */
    private List<String> localized(Long userId, List<String> texts) {
        if (language == null || translations == null) {
            return texts;
        }
        String lang = language.of(userId);
        if (com.aquaassistant.i18n.Languages.isOriginal(lang)) {
            return texts;
        }
        return translations.translateAll(lang, texts);
    }

    /**
     * Alert of a measurement: push if notifications are on, email if the person kept "alertas por correo". The alarm's
     * email (an alteration of the pH) does not wait behind the email of an earlier, milder alert.
     */
    public void alert(Long userId, Message message) {
        UserSettings s = settings.findById(userId).orElse(null);
        Notice notice = new AlertNotice(pushChannel, emailChannel, smsChannel, s);
        background.submit(() -> notice.send(userId, message));
    }

    /** Whether an email to this address can arrive (see {@link EmailChannel#deliverable}). */
    public static boolean deliverable(String email) {
        return EmailChannel.deliverable(email);
    }

    /** Reminder: through the channels the reminder asks for. */
    public void reminder(Long userId, Message message, boolean byPush, boolean byEmail) {
        Notice notice = new ReminderNotice(pushChannel, emailChannel, byPush, byEmail);
        background.submit(() -> notice.send(userId, message));
    }

    /** Sends right away and tells how many devices got it (used by "Probar aviso"). */
    public int testPush(Long userId) {
        List<String> text = localized(userId, List.of("Aqua Assistant AI", "Así se verán los avisos en este dispositivo."));
        return push.send(userId, text.get(0), text.get(1), "/#/settings");
    }
}
