package com.aquaassistant.i18n;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Limits how many NEW texts (not yet translated, so they cost an AI call) can be sent: per address per hour, and in
 * total per day. Texts already translated are always free. It keeps the translation endpoint, which is open before
 * signing in, from being used as a general translator.
 */
@Component
public class TranslationBudget {

    public static final String SERVER = "server";
    private static final Duration HOUR = Duration.ofHours(1);
    private static final Duration DAY = Duration.ofDays(1);

    private final Clock clock;
    private final int perAddressPerHour;
    private final int perDay;
    private final Map<String, long[]> windows = new ConcurrentHashMap<>();
    private final long[] day = {0, 0};

    @Autowired
    public TranslationBudget(@Value("${aquasense.i18n.new-texts-per-address-hour:2500}") int perAddressPerHour,
                             @Value("${aquasense.i18n.new-texts-per-day:60000}") int perDay) {
        this(Clock.systemUTC(), perAddressPerHour, perDay);
    }

    TranslationBudget(Clock clock, int perAddressPerHour, int perDay) {
        this.clock = clock;
        this.perAddressPerHour = perAddressPerHour;
        this.perDay = perDay;
    }

    /** How many of {@code wanted} new texts {@code who} may send now (they are counted). */
    public synchronized int take(String who, int wanted) {
        if (wanted <= 0) {
            return 0;
        }
        long now = clock.millis();
        if (now - day[0] > DAY.toMillis()) {
            day[0] = now;
            day[1] = 0;
        }
        int allowed = (int) Math.max(0, Math.min(wanted, perDay - day[1]));
        if (!SERVER.equals(who)) {
            long[] window = windows.computeIfAbsent(who, k -> new long[] {now, 0});
            if (now - window[0] > HOUR.toMillis()) {
                window[0] = now;
                window[1] = 0;
            }
            allowed = (int) Math.max(0, Math.min(allowed, perAddressPerHour - window[1]));
            window[1] += allowed;
        }
        day[1] += allowed;
        return allowed;
    }

    @Scheduled(fixedDelay = 600_000)
    public void forgetOld() {
        long now = clock.millis();
        windows.entrySet().removeIf(e -> now - e.getValue()[0] > HOUR.toMillis());
    }
}
