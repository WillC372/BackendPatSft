package com.aquasense.intelligence.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Question to the assistant. Without {@code conversationId} a new conversation starts. */
public record ChatRequest(@NotBlank(message = "Escribe una pregunta.") @Size(max = 500, message = "Máximo 500 caracteres.") String question,
                          Long conversationId) {
}
