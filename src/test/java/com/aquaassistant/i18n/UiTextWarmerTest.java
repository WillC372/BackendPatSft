package com.aquaassistant.i18n;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquaassistant.intelligence.ai.FallbackAiProvider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UiTextWarmerTest {

    @Test
    void theListOfInterfaceTextsIsBundled() {
        List<String> texts = UiTextWarmer.texts();
        assertThat(texts.size()).isGreaterThan(1500);
        assertThat(texts).contains("Calculadora de dosis", "Nuevo lugar", "Mi agua").doesNotHaveDuplicates();
    }

    @Test
    void onlyWhatIsMissingIsSentAndSpanishIsSkipped() {
        TranslationService translations = mock(TranslationService.class);
        FallbackAiProvider ai = mock(FallbackAiProvider.class);
        when(ai.isAvailable()).thenReturn(true);
        Map<String, String> known = new java.util.HashMap<>();
        UiTextWarmer.texts().forEach(t -> known.put(t, t));
        known.remove("Calculadora de dosis");
        when(translations.dictionary(anyString())).thenReturn(known);
        when(translations.translate(anyString(), any(), eq(TranslationBudget.SERVER))).thenReturn(List.of("Dose calculator"));
        int done = new UiTextWarmer(translations, ai, true, 0).warm();
        assertThat(done).isEqualTo(6);
        verify(translations).translate("en", List.of("Calculadora de dosis"), TranslationBudget.SERVER);
        verify(translations, never()).translate(eq("es"), any(), any());
    }

    @Test
    void withoutAnAiNothingIsTried() {
        TranslationService translations = mock(TranslationService.class);
        FallbackAiProvider ai = mock(FallbackAiProvider.class);
        assertThat(new UiTextWarmer(translations, ai, true, 0).warm()).isZero();
        verify(translations, never()).dictionary(anyString());
    }
}
