package com.aquasense.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquasense.config.AppProperties;
import com.aquasense.support.TestData;
import com.aquasense.user.mail.MailChannel;
import com.aquasense.user.mail.MailDeliveryException;
import com.aquasense.user.mail.MailDeliveryProperties;
import com.aquasense.user.mail.OutgoingMail;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class MailServiceTest {

    private static final String TOKEN = "token-secreto-de-prueba";

    static final class FakeChannel implements MailChannel {
        final String id;
        final boolean configured;
        final boolean fail;
        final List<OutgoingMail> sent = new ArrayList<>();

        FakeChannel(String id, boolean configured, boolean fail) {
            this.id = id;
            this.configured = configured;
            this.fail = fail;
        }

        @Override public String id() { return id; }
        @Override public String label() { return id.toUpperCase(); }
        @Override public boolean isConfigured() { return configured; }

        @Override
        public void send(OutgoingMail mail, String fromEmail, String fromName) {
            if (fail) {
                throw new MailDeliveryException("Brevo rechazó el envío (400): sender not valid");
            }
            sent.add(mail);
        }
    }

    private static MailService service(String provider, boolean logLinks, MailChannel... channels) {
        AppProperties base = TestData.properties("", 10);
        AppProperties props = new AppProperties(base.security(), base.cors(),
            new AppProperties.Mail("no-reply@aquasense.test", "http://localhost:5500/#/reset-password", logLinks, null), base.ai(),
            base.device());
        return new MailService(List.of(channels), props, new MailDeliveryProperties(provider, "AquaSense AI", null, null));
    }

    @Test
    void productionWithoutChannelDisablesRecoveryAndNeverLogsTheLink(CapturedOutput output) {
        MailService mail = service("auto", false, new FakeChannel("brevo", false, false), new FakeChannel("smtp", false, false));
        assertThat(mail.isPasswordRecoveryAvailable()).isFalse();
        assertThat(mail.sendPasswordReset("ana@aquasense.test", TOKEN)).isFalse();
        assertThat(output.getAll()).doesNotContain(TOKEN).contains("password recovery by email is disabled");
    }

    @Test
    void developmentModeWritesTheLinkAndWarnsAtStartup(CapturedOutput output) {
        MailService mail = service("auto", true, new FakeChannel("brevo", false, false));
        assertThat(mail.isPasswordRecoveryAvailable()).isTrue();
        assertThat(mail.isDevelopmentLogMode()).isTrue();
        assertThat(mail.sendPasswordReset("ana@aquasense.test", TOKEN)).isFalse();
        assertThat(output.getAll()).contains("DEVELOPMENT MODE").contains("reset-password?token=" + TOKEN);
    }

    @Test
    void autoPrefersBrevoAndEmailsTheLinkEvenWithTheDevelopmentFlagOn(CapturedOutput output) {
        FakeChannel brevo = new FakeChannel("brevo", true, false);
        FakeChannel smtp = new FakeChannel("smtp", true, false);
        MailService mail = service("auto", true, smtp, brevo);
        assertThat(mail.status().channel()).isEqualTo("BREVO");
        assertThat(mail.isDevelopmentLogMode()).isFalse();
        assertThat(mail.sendPasswordReset("ana@aquasense.test", TOKEN)).isTrue();
        assertThat(brevo.sent).hasSize(1);
        assertThat(smtp.sent).isEmpty();
        OutgoingMail sent = brevo.sent.get(0);
        assertThat(sent.to()).isEqualTo("ana@aquasense.test");
        assertThat(sent.text()).contains("reset-password?token=" + TOKEN);
        assertThat(sent.html()).contains("href=\"http://localhost:5500/#/reset-password?token=" + TOKEN + "\"");
        assertThat(output.getAll()).doesNotContain(TOKEN);
    }

    @Test
    void explicitProviderIsRespectedAndFailuresNeverLeakTheLink(CapturedOutput output) {
        FakeChannel brevo = new FakeChannel("brevo", true, true);
        FakeChannel smtp = new FakeChannel("smtp", true, false);
        assertThat(service("smtp", false, brevo, smtp).status().channel()).isEqualTo("SMTP");
        assertThat(service("none", true, brevo, smtp).isDevelopmentLogMode()).isTrue();
        MailService failing = service("brevo", false, brevo, smtp);
        assertThat(failing.sendPasswordReset("ana@aquasense.test", TOKEN)).isFalse();
        assertThat(output.getAll()).contains("sender not valid").doesNotContain(TOKEN);
    }
}
