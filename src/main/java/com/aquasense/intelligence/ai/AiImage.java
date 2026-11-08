package com.aquasense.intelligence.ai;

/**
 * Imagen adjunta a una solicitud de IA (ya saneada: JPEG reducido y sin metadatos).
 */
public record AiImage(String mimeType, byte[] data) {
}
