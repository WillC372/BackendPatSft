package com.aquaassistant.notify;

/** Concrete implementor: to the person's phones and computers through Web Push. */
public class WebPushChannel implements NoticeChannel {

    private final PushService push;

    public WebPushChannel(PushService push) {
        this.push = push;
    }

    @Override
    public boolean deliver(Long userId, NoticeContent content) {
        return push.send(userId, content.title(), content.body(), content.url(), content.alarm()) > 0;
    }
}
