package com.aquasense.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Adapter de PayU Latam (WebCheckout + API de consultas), según developers-payu-latam.github.io.
 * <ul>
 *   <li>Formulario: firma SHA-256 de {@code ApiKey~merchantId~referenceCode~amount~currency}.</li>
 *   <li>Verificación: {@code ORDER_DETAIL_BY_REFERENCE_CODE} en {@code /reports-api/4.0/service.cgi}. El servidor nunca
 *       activa Premium con lo que diga el navegador: siempre consulta la orden en PayU.</li>
 *   <li>Confirmación (webhook): firma de {@code ApiKey~merchant_id~reference_sale~new_value~currency~state_pol}.</li>
 * </ul>
 */
@Component
public class PayuGateway {

    static final String PROVIDER = "PAYU";
    private static final String USER_AGENT = "AquaSense-AI/1.0";

    private final PaymentProperties.Payu config;
    private final ObjectMapper mapper;
    private final HttpClient http;

    @Autowired
    public PayuGateway(PaymentProperties properties, ObjectMapper mapper) {
        this(properties.payu(), mapper, HttpClient.newBuilder().proxy(ProxySelector.getDefault())
            .connectTimeout(Duration.ofSeconds(10)).build());
    }

    PayuGateway(PaymentProperties.Payu config, ObjectMapper mapper, HttpClient http) {
        this.config = config;
        this.mapper = mapper;
        this.http = http;
    }

    public boolean isTest() {
        return config.test();
    }

    public String checkoutUrl() {
        return config.checkoutUrl();
    }

    /** Campos del formulario WebCheckout, en el orden de la documentación. */
    public Map<String, String> formFields(Payment payment, String description, String buyerEmail, String responseUrl,
                                          String confirmationUrl) {
        String amount = amount(payment.getAmount());
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("merchantId", config.merchantId());
        fields.put("accountId", config.accountId());
        fields.put("description", description);
        fields.put("referenceCode", payment.getReferenceCode());
        fields.put("amount", amount);
        fields.put("tax", "0");
        fields.put("taxReturnBase", "0");
        fields.put("currency", payment.getCurrency());
        fields.put("signature", sha256(config.apiKey() + "~" + config.merchantId() + "~" + payment.getReferenceCode() + "~"
            + amount + "~" + payment.getCurrency()));
        fields.put("algorithmSignature", "SHA256");
        fields.put("test", config.test() ? "1" : "0");
        fields.put("lng", "es");
        fields.put("buyerEmail", buyerEmail);
        if (responseUrl != null && !responseUrl.isBlank()) {
            fields.put("responseUrl", responseUrl);
        }
        if (confirmationUrl != null && !confirmationUrl.isBlank()) {
            fields.put("confirmationUrl", confirmationUrl);
        }
        return fields;
    }

    /** Consulta la orden por referencia. Vacío si PayU aún no tiene una orden con esa referencia. */
    public Optional<OrderResult> findOrder(String referenceCode) {
        ObjectNode body = mapper.createObjectNode();
        body.put("test", config.test());
        body.put("language", "es");
        body.put("command", "ORDER_DETAIL_BY_REFERENCE_CODE");
        body.putObject("merchant").put("apiLogin", config.apiLogin()).put("apiKey", config.apiKey());
        body.putObject("details").put("referenceCode", referenceCode);
        HttpRequest request = HttpRequest.newBuilder(URI.create(config.apiUrl() + "/reports-api/4.0/service.cgi"))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
            .build();
        JsonNode root;
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new GatewayException("PayU respondió " + response.statusCode() + ".");
            }
            root = mapper.readTree(response.body());
        } catch (IOException ex) {
            throw new GatewayException("No se pudo consultar PayU: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new GatewayException("Consulta interrumpida.");
        }
        if (!"SUCCESS".equals(root.path("code").asText())) {
            throw new GatewayException("PayU rechazó la consulta: " + root.path("error").asText("sin detalle"));
        }
        JsonNode payload = root.path("result").path("payload");
        if (!payload.isArray() || payload.isEmpty()) {
            return Optional.empty();
        }
        JsonNode order = null;
        for (JsonNode candidate : payload) {
            if (referenceCode.equals(candidate.path("referenceCode").asText())) {
                order = candidate;
            }
        }
        if (order == null) {
            return Optional.empty();
        }
        JsonNode chosen = null;
        for (JsonNode tx : order.path("transactions")) {
            String state = tx.path("transactionResponse").path("state").asText("");
            if (chosen == null || "APPROVED".equals(state)) {
                chosen = tx;
            }
        }
        JsonNode values = chosen == null ? order.path("additionalValues") : chosen.path("additionalValues");
        JsonNode txValue = values.path("TX_VALUE");
        if (txValue.isMissingNode()) {
            txValue = order.path("additionalValues").path("TX_VALUE");
        }
        String state = chosen == null ? order.path("status").asText("") : chosen.path("transactionResponse").path("state").asText("");
        return Optional.of(new OrderResult(order.path("id").asText(), order.path("accountId").asText(),
            chosen == null ? null : chosen.path("id").asText(null), state,
            txValue.isMissingNode() ? null : txValue.path("value").decimalValue(), txValue.path("currency").asText(null),
            chosen == null ? null : chosen.path("paymentMethod").asText(null)));
    }

    /**
     * Valida la firma de la página de confirmación. Si el segundo decimal del valor es cero se usa un decimal
     * (150.00 → 150.0); si no, dos (150.26). Se acepta MD5 o SHA-256, según el algoritmo con que se firmó el formulario.
     */
    public boolean validConfirmation(Map<String, String> params) {
        String sign = params.getOrDefault("sign", "");
        String value = confirmationValue(params.getOrDefault("value", ""));
        String base = config.apiKey() + "~" + params.getOrDefault("merchant_id", "") + "~" + params.getOrDefault("reference_sale", "")
            + "~" + value + "~" + params.getOrDefault("currency", "") + "~" + params.getOrDefault("state_pol", "");
        return config.merchantId().equals(params.get("merchant_id"))
            && (sign.equalsIgnoreCase(md5(base)) || sign.equalsIgnoreCase(sha256(base)));
    }

    static String confirmationValue(String raw) {
        try {
            BigDecimal value = new BigDecimal(raw).setScale(2, RoundingMode.HALF_UP);
            String two = value.toPlainString();
            return two.endsWith("0") ? value.setScale(1, RoundingMode.HALF_UP).toPlainString() : two;
        } catch (NumberFormatException ex) {
            return raw;
        }
    }

    /** Valor sin ceros sobrantes y con máximo dos decimales (49900, 10000.5). */
    static String amount(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    static String sha256(String text) {
        return digest("SHA-256", text);
    }

    static String md5(String text) {
        return digest("MD5", text);
    }

    private static String digest(String algorithm, String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public boolean belongsToMerchant(OrderResult order) {
        return config.accountId().equals(order.accountId());
    }

    /** Orden consultada en PayU. */
    public record OrderResult(String orderId, String accountId, String transactionId, String state, BigDecimal value,
                              String currency, String paymentMethod) {

        public PaymentStatus status() {
            return switch (state == null ? "" : state) {
                case "APPROVED" -> PaymentStatus.APPROVED;
                case "DECLINED", "REJECTED", "ANTIFRAUD_REJECTED", "PAYMENT_NETWORK_REJECTED", "ENTITY_DECLINED" -> PaymentStatus.DECLINED;
                case "ERROR", "INTERNAL_PAYMENT_PROVIDER_ERROR" -> PaymentStatus.ERROR;
                case "EXPIRED" -> PaymentStatus.EXPIRED;
                default -> PaymentStatus.PENDING;
            };
        }
    }

    public static class GatewayException extends RuntimeException {
        public GatewayException(String message) {
            super(message);
        }
    }
}
