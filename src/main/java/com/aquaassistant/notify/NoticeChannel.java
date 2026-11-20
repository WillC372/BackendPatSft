package com.aquaassistant.notify;

/**
 * Bridge, implementor side: how a notice reaches the person (Web Push, email). The notices ({@link Notice}) only
 * decide which channels to use, so every kind of notice works with every channel without a class per combination.
 */
public interface NoticeChannel {

    /** Delivers the notice; returns whether it went out. */
    boolean deliver(Long userId, NoticeContent content);
}
