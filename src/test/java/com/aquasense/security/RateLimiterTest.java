package com.aquasense.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquasense.common.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class RateLimiterTest {

    /** A clock the test moves by hand. */
    private static final class MovingClock extends Clock {
        private Instant now = Instant.parse("2026-11-13T12:00:00Z");
        void advance(Duration d) { now = now.plus(d); }
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void blocksAfterConfiguredAttemptsPerMinute() {
        RateLimiter limiter = new RateLimiter(Clock.systemUTC(), 3);
        limiter.check("login:1.2.3.4");
        limiter.check("login:1.2.3.4");
        limiter.check("login:1.2.3.4");
        assertThatThrownBy(() -> limiter.check("login:1.2.3.4")).isInstanceOf(BusinessException.class)
            .hasMessageContaining("Demasiados intentos");
        limiter.check("login:5.6.7.8");
    }

    @Test
    void onlyFailuresCountForTheAccountLimitAndTheyExpire() {
        MovingClock clock = new MovingClock();
        RateLimiter limiter = new RateLimiter(clock, 10);
        for (int i = 0; i < 4; i++) {
            limiter.ensureAllowed("acct", 5, Duration.ofMinutes(15));
            limiter.recordFailure("acct");
        }
        // Checking without failing does not count.
        for (int i = 0; i < 20; i++) {
            limiter.ensureAllowed("acct", 5, Duration.ofMinutes(15));
        }
        limiter.recordFailure("acct");
        assertThatThrownBy(() -> limiter.ensureAllowed("acct", 5, Duration.ofMinutes(15)))
            .hasMessageContaining("Espera").hasMessageContaining("minutos");
        clock.advance(Duration.ofMinutes(16));
        assertThatCode(() -> limiter.ensureAllowed("acct", 5, Duration.ofMinutes(15))).doesNotThrowAnyException();
    }

    @Test
    void oldEntriesAreForgottenSoTheMapDoesNotGrowForever() {
        MovingClock clock = new MovingClock();
        RateLimiter limiter = new RateLimiter(clock, 10);
        for (int i = 0; i < 100; i++) {
            limiter.check("login:10.0.0." + i);
        }
        assertThat(limiter.trackedKeys()).isEqualTo(100);
        limiter.forgetOldAttempts();
        assertThat(limiter.trackedKeys()).isEqualTo(100);
        clock.advance(Duration.ofMinutes(16));
        limiter.check("login:10.0.0.200");
        limiter.forgetOldAttempts();
        assertThat(limiter.trackedKeys()).isEqualTo(1);
    }

    @Test
    void theClientAddressComesFromTheProxyHeaderWhenConfigured() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.70.1.9");
        request.addHeader("CF-Connecting-IP", "190.85.4.20");
        assertThat(new ClientAddress("CF-Connecting-IP").of(request)).isEqualTo("190.85.4.20");
        assertThat(new ClientAddress("").of(request)).isEqualTo("172.70.1.9");
        MockHttpServletRequest odd = new MockHttpServletRequest();
        odd.setRemoteAddr("172.70.1.9");
        odd.addHeader("CF-Connecting-IP", "<script>");
        assertThat(new ClientAddress("CF-Connecting-IP").of(odd)).isEqualTo("172.70.1.9");
    }
}
