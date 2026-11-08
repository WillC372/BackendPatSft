package com.aquasense.intelligence.ai;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Request to the AI. If {@code responseSchema} is not null, the provider must return JSON that matches that schema.
 * {@code image} attaches a photo (only providers with vision handle it) and {@code excludedModels} allows asking
 * a model other than the one that already answered for a second opinion. {@code history} holds the previous turns of a
 * conversation (assistant memory) and they are sent before the current message.
 */
public record AiPrompt(Long userId, String purpose, String system, String user, String schemaName,
                       Map<String, Object> responseSchema, AiImage image, Set<String> excludedModels,
                       List<Turn> history) {

    public AiPrompt {
        excludedModels = excludedModels == null ? Set.of() : Set.copyOf(excludedModels);
        history = history == null ? List.of() : List.copyOf(history);
    }

    public AiPrompt(Long userId, String purpose, String system, String user, String schemaName,
                    Map<String, Object> responseSchema, AiImage image, Set<String> excludedModels) {
        this(userId, purpose, system, user, schemaName, responseSchema, image, excludedModels, List.of());
    }

    public AiPrompt(Long userId, String purpose, String system, String user) {
        this(userId, purpose, system, user, null, null, null, Set.of(), List.of());
    }

    public AiPrompt(Long userId, String purpose, String system, String user, String schemaName,
                    Map<String, Object> responseSchema) {
        this(userId, purpose, system, user, schemaName, responseSchema, null, Set.of(), List.of());
    }

    public boolean expectsJson() {
        return responseSchema != null;
    }

    public boolean hasImage() {
        return image != null;
    }

    public AiPrompt excluding(String model) {
        java.util.Set<String> excluded = new java.util.HashSet<>(excludedModels);
        excluded.add(model);
        return new AiPrompt(userId, purpose, system, user, schemaName, responseSchema, image, excluded, history);
    }

    public AiPrompt withHistory(List<Turn> turns) {
        return new AiPrompt(userId, purpose, system, user, schemaName, responseSchema, image, excludedModels, turns);
    }

    /** A previous turn: {@code assistant} tells whether the AI wrote it. */
    public record Turn(boolean assistant, String content) {
    }
}
