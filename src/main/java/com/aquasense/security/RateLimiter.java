package com.aquasense.security;

import com.aquasense.common.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Sliding-window attempt limiter (Rate Limiting).
 * It protects login and password recovery against brute force.
 */
@Component
public class RateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

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

    public void check(String key) {
        long now = clock.millis();
        Deque<Long> window = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() > WINDOW.toMillis()) {
                window.pollFirst();
            }
            if (window.size() >= maxAttempts) {
                throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,
                    "Demasiados intentos. Espera un minuto antes de intentarlo de nuevo.");
            }
            window.addLast(now);
        }
    }
}
