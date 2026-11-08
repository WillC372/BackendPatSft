package com.aquasense.intelligence.ai;

/**
 * Observer of the failed attempts of the provider chain, to record failures and latency per provider.
 */
public interface AiCallListener {

    void onFailure(AiPrompt prompt, String provider, String model, long latencyMs, String error);
}
