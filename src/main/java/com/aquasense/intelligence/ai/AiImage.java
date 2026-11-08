package com.aquasense.intelligence.ai;

/**
 * Image attached to an AI request (already sanitized: reduced JPEG without metadata).
 */
public record AiImage(String mimeType, byte[] data) {
}
