package com.aquasense.intelligence.ai;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Solicitud a la IA. Si {@code responseSchema} no es nulo, el proveedor debe devolver JSON que cumpla ese esquema.
 * {@code image} adjunta una foto (solo la atienden proveedores con visión) y {@code excludedModels} permite pedir
 * una segunda opinión a un modelo distinto del que ya respondió. {@code history} son los turnos previos de una
 * conversación (memoria del asistente) y se envían antes del mensaje actual.
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

    /** Un turno previo: {@code assistant} indica si lo escribió la IA. */
    public record Turn(boolean assistant, String content) {
    }
}
