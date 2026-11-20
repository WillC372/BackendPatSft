package com.aquaassistant.notify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Concrete decorator: lets through at most one notice of each kind per person in a period (one hour for email), so a
 * meter that measures every few seconds does not fill the inbox. The alarm and each reminder count as their own kind.
 */
public class OncePerHourChannel extends ChannelDecorator {

    private static final int MAX_KEYS = 5000;

    private final Duration gap;
    private final Clock clock;
    private final Map<String, Instant> last = new ConcurrentHashMap<>();

    public OncePerHourChannel(NoticeChannel inner) {
        this(inner, Duration.ofHours(1), Clock.systemUTC());
    }

    OncePerHourChannel(NoticeChannel inner, Duration gap, Clock clock) {
        super(inner);
        this.gap = gap;
        this.clock = clock;
    }

    @Override
    public boolean deliver(Long userId, NoticeContent content) {
        String key = userId + "|" + content.kind();
        Instant now = clock.instant();
        Instant previous = last.get(key);
        if (previous != null && previous.plus(gap).isAfter(now)) {
            return false;
        }
        last.put(key, now);
        if (last.size() > MAX_KEYS) {
            last.entrySet().removeIf(e -> e.getValue().plus(gap).isBefore(now));
        }
        return inner.deliver(userId, content);
    }
}
