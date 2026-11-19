package com.aquaassistant.i18n;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aquaassistant.user.mail.OutgoingMail;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Emails and notices in the person's language: the shared table first, the AI service only for new texts. */
class TranslationClientTest {

    private final UiTranslationRepository repository = mock(UiTranslationRepository.class);
    private final List<String> asked = new CopyOnWriteArrayList<>();
    private final List<String> keys = new CopyOnWriteArrayList<>();
    private HttpServer ai;
    private volatile String reply = "{\"lang\":\"en\",\"translations\":[]}";

    @BeforeEach
    void startAi() throws Exception {
        ai = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ai.createContext("/api/v1/internal/translate", exchange -> {
            asked.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            keys.add(exchange.getRequestHeaders().getFirst("X-Internal-Key"));
            byte[] out = reply.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        ai.start();
    }

    @AfterEach
    void stopAi() {
        ai.stop(0);
    }

    private TranslationClient client(String url) {
        return new TranslationClient(repository, new ObjectMapper(), url, "test-internal-key-0123456789");
    }

    private String url() {
        return "http://127.0.0.1:" + ai.getAddress().getPort();
    }

    @Test
    void knownTextsComeFromTheSharedTableWithTheirNumbers() {
        when(repository.findAllById(any())).thenReturn(List.of(
            new UiTranslation(TranslationClient.key("en", "Mediciones: {0}"), "en", "Mediciones: {0}", "Measurements: {0}")));
        List<String> out = client(url()).translateAll("en", List.of("Mediciones: 4"));
        assertThat(out).containsExactly("Measurements: 4");
        assertThat(asked).isEmpty();
    }

    @Test
    void newTextsAreAskedToTheAiServiceWithTheInternalKey() {
        when(repository.findAllById(any())).thenReturn(List.of());
        reply = "{\"lang\":\"fr\",\"translations\":[\"Ton eau est en dehors de la plage\"]}";
        List<String> out = client(url()).translateAll("fr", List.of("Tu agua está fuera del rango", "7.4"));
        assertThat(out).containsExactly("Ton eau est en dehors de la plage", "7.4");
        assertThat(asked).hasSize(1);
        assertThat(asked.get(0)).contains("\"lang\":\"fr\"").contains("Tu agua está fuera del rango").doesNotContain("7.4");
        assertThat(keys).containsExactly("test-internal-key-0123456789");
    }

    @Test
    void withoutTheAiServiceTheTextStaysInSpanish() {
        when(repository.findAllById(any())).thenReturn(List.of());
        assertThat(client("http://127.0.0.1:9").translateAll("de", List.of("Hola"))).containsExactly("Hola");
        assertThat(client(url()).translateAll("es", List.of("Hola"))).containsExactly("Hola");
        assertThat(asked).isEmpty();
    }

    @Test
    void emailsAreTranslatedKeepingLinksAndHtml() {
        when(repository.findAllById(any())).thenReturn(List.of());
        reply = "{\"lang\":\"en\",\"translations\":[\"Reset your password\",\"Open this link:\",\"Reset\"]}";
        var users = mock(com.aquaassistant.user.UserRepository.class);
        when(users.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        MailLocalizer localizer = new MailLocalizer(users, mock(UserLanguage.class), client(url()));
        OutgoingMail mail = new OutgoingMail("a@b.co", "Restablecer contraseña",
            "Abre este enlace:\nhttps://x.co/r?t=1", "<html lang=\"es\"><p>Restablecer</p><a href=\"https://x.co/r?t=1\">https://x.co/r?t=1</a></html>");
        OutgoingMail out = localizer.translate(mail, "en");
        assertThat(out.subject()).isEqualTo("Reset your password");
        assertThat(out.text()).isEqualTo("Open this link:\nhttps://x.co/r?t=1");
        assertThat(out.html()).contains("<p>Reset</p>").contains("href=\"https://x.co/r?t=1\"").contains("lang=\"en\"");
    }

    @Test
    void languagesAreNormalizedAndDescribed() {
        assertThat(Languages.normalize("ZH-hans")).isEqualTo("zh-Hans");
        assertThat(Languages.normalize("xx")).isNull();
        assertThat(Languages.englishName("zh-Hans")).isEqualTo("Simplified Chinese");
        assertThat(Languages.nativeName("fr")).isEqualTo("Français");
        assertThat(Languages.codes()).containsExactly("es", "en", "fr", "de", "it", "zh-Hans", "ja");
        assertThat(Languages.normalize("pt-BR")).isNull();
    }
}
