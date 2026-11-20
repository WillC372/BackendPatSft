package com.aquaassistant.notify;

import com.aquaassistant.user.UserSettings;
import java.util.ArrayList;
import java.util.List;

/**
 * Refined abstraction: an alert of the water, or the alarm when the pH is altered. It follows the person's settings:
 * to the phone if notifications are on, by email if "alertas por correo" is on (both, when there are no settings).
 * The alarm is its own kind, so its email does not wait behind the email of an earlier, milder alert. Only the alarm
 * also goes by SMS (if the person keeps "Alarma por SMS" on), because each text message costs money.
 */
public class AlertNotice extends Notice {

    private final boolean pushOn;
    private final boolean emailOn;
    private final boolean smsOn;

    public AlertNotice(NoticeChannel push, NoticeChannel email, UserSettings settings) {
        this(push, email, null, settings);
    }

    public AlertNotice(NoticeChannel push, NoticeChannel email, NoticeChannel sms, UserSettings settings) {
        super(push, email, sms);
        this.pushOn = settings == null || settings.isNotificationsEnabled();
        this.emailOn = settings == null || settings.isEmailAlerts();
        this.smsOn = settings == null || settings.isSmsAlerts();
    }

    @Override
    protected List<NoticeChannel> channels(Notifier.Message message) {
        List<NoticeChannel> out = new ArrayList<>(3);
        if (pushOn) {
            out.add(push);
        }
        if (emailOn) {
            out.add(email);
        }
        if (sms != null && smsOn && message.alarm()) {
            out.add(sms);
        }
        return out;
    }

    @Override
    protected String kind(Notifier.Message message) {
        return message.alarm() ? "alarm" : "alert";
    }
}
