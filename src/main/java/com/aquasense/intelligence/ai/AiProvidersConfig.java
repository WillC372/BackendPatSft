package com.aquasense.intelligence.ai;

import com.aquasense.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the provider chain from the configuration: first the OpenAI-compatible ones
 * in the declared order (Gemini, Groq...) and Claude last if it has a key.
 */
@Configuration
public class AiProvidersConfig {

    @Bean
    public FallbackAiProvider fallbackAiProvider(AppProperties properties, ObjectMapper mapper) {
        List<AiTextProvider> chain = new ArrayList<>();
        properties.ai().providers().forEach(provider -> chain.add(new OpenAiCompatibleProvider(provider, mapper)));
        chain.add(new ClaudeTextProvider(properties));
        return new FallbackAiProvider(chain, mapper);
    }
}
