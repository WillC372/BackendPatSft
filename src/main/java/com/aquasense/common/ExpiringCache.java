package com.aquasense.common;

import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * In-memory cache that cleans itself: an entry not used for {@code idleTime} expires, and when the cache holds more
 * than {@code maxEntries} the entry used longest ago is removed first (least recently used). Expired entries are
 * never returned; {@link #evictExpired()} frees their memory and is called on a schedule by the owner.
 * Thread-safe: building a missing value locks only that key, so other users are not kept waiting.
 */
public final class ExpiringCache<K, V> {

    private final Map<K, Entry<V>> entries = new ConcurrentHashMap<>();
    private final Clock clock;
    private final long idleMillis;
    private final int maxEntries;

    public ExpiringCache(Duration idleTime, int maxEntries) {
        this(Clock.systemUTC(), idleTime, maxEntries);
    }

    public ExpiringCache(Clock clock, Duration idleTime, int maxEntries) {
        if (idleTime.isNegative() || idleTime.isZero() || maxEntries < 1) {
            throw new IllegalArgumentException("idleTime must be positive and maxEntries at least 1");
        }
        this.clock = clock;
        this.idleMillis = idleTime.toMillis();
        this.maxEntries = maxEntries;
    }

    /** The value for {@code key}, built with {@code loader} if it is missing or expired. */
    public V get(K key, Function<? super K, ? extends V> loader) {
        long now = clock.millis();
        Entry<V> entry = entries.compute(key, (k, old) -> {
            if (old != null && !old.isExpired(now, idleMillis)) {
                old.lastUsed = now;
                return old;
            }
            return new Entry<>(loader.apply(k), now);
        });
        trimToSize();
        return entry.value;
    }

    /** The value for {@code key} if present and not expired, or {@code null}. Counts as a use. */
    public V getIfPresent(K key) {
        long now = clock.millis();
        Entry<V> entry = entries.get(key);
        if (entry == null || entry.isExpired(now, idleMillis)) {
            return null;
        }
        entry.lastUsed = now;
        return entry.value;
    }

    public void put(K key, V value) {
        entries.put(key, new Entry<>(value, clock.millis()));
        trimToSize();
    }

    public void remove(K key) {
        entries.remove(key);
    }

    /** Removes the entries whose key matches (for example, every model of a user who added a measurement). */
    public void removeIf(Predicate<? super K> keyFilter) {
        entries.keySet().removeIf(keyFilter);
    }

    /** Removes the expired entries and returns how many were removed. */
    public int evictExpired() {
        long now = clock.millis();
        int before = entries.size();
        entries.values().removeIf(e -> e.isExpired(now, idleMillis));
        return Math.max(0, before - entries.size());
    }

    public int size() {
        return entries.size();
    }

    private void trimToSize() {
        while (entries.size() > maxEntries) {
            entries.entrySet().stream().min(Comparator.comparingLong(e -> e.getValue().lastUsed))
                .ifPresent(oldest -> entries.remove(oldest.getKey(), oldest.getValue()));
        }
    }

    private static final class Entry<V> {
        private final V value;
        private volatile long lastUsed;

        Entry(V value, long now) {
            this.value = value;
            this.lastUsed = now;
        }

        boolean isExpired(long now, long idleMillis) {
            return now - lastUsed >= idleMillis;
        }
    }
}
