package com.aquasense.payment;

/** Acceso de prueba al cálculo de firmas de {@link PayuGateway}. */
public final class PayuGatewayProbe {

    private PayuGatewayProbe() {
    }

    public static String md5(String text) {
        return PayuGateway.md5(text);
    }
}
