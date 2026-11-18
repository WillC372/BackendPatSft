package com.aquaassistant.i18n;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquaassistant.intelligence.ai.AiAnswer;
import com.aquaassistant.intelligence.ai.AiPrompt;
import com.aquaassistant.intelligence.ai.AiUnavailableException;
import com.aquaassistant.intelligence.ai.FallbackAiProvider;
import com.aquaassistant.user.mail.OutgoingMail;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TranslationServiceTest {

    private UiTranslationRepository repository;
    private FallbackAiProvider ai;
    private TranslationService service;

    @BeforeEach
    void setUp() {
        repository = mock(UiTranslationRepository.class);
        when(repository.findByLang(any())).thenReturn(List.of());
        ai = mock(FallbackAiProvider.class);
        service = new TranslationService(repository, ai, new ObjectMapper(), new TranslationBudget(1000, 100000));
    }

    private void aiAnswers(String json) {
        when(ai.complete(any(AiPrompt.class))).thenReturn(new AiAnswer(json, "Google Gemini", "test", 10, 10, false));
    }

    @Test
    void translatesOnceAndThenUsesTheStoredTranslation() {
        aiAnswers("{\"t\":[\"Places\",\"Use <0>My water</0> as the first place.\"]}");
        List<String> first = service.translate("en", List.of("Lugares", "Usa <0>Mi agua</0> como primer lugar.", "7.25"), "1.2.3.4");
        assertThat(first).containsExactly("Places", "Use <0>My water</0> as the first place.", "7.25");
        List<String> again = service.translate("en", List.of("Lugares"), "1.2.3.4");
        assertThat(again).containsExactly("Places");
        verify(ai, times(1)).complete(any(AiPrompt.class));
        verify(repository, times(2)).saveAndFlush(any(UiTranslation.class));
    }

    @Test
    void discardsTranslationsThatLoseTheMarkers() {
        aiAnswers("{\"t\":[\"Use My water as the first place.\"]}");
        List<String> out = service.translate("en", List.of("Usa <0>Mi agua</0> como primer lugar."), "1.2.3.4");
        assertThat(out).containsExactly((String) null);
        verify(repository, times(0)).saveAndFlush(any(UiTranslation.class));
    }

    @Test
    void numberMarkersMustSurvive() {
        assertThat(TranslationService.sameMarkers("Cada {0} días", "Every {0} days")).isTrue();
        assertThat(TranslationService.sameMarkers("Cada {0} días", "Every day")).isFalse();
        assertThat(TranslationService.sameMarkers("<0>{0}</0> de {1}", "{1} の <0>{0}</0>")).isTrue();
    }

    @Test
    void serverTextsShareOneTranslationWhateverTheNumbers() {
        aiAnswers("{\"t\":[\"Measurements: {0}. Average: {1}.\"]}");
        assertThat(service.translateAll("en", List.of("Mediciones: 4. Promedio: 7.65.")))
            .containsExactly("Measurements: 4. Average: 7.65.");
        assertThat(service.translateAll("en", List.of("Mediciones: 9. Promedio: 6.10.")))
            .containsExactly("Measurements: 9. Average: 6.10.");
        verify(ai, times(1)).complete(any(AiPrompt.class));
    }

    @Test
    void spanishAndUnknownTextsAreNotSentToTheAi() {
        assertThat(service.translate("es", List.of("Hola"), "x")).containsExactly("Hola");
        verify(ai, times(0)).complete(any(AiPrompt.class));
    }

    @Test
    void whenTheAiFailsTheTextStaysUntranslatedAndIsNotStored() {
        when(ai.complete(any(AiPrompt.class))).thenThrow(new AiUnavailableException("sin cuota"));
        assertThat(service.translate("fr", List.of("Medir"), "x")).containsExactly((String) null);
        assertThat(service.translateOne("fr", "Medir")).isEqualTo("Medir");
    }

    @Test
    void theAiIsToldTheLanguageAndToKeepTheMarkers() {
        aiAnswers("{\"t\":[\"Medir\"]}");
        service.translate("de", List.of("Medir"), "x");
        ArgumentCaptor<AiPrompt> prompt = ArgumentCaptor.forClass(AiPrompt.class);
        verify(ai).complete(prompt.capture());
        assertThat(prompt.getValue().system()).contains("German").contains("<0>");
        assertThat(prompt.getValue().purpose()).isEqualTo("TRANSLATION");
        assertThat(prompt.getValue().userId()).isNull();
    }

    @Test
    void theBudgetLimitsNewTextsPerAddress() {
        TranslationBudget budget = new TranslationBudget(3, 1000);
        assertThat(budget.take("a", 2)).isEqualTo(2);
        assertThat(budget.take("a", 2)).isEqualTo(1);
        assertThat(budget.take("b", 2)).isEqualTo(2);
        assertThat(budget.take(TranslationBudget.SERVER, 50)).isEqualTo(50);
    }

    @Test
    void emailsAreTranslatedKeepingLinksAndHtml() {
        aiAnswers("{\"t\":[\"Reset your password\",\"Open this link:\",\"Reset\"]}");
        var users = mock(com.aquaassistant.user.UserRepository.class);
        when(users.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        MailLocalizer localizer = new MailLocalizer(users, mock(UserLanguage.class), service);
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
        assertThat(Languages.drawableInPdf("ja")).isTrue();
        assertThat(Languages.nativeName("ja")).isEqualTo("日本語");
    }
}
