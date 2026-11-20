package com.aquaassistant.notify;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bridge, abstraction side: a kind of notice (an alert of the water, a reminder) decides which channels it goes
 * through; the channels ({@link NoticeChannel}) decide how it is delivered. The two hierarchies change apart: a new
 * kind of notice works with every channel, and a new channel serves every kind of notice.
 */
public abstract class Notice {

    private static final Logger log = LoggerFactory.getLogger(Notice.class);

    protected final NoticeChannel push;
    protected final NoticeChannel email;
    /** Text message to the mobile number; {@code null} when this kind of notice never goes by SMS. */
    protected final NoticeChannel sms;

    protected Notice(NoticeChannel push, NoticeChannel email) {
        this(push, email, null);
    }

    protected Notice(NoticeChannel push, NoticeChannel email, NoticeChannel sms) {
        this.push = push;
        this.email = email;
        this.sms = sms;
    }

    /** The channels this notice uses for this person and this message. */
    protected abstract List<NoticeChannel> channels(Notifier.Message message);

    /** The kind that groups repeated notices of this type. */
    protected abstract String kind(Notifier.Message message);

    /** Sends through every chosen channel; a channel that fails does not stop the others. */
    public final void send(Long userId, Notifier.Message message) {
        NoticeContent content = NoticeContent.of(kind(message), message);
        for (NoticeChannel channel : channels(message)) {
            try {
                channel.deliver(userId, content);
            } catch (RuntimeException e) {
                log.warn("Notification for user {} failed: {}", userId, e.getMessage());
            }
        }
    }
}
