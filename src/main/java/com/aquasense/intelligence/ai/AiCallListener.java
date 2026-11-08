package com.aquasense.intelligence.ai;

/**
 * Observer de los intentos fallidos de la cadena de proveedores, para registrar fallos y latencia por proveedor.
 */
public interface AiCallListener {

    void onFailure(AiPrompt prompt, String provider, String model, long latencyMs, String error);
}
