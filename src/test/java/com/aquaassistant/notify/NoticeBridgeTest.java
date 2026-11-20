package com.aquaassistant.notify;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquaassistant.user.Theme;
import com.aquaassistant.user.UserSettings;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Bridge: the kind of notice chooses the channels; the channels deliver it. */
class NoticeBridgeTest {

    private final Recorder push = new Recorder();
    private final Recorder email = new Recorder();

    @Test
    void anAlertFollowsThePersonsSettings() {
        UserSettings settings = UserSettings.defaultsFor(1L, null);
        settings.update(Theme.LIGHT, false, false, "es");
        settings.setEmailAlerts(true);
        new AlertNotice(push, email, settings).send(1L, message(false));
        assertThat(push.got).isEmpty();
        assertThat(email.got).extracting(NoticeContent::kind).containsExactly("alert");
    }

    @Test
    void theAlarmIsItsOwnKindAndGoesEverywhereWithoutSettings() {
        new AlertNotice(push, email, null).send(1L, message(true));
        assertThat(push.got).extracting(NoticeContent::kind).containsExactly("alarm");
        assertThat(push.got.get(0).alarm()).isTrue();
        assertThat(email.got).hasSize(1);
    }

    @Test
    void aReminderUsesTheChannelsItChoseAndIsItsOwnKind() {
        new ReminderNotice(push, email, true, false).send(1L, message(false));
        assertThat(push.got).extracting(NoticeContent::kind).containsExactly("reminder:Revisa el pH");
        assertThat(email.got).isEmpty();
    }

    @Test
    void aFailingChannelDoesNotStopTheOthers() {
        NoticeChannel broken = (userId, content) -> {
            throw new IllegalStateException("push service down");
        };
        new ReminderNotice(broken, email, true, true).send(1L, message(false));
        assertThat(email.got).hasSize(1);
    }

    private static Notifier.Message message(boolean alarm) {
        return new Notifier.Message("Revisa el pH", "Toca medir la piscina.", "/#/measure", "Recordatorio",
            List.of("Toca medir la piscina."), alarm);
    }

    private static final class Recorder implements NoticeChannel {
        final List<NoticeContent> got = new ArrayList<>();

        @Override
        public boolean deliver(Long userId, NoticeContent content) {
            got.add(content);
            return true;
        }
    }
}
