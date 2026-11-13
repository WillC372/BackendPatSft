package com.aquasense.user;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes the password-reset and verification links that no longer serve: used or expired for more than a day.
 * They only hold a hash, but keeping them forever makes the table grow for nothing.
 */
@Component
public class TokenCleanup {

    private static final Logger LOG = LoggerFactory.getLogger(TokenCleanup.class);
    static final Duration KEEP = Duration.ofDays(1);

    private final PasswordResetTokenRepository tokens;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public TokenCleanup(PasswordResetTokenRepository tokens) {
        this(tokens, Clock.systemUTC());
    }

    TokenCleanup(PasswordResetTokenRepository tokens, Clock clock) {
        this.tokens = tokens;
        this.clock = clock;
    }

    /** Every 6 hours (the first run an hour after starting). */
    @Scheduled(fixedDelay = 21_600_000, initialDelay = 3_600_000)
    @Transactional
    public int deleteFinishedTokens() {
        int deleted = tokens.deleteFinishedBefore(Instant.now(clock).minus(KEEP));
        if (deleted > 0) {
            LOG.info("Deleted {} used or expired account links", deleted);
        }
        return deleted;
    }
}
