package com.aquasense.intelligence.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Pregunta al asistente. Sin {@code conversationId} se inicia una conversación nueva. */
public record ChatRequest(@NotBlank(message = "Escribe una pregunta.") @Size(max = 500, message = "Máximo 500 caracteres.") String question,
                          Long conversationId) {
}
