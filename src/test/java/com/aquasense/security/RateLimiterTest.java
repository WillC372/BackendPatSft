package com.aquasense.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquasense.common.BusinessException;
import java.time.Clock;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

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
}
