package com.aquaassistant.notify;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Decorator: capabilities that wrap a channel without changing it, and stack. */
class ChannelDecoratorTest {

    private final List<NoticeContent> delivered = new ArrayList<>();
    private final NoticeChannel channel = (userId, content) -> delivered.add(content);

    @Test
    void theTranslatedChannelSendsTheTextInThePersonsLanguage() {
        NoticeChannel translated = new TranslatedChannel(channel,
            (userId, texts) -> texts.stream().map(t -> "[en] " + t).toList());
        translated.deliver(1L, content("alarm"));
        assertThat(delivered.get(0).title()).isEqualTo("[en] Alteración del pH en el agua");
        assertThat(delivered.get(0).body()).isEqualTo("[en] El pH subió de 7.30 a 12.67.");
        assertThat(delivered.get(0).alarm()).isTrue();
    }

    @Test
    void onePerHourOfEachKind() {
        MutableClock clock = new MutableClock();
        NoticeChannel limited = new OncePerHourChannel(channel, Duration.ofHours(1), clock);
        assertThat(limited.deliver(1L, content("alert"))).isTrue();
        assertThat(limited.deliver(1L, content("alert"))).isFalse();
        assertThat(limited.deliver(1L, content("alarm"))).isTrue();
        assertThat(limited.deliver(2L, content("alert"))).isTrue();
        clock.now = clock.now.plus(Duration.ofMinutes(61));
        assertThat(limited.deliver(1L, content("alert"))).isTrue();
        assertThat(delivered).hasSize(4);
    }

    @Test
    void decoratorsStack() {
        NoticeChannel both = new OncePerHourChannel(new TranslatedChannel(channel, (userId, texts) -> List.of("A", "B")));
        both.deliver(1L, content("alarm"));
        both.deliver(1L, content("alarm"));
        assertThat(delivered).extracting(NoticeContent::title).containsExactly("A");
    }

    private static NoticeContent content(String kind) {
        return new NoticeContent(kind, "Alteración del pH en el agua", "El pH subió de 7.30 a 12.67.", "/#/alerts",
            "Alarma", List.of("El pH subió."), kind.equals("alarm"));
    }

    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-20T12:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
