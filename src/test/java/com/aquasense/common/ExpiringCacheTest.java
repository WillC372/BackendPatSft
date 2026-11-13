package com.aquasense.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ExpiringCacheTest {

    private static final class MovingClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");
        void advance(Duration d) { now = now.plus(d); }
        @Override public Instant instant() { return now; }
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
    }

    @Test
    void aValueIsBuiltOnceAndReusedWhileItIsUsed() {
        MovingClock clock = new MovingClock();
        ExpiringCache<String, Integer> cache = new ExpiringCache<>(clock, Duration.ofMinutes(30), 10);
        AtomicInteger builds = new AtomicInteger();
        for (int i = 0; i < 5; i++) {
            assertThat(cache.get("a", k -> builds.incrementAndGet())).isEqualTo(1);
            clock.advance(Duration.ofMinutes(20)); // used every 20 minutes: never idle for 30
        }
        assertThat(builds).hasValue(1);
    }

    @Test
    void anUnusedValueExpiresAndIsRemovedByTheCleanup() {
        MovingClock clock = new MovingClock();
        ExpiringCache<String, String> cache = new ExpiringCache<>(clock, Duration.ofMinutes(30), 10);
        cache.put("old", "x");
        clock.advance(Duration.ofMinutes(20));
        cache.put("recent", "y");
        clock.advance(Duration.ofMinutes(15));

        assertThat(cache.getIfPresent("old")).isNull();
        assertThat(cache.size()).isEqualTo(2);
        assertThat(cache.evictExpired()).isEqualTo(1);
        assertThat(cache.size()).isEqualTo(1);
        assertThat(cache.getIfPresent("recent")).isEqualTo("y");
        assertThat(cache.get("old", k -> "rebuilt")).isEqualTo("rebuilt");
    }

    @Test
    void whenFullTheEntryUsedLongestAgoLeavesFirst() {
        MovingClock clock = new MovingClock();
        ExpiringCache<String, String> cache = new ExpiringCache<>(clock, Duration.ofHours(1), 2);
        cache.put("a", "1");
        clock.advance(Duration.ofSeconds(1));
        cache.put("b", "2");
        clock.advance(Duration.ofSeconds(1));
        cache.getIfPresent("a"); // a is now more recent than b
        clock.advance(Duration.ofSeconds(1));
        cache.put("c", "3");

        assertThat(cache.size()).isEqualTo(2);
        assertThat(cache.getIfPresent("b")).isNull();
        assertThat(cache.getIfPresent("a")).isEqualTo("1");
        assertThat(cache.getIfPresent("c")).isEqualTo("3");
    }

    @Test
    void entriesCanBeRemovedByKey() {
        ExpiringCache<String, String> cache = new ExpiringCache<>(Duration.ofMinutes(5), 10);
        cache.put("user1-a", "x");
        cache.put("user1-b", "y");
        cache.put("user2-a", "z");
        cache.removeIf(k -> k.startsWith("user1"));
        assertThat(cache.size()).isEqualTo(1);
    }
}
