package com.aquasense.security;

import com.aquasense.common.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sliding-window attempt limiter (Rate Limiting). It protects login and password recovery against brute force:
 * per address ({@link #check(String)}) and, for logins, per account counting only failed attempts
 * ({@link #ensureAllowed} and {@link #recordFailure}), so rotating addresses does not help an attacker.
 * Entries whose window has passed are removed every few minutes, so the map does not grow without limit.
 */
@Component
public class RateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    /** The longest window used; entries older than this are no longer needed. */
    private static final Duration LONGEST = Duration.ofMinutes(15);

    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maxAttempts;

    @org.springframework.beans.factory.annotation.Autowired
    public RateLimiter(com.aquasense.config.AppProperties properties) {
        this(Clock.systemUTC(), properties.security().rateLimitPerMinute() > 0 ? properties.security().rateLimitPerMinute() : 10);
    }

    RateLimiter(Clock clock, int maxAttempts) {
        this.clock = clock;
        this.maxAttempts = maxAttempts;
    }

    /** Counts an attempt for {@code key} and rejects it beyond the per-minute limit. */
    public void check(String key) {
        ensureAllowed(key, maxAttempts, WINDOW);
        record(key);
    }

    /** Rejects the request if {@code key} already reached {@code max} attempts in {@code window}; counts nothing. */
    public void ensureAllowed(String key, int max, Duration window) {
        Deque<Long> times = attempts.get(key);
        if (times == null) {
            return;
        }
        long now = clock.millis();
        synchronized (times) {
            times.removeIf(t -> now - t > window.toMillis());
            if (times.size() >= max) {
                long retry = Math.max(1, (window.toMillis() - (now - times.peekFirst())) / 60000 + 1);
                throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, retry <= 1
                    ? "Demasiados intentos. Espera un minuto antes de intentarlo de nuevo."
                    : "Demasiados intentos fallidos. Espera " + retry + " minutos antes de intentarlo de nuevo.");
            }
        }
    }

    /** Counts a failed attempt (for limits that only count failures). */
    public void recordFailure(String key) {
        record(key);
    }

    private void record(String key) {
        Deque<Long> times = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            times.addLast(clock.millis());
        }
    }

    /** Removes the entries whose attempts are all older than the longest window. */
    @Scheduled(fixedDelay = 300_000, initialDelay = 300_000)
    public void forgetOldAttempts() {
        long now = clock.millis();
        attempts.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                Long last = e.getValue().peekLast();
                return last == null || now - last > LONGEST.toMillis();
            }
        });
    }

    int trackedKeys() {
        return attempts.size();
    }
}
