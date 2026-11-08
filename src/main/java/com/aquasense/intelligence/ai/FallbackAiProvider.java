package com.aquasense.intelligence.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chain of Responsibility sobre proveedores de IA: intenta cada proveedor configurado en orden
 * (por ejemplo Gemini, Gemini Lite, Groq, Claude). Si uno esta saturado, sin cuota o devuelve un JSON
 * inválido, pasa al siguiente. Si todos fallan, lanza una excepcion y el sistema usa sus reglas propias.
 */
public class FallbackAiProvider implements AiTextProvider {

    private static final Logger LOG = LoggerFactory.getLogger(FallbackAiProvider.class);

    private final List<AiTextProvider> providers;
    private volatile AiCallListener listener;
    private final ObjectMapper mapper;

    public FallbackAiProvider(List<AiTextProvider> providers, ObjectMapper mapper) {
        this.providers = List.copyOf(providers);
        this.mapper = mapper;
    }

    @Override
    public boolean isAvailable() {
        return providers.stream().anyMatch(AiTextProvider::isAvailable);
    }

    @Override
    public String providerName() {
        return available().stream().map(AiTextProvider::providerName).distinct().collect(Collectors.joining(" > "));
    }

    @Override
    public String modelName() {
        return available().stream().map(AiTextProvider::modelName).collect(Collectors.joining(" > "));
    }

    public void setListener(AiCallListener listener) {
        this.listener = listener;
    }

    private void notifyFailure(AiPrompt prompt, AiTextProvider provider, long start, String error) {
        AiCallListener current = listener;
        if (current != null) {
            try {
                current.onFailure(prompt, provider.providerName(), provider.modelName(), System.currentTimeMillis() - start, error);
            } catch (RuntimeException ex) {
                LOG.warn("Could not record AI failure: {}", ex.getMessage());
            }
        }
    }

    public List<AiTextProvider> chain() {
        return providers;
    }

    @Override
    public AiAnswer complete(AiPrompt prompt) {
        List<String> failures = new ArrayList<>();
        List<AiTextProvider> candidates = available().stream()
            .filter(provider -> !prompt.hasImage() || provider.supportsVision())
            .filter(provider -> !prompt.excludedModels().contains(provider.modelName()))
            .toList();
        if (candidates.isEmpty()) {
            throw new AiUnavailableException(prompt.hasImage()
                ? "Ningún proveedor configurado puede analizar imágenes." : "No hay proveedores de IA disponibles para esta solicitud.");
        }
        for (AiTextProvider provider : candidates) {
            long start = System.currentTimeMillis();
            try {
                AiAnswer answer = provider.complete(prompt);
                if (prompt.expectsJson() && !isJsonObject(answer.text())) {
                    failures.add(provider.providerName() + " (" + provider.modelName() + "): JSON inválido");
                    notifyFailure(prompt, provider, start, "JSON inválido");
                    continue;
                }
                return answer;
            } catch (AiUnavailableException ex) {
                LOG.warn("AI provider {} ({}) failed: {}", provider.providerName(), provider.modelName(), ex.getMessage());
                failures.add(ex.getMessage());
                notifyFailure(prompt, provider, start, ex.getMessage());
            }
        }
        if (failures.isEmpty()) {
            throw new AiUnavailableException("No hay proveedores de IA configurados en el servidor.");
        }
        throw new AiUnavailableException("Ningún proveedor de IA respondió: " + String.join(" | ", failures));
    }

    private List<AiTextProvider> available() {
        return providers.stream().filter(AiTextProvider::isAvailable).toList();
    }

    private boolean isJsonObject(String text) {
        try {
            return mapper.readTree(text).isObject();
        } catch (Exception ex) {
            return false;
        }
    }
}
