package com.aquaassistant.notify;

import java.util.ArrayList;
import java.util.List;

/** Refined abstraction: a reminder goes through the channels the person chose for it; each reminder is its own kind. */
public class ReminderNotice extends Notice {

    private final boolean byPush;
    private final boolean byEmail;

    public ReminderNotice(NoticeChannel push, NoticeChannel email, boolean byPush, boolean byEmail) {
        super(push, email);
        this.byPush = byPush;
        this.byEmail = byEmail;
    }

    @Override
    protected List<NoticeChannel> channels() {
        List<NoticeChannel> out = new ArrayList<>(2);
        if (byPush) {
            out.add(push);
        }
        if (byEmail) {
            out.add(email);
        }
        return out;
    }

    @Override
    protected String kind(Notifier.Message message) {
        return "reminder:" + message.title();
    }
}
