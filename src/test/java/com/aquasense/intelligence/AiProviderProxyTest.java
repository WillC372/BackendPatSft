package com.aquasense.intelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquasense.common.BusinessException;
import com.aquasense.intelligence.ai.AiAnswer;
import com.aquasense.intelligence.ai.AiPrompt;
import com.aquasense.intelligence.ai.AiTextProvider;
import com.aquasense.intelligence.ai.AiUnavailableException;
import com.aquasense.intelligence.ai.AiUsageRepository;
import com.aquasense.support.TestData;
import com.aquasense.user.UserRepository;
import java.lang.reflect.Constructor;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiProviderProxyTest {

    private final AiTextProvider real = mock(AiTextProvider.class);
    private final AiUsageRepository usage = mock(AiUsageRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private com.aquasense.intelligence.ai.AiProviderProxy proxy;
    private final AiPrompt prompt = new AiPrompt(1L, "CHAT", "system", "pregunta");

    @BeforeEach
    void setUp() throws Exception {
        Constructor<com.aquasense.intelligence.ai.AiProviderProxy> constructor =
            com.aquasense.intelligence.ai.AiProviderProxy.class.getDeclaredConstructor(AiTextProvider.class,
                AiUsageRepository.class, UserRepository.class, com.aquasense.config.AppProperties.class,
                com.aquasense.intelligence.ai.AiPricing.class, com.aquasense.intelligence.ai.AiUsageRecorder.class);
        constructor.setAccessible(true);
        var pricing = new com.aquasense.intelligence.ai.AiPricing(new com.aquasense.intelligence.ai.AiPricingProperties("FREE", "nota",
            java.util.List.of(new com.aquasense.intelligence.ai.AiPricingProperties.ModelPrice("openai/gpt-oss-120b", 0.15, 0.60,
                "prueba", "2026-11-07", false))));
        proxy = constructor.newInstance(real, usage, users, TestData.properties("key", 2), pricing,
            new com.aquasense.intelligence.ai.AiUsageRecorder(usage));
        when(users.findById(anyLong())).thenReturn(Optional.empty());
        when(real.complete(any())).thenReturn(new AiAnswer("respuesta", "Groq", "openai/gpt-oss-120b", 100, 50, false));
    }

    @Test
    void blocksCallsWhenProviderIsNotConfigured() {
        when(real.isAvailable()).thenReturn(false);
        assertThatThrownBy(() -> proxy.complete(prompt)).isInstanceOf(AiUnavailableException.class);
        verify(real, times(0)).complete(any());
    }

    @Test
    void enforcesDailyQuotaBeforeCallingProvider() {
        when(real.isAvailable()).thenReturn(true);
        when(usage.countByUserIdAndCachedFalseAndSuccessTrueAndCreatedAtAfter(eq(1L), any())).thenReturn(2L);
        assertThatThrownBy(() -> proxy.complete(prompt)).isInstanceOf(BusinessException.class)
            .hasMessageContaining("límite diario");
        verify(real, times(0)).complete(any());
    }

    @Test
    void repeatedPromptIsServedFromCache() {
        when(real.isAvailable()).thenReturn(true);
        when(usage.countByUserIdAndCachedFalseAndSuccessTrueAndCreatedAtAfter(eq(1L), any())).thenReturn(0L);
        AiAnswer first = proxy.complete(prompt);
        AiAnswer second = proxy.complete(prompt);
        assertThat(first.cached()).isFalse();
        assertThat(second.cached()).isTrue();
        assertThat(second.text()).isEqualTo("respuesta");
        verify(real, times(1)).complete(any());
        verify(usage, times(2)).save(any());
    }

    @Test
    void storesEstimatedCostAndRecordsFailuresWithoutConsumingQuota() {
        when(real.isAvailable()).thenReturn(true);
        proxy.complete(new AiPrompt(1L, "CHAT", "system", "otra pregunta"));
        var saved = org.mockito.ArgumentCaptor.forClass(com.aquasense.intelligence.ai.AiUsage.class);
        verify(usage).save(saved.capture());
        assertThat(saved.getValue().getCostUsd()).isEqualByComparingTo("0.000045");
        assertThat(saved.getValue().getProvider()).isEqualTo("Groq");
        assertThat(saved.getValue().isSuccess()).isTrue();
        assertThat(proxy.pricing().charged(saved.getValue().getCostUsd())).isEqualByComparingTo("0");

        proxy.onFailure(prompt, "Google Gemini", "gemini-3.5-flash", 120, "límite de uso del plan gratuito alcanzado");
        verify(usage, times(2)).save(saved.capture());
        assertThat(saved.getValue().isSuccess()).isFalse();
        assertThat(saved.getValue().getCostUsd()).isEqualByComparingTo("0");
        assertThat(saved.getValue().getError()).contains("límite");
    }
}
