package com.aquasense.payment;

/** Test access to the signature computation of {@link PayuGateway}. */
public final class PayuGatewayProbe {

    private PayuGatewayProbe() {
    }

    public static String md5(String text) {
        return PayuGateway.md5(text);
    }
}
