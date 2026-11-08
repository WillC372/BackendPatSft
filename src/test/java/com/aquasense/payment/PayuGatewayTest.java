package com.aquasense.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PayuGatewayTest {

    private final PayuGateway gateway = new PayuGateway(new PaymentProperties(true, null, null, 0, 0, null, null, null),
        new ObjectMapper());

    @Test
    void signaturesMatchTheExamplesInPayuDocumentation() {
        String base = "4Vj8eK4rloUd272L48hsrarnUA~508029~TestPayU~20000~COP";
        assertThat(PayuGateway.md5(base)).isEqualTo("7ee7cf808ce6a39b17481c54f2c57acc");
        assertThat(PayuGateway.sha256(base)).isEqualTo("af3899a22336b79db46006491d15813158826f944599bf3bf601e2327f898022");
    }

    @Test
    void formIsSignedWithSha256AndUsesTheColombianTestAccount() {
        Payment payment = new Payment(1L, "PAYU", "TestPayU", new BigDecimal("20000"), "COP", 30, true);
        Map<String, String> fields = gateway.formFields(payment, "Prueba", "ana@correo.com", "http://localhost:5500/#/billing", null);
        assertThat(fields).containsEntry("merchantId", "508029").containsEntry("accountId", "512321")
            .containsEntry("amount", "20000").containsEntry("currency", "COP").containsEntry("algorithmSignature", "SHA256")
            .containsEntry("test", "1").containsEntry("signature", "af3899a22336b79db46006491d15813158826f944599bf3bf601e2327f898022")
            .doesNotContainKey("confirmationUrl");
        assertThat(PayuGateway.amount(new BigDecimal("49900.00"))).isEqualTo("49900");
        assertThat(PayuGateway.amount(new BigDecimal("10000.50"))).isEqualTo("10000.5");
    }

    @Test
    void confirmationValueAndSignatureFollowTheDocumentedRules() {
        assertThat(PayuGateway.confirmationValue("150.00")).isEqualTo("150.0");
        assertThat(PayuGateway.confirmationValue("150.26")).isEqualTo("150.26");
        assertThat(PayuGateway.confirmationValue("150.20")).isEqualTo("150.2");
        // Ejemplo "con dos decimales" de la documentación de PayU (su hash coincide con la cadena documentada).
        Map<String, String> example = Map.of("merchant_id", "508029", "reference_sale", "TestPayU05", "value", "150.26",
            "currency", "USD", "state_pol", "4", "sign", "1d95778a651e11a0ab93c2169a519cd6");
        assertThat(gateway.validConfirmation(example)).isTrue();
        // Ejemplo "con un decimal": la cadena documentada es ...~150.0~USD~6. El hash impreso en la documentación
        // (b607a2c2…) no corresponde a esa cadena, así que se comprueba con el MD5 real de la cadena documentada.
        Map<String, String> oneDecimal = Map.of("merchant_id", "508029", "reference_sale", "TestPayU04", "value", "150.00",
            "currency", "USD", "state_pol", "6", "sign", PayuGateway.md5("4Vj8eK4rloUd272L48hsrarnUA~508029~TestPayU04~150.0~USD~6"));
        assertThat(gateway.validConfirmation(oneDecimal)).isTrue();
        Map<String, String> tampered = new java.util.HashMap<>(example);
        tampered.put("state_pol", "6");
        assertThat(gateway.validConfirmation(tampered)).isFalse();
        tampered = new java.util.HashMap<>(example);
        tampered.put("merchant_id", "999999");
        assertThat(gateway.validConfirmation(tampered)).isFalse();
    }
}
