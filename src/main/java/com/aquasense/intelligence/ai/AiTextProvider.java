package com.aquasense.intelligence.ai;

/**
 * Abstraction of the generative AI provider. Switching providers only requires another implementation.
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
     * One conversation turn with tools (OpenAI format). It returns the assistant message as it
     * arrived, to send it back unchanged in the next turn (Gemini requires keeping its thought signature).
     */
    default ToolTurn chatWithTools(com.fasterxml.jackson.databind.node.ArrayNode messages,
                                   com.fasterxml.jackson.databind.node.ArrayNode tools) {
        throw new AiUnavailableException(providerName() + " no admite herramientas.");
    }

    record ToolTurn(com.fasterxml.jackson.databind.JsonNode assistantMessage, String model, long inputTokens,
                    long outputTokens) {
    }

    AiAnswer complete(AiPrompt prompt);

    /** Model that answers in a few seconds; it goes first for interactive requests. */
    default boolean isFast() {
        return false;
    }

    /**
     * Same as {@link #complete(AiPrompt)} but hands each fragment of the answer to {@code onDelta} as it is generated.
     * Providers without streaming deliver the whole text at once.
     */
    default AiAnswer stream(AiPrompt prompt, java.util.function.Consumer<String> onDelta) {
        AiAnswer answer = complete(prompt);
        onDelta.accept(answer.text());
        return answer;
    }
}
