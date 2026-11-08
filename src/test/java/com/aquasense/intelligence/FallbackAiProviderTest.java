package com.aquasense.intelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquasense.intelligence.ai.AiAnswer;
import com.aquasense.intelligence.ai.AiPrompt;
import com.aquasense.intelligence.ai.AiTextProvider;
import com.aquasense.intelligence.ai.AiUnavailableException;
import com.aquasense.intelligence.ai.FallbackAiProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FallbackAiProviderTest {

    private final List<String> calls = new ArrayList<>();
    private final AiPrompt jsonPrompt = new AiPrompt(1L, "DIAGNOSIS", "s", "u", "x", Map.of("type", "object"));

    private AiTextProvider provider(String name, boolean configured, String reply) {
        return new AiTextProvider() {
            public boolean isAvailable() { return configured; }
            public String providerName() { return name; }
            public String modelName() { return name + "-model"; }
            public AiAnswer complete(AiPrompt prompt) {
                calls.add(name);
                if (reply == null) {
                    throw new AiUnavailableException(name + " saturado (503)");
                }
                return new AiAnswer(reply, name, name + "-model", 1, 1, false);
            }
        };
    }

    @Test
    void fallsBackToNextProviderWhenOneIsOverloaded() {
        var chain = new FallbackAiProvider(List.of(provider("Gemini", true, null), provider("Groq", true, "{\"ok\":1}")), new ObjectMapper());
        AiAnswer answer = chain.complete(jsonPrompt);
        assertThat(answer.provider()).isEqualTo("Groq");
        assertThat(calls).containsExactly("Gemini", "Groq");
    }

    @Test
    void skipsProvidersThatReturnInvalidJsonOrAreNotConfigured() {
        var chain = new FallbackAiProvider(List.of(provider("SinClave", false, "{}"), provider("Gemini", true, "no es json"),
            provider("Groq", true, "{\"ok\":1}")), new ObjectMapper());
        assertThat(chain.complete(jsonPrompt).provider()).isEqualTo("Groq");
        assertThat(calls).containsExactly("Gemini", "Groq");
        assertThat(chain.providerName()).isEqualTo("Gemini > Groq");
    }

    @Test
    void reportsEveryFailureWhenAllProvidersFail() {
        var chain = new FallbackAiProvider(List.of(provider("Gemini", true, null), provider("Groq", true, null)), new ObjectMapper());
        assertThatThrownBy(() -> chain.complete(jsonPrompt)).isInstanceOf(AiUnavailableException.class)
            .hasMessageContaining("Gemini saturado").hasMessageContaining("Groq saturado");
    }
}
