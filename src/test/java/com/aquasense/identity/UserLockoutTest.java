package com.aquasense.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquasense.identity.domain.User;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserLockoutTest {

    private final Instant now = Instant.parse("2026-11-02T12:00:00Z");

    @Test
    void locksAccountAfterMaxFailedAttempts() {
        User user = new User("a@b.co", null, "Ana", "hash");
        for (int i = 0; i < User.MAX_FAILED_ATTEMPTS; i++) {
            assertThat(user.isLocked(now)).isFalse();
            user.registerFailedLogin(now);
        }
        assertThat(user.isLocked(now)).isTrue();
        assertThat(user.isLocked(now.plusSeconds(User.LOCK_MINUTES * 60 + 1))).isFalse();
    }

    @Test
    void successfulLoginClearsCounters() {
        User user = new User("a@b.co", null, "Ana", "hash");
        user.registerFailedLogin(now);
        user.registerSuccessfulLogin();
        for (int i = 0; i < User.MAX_FAILED_ATTEMPTS - 1; i++) {
            user.registerFailedLogin(now);
        }
        assertThat(user.isLocked(now)).isFalse();
    }
}
