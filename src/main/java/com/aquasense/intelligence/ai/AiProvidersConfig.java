package com.aquasense.intelligence.ai;

import com.aquasense.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Arma la cadena de proveedores a partir de la configuración: primero los compatibles con OpenAI
 * en el orden declarado (Gemini, Groq...) y al final Claude si tiene clave.
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
