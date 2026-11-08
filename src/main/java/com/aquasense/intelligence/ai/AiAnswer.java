package com.aquasense.intelligence.ai;

public record AiAnswer(String text, String provider, String model, long inputTokens, long outputTokens, boolean cached) {

    public AiAnswer asCached() {
        return new AiAnswer(text, provider, model, 0, 0, true);
    }
}
