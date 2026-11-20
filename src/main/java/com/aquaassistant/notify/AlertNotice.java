package com.aquaassistant.notify;

import com.aquaassistant.user.UserSettings;
import java.util.ArrayList;
import java.util.List;

/**
 * Refined abstraction: an alert of the water, or the alarm when the pH is altered. It follows the person's settings:
 * to the phone if notifications are on, by email if "alertas por correo" is on (both, when there are no settings).
 * The alarm is its own kind, so its email does not wait behind the email of an earlier, milder alert.
 */
public class AlertNotice extends Notice {

    private final boolean pushOn;
    private final boolean emailOn;

    public AlertNotice(NoticeChannel push, NoticeChannel email, UserSettings settings) {
        super(push, email);
        this.pushOn = settings == null || settings.isNotificationsEnabled();
        this.emailOn = settings == null || settings.isEmailAlerts();
    }

    @Override
    protected List<NoticeChannel> channels() {
        List<NoticeChannel> out = new ArrayList<>(2);
        if (pushOn) {
            out.add(push);
        }
        if (emailOn) {
            out.add(email);
        }
        return out;
    }

    @Override
    protected String kind(Notifier.Message message) {
        return message.alarm() ? "alarm" : "alert";
    }
}
