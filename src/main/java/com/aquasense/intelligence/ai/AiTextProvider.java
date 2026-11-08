package com.aquasense.intelligence.ai;

/**
 * Abstraccion del proveedor de IA generativa. Cambiar de proveedor solo requiere otra implementacion.
 */
public interface AiTextProvider {

    boolean isAvailable();

    String providerName();

    String modelName();

    default boolean supportsVision() {
        return false;
    }

    default boolean supportsTools() {
        return false;
    }

    /**
     * Un turno de conversación con herramientas (formato de OpenAI). Devuelve el mensaje del asistente tal como
     * llegó, para reenviarlo sin cambios en el siguiente turno (Gemini exige conservar su firma de razonamiento).
     */
    default ToolTurn chatWithTools(com.fasterxml.jackson.databind.node.ArrayNode messages,
                                   com.fasterxml.jackson.databind.node.ArrayNode tools) {
        throw new AiUnavailableException(providerName() + " no admite herramientas.");
    }

    record ToolTurn(com.fasterxml.jackson.databind.JsonNode assistantMessage, String model, long inputTokens,
                    long outputTokens) {
    }

    AiAnswer complete(AiPrompt prompt);
}
