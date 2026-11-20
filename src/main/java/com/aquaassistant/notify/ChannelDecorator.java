package com.aquaassistant.notify;

/**
 * Decorator: wraps a channel and adds one capability without changing it. Decorators stack, so each channel gets
 * only the capabilities it needs (the push notice is translated; the email is limited to once per hour).
 */
public abstract class ChannelDecorator implements NoticeChannel {

    protected final NoticeChannel inner;

    protected ChannelDecorator(NoticeChannel inner) {
        this.inner = inner;
    }
}
