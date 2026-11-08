package com.aquasense.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquasense.payment.PaymentService;
import com.aquasense.user.Plan;
import com.aquasense.user.User;
import com.aquasense.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * End-to-end Premium payment against a local server that imitates the PayU query API.
 * Premium is only activated when the queried order is APPROVED and matches the account, amount and currency.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentIntegrationTest {

    private static final Map<String, String> ORDERS = new ConcurrentHashMap<>();
    private static final HttpServer PAYU;
    private static final ObjectMapper JSON = new ObjectMapper();

    static {
        try {
            PAYU = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        PAYU.createContext("/reports-api/4.0/service.cgi", exchange -> {
            JsonNode request = JSON.readTree(exchange.getRequestBody().readAllBytes());
            String reference = request.path("details").path("referenceCode").asText();
            String payload = ORDERS.getOrDefault(reference, "[]");
            byte[] bytes = ("{\"code\":\"SUCCESS\",\"error\":null,\"result\":{\"payload\":" + payload + "}}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        PAYU.start();
    }

    @DynamicPropertySource
    static void payments(DynamicPropertyRegistry registry) {
        registry.add("aquasense.payments.enabled", () -> true);
        registry.add("aquasense.payments.response-url", () -> "http://localhost:5500/#/billing");
        registry.add("aquasense.payments.payu.api-url", () -> "http://127.0.0.1:" + PAYU.getAddress().getPort());
        registry.add("aquasense.payments.payu.test", () -> true);
    }

    @AfterAll
    static void stop() {
        PAYU.stop(0);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private TransactionTemplate tx;

    private static String order(String reference, String state, String value, String currency, String account) {
        return "[{\"id\":9001,\"accountId\":" + account + ",\"status\":\"CAPTURED\",\"referenceCode\":\"" + reference + "\","
            + "\"transactions\":[{\"id\":\"tx-" + reference + "\",\"paymentMethod\":\"VISA\",\"transactionResponse\":{\"state\":\""
            + state + "\"},\"additionalValues\":{\"TX_VALUE\":{\"value\":" + value + ",\"currency\":\"" + currency + "\"}}}]}]";
    }

    @Test
    void premiumIsActivatedOnlyByAVerifiedApprovedOrder() throws Exception {
        String token = register();
        JsonNode plan = json(mvc.perform(get("/api/v1/payments/plan").header("Authorization", "Bearer " + token)).andReturn());
        assertThat(plan.path("plan").asText()).isEqualTo("FREE");
        assertThat(plan.path("price").decimalValue()).isEqualByComparingTo("49900");
        assertThat(plan.path("testMode").asBoolean()).isTrue();
        assertThat(plan.path("testCases").size()).isEqualTo(2);

        // 1. Declined payment: the plan does not change.
        String declined = checkout(token);
        verify(token, declined, "PENDING", "todavía no registra");
        ORDERS.put(declined, order(declined, "DECLINED", "49900.00", "COP", "512321"));
        verify(token, declined, "DECLINED", "rechazó");

        // 2. Payment with a tampered amount: Premium is not activated.
        String tampered = checkout(token);
        ORDERS.put(tampered, order(tampered, "APPROVED", "100.00", "COP", "512321"));
        verify(token, tampered, "ERROR", "no coincide");
        // Order from another account of the shared merchant: not either.
        String foreign = checkout(token);
        ORDERS.put(foreign, order(foreign, "APPROVED", "49900.00", "COP", "512322"));
        verify(token, foreign, "ERROR", "no coincide");
        assertThat(userPlan(token)).isEqualTo("FREE");

        // 3. Approved payment: Premium for 30 days, only once even if it is verified again.
        String approved = checkout(token);
        ORDERS.put(approved, order(approved, "APPROVED", "49900.00", "COP", "512321"));
        JsonNode ok = verify(token, approved, "APPROVED", "Premium activo");
        Instant until = Instant.parse(ok.path("premiumUntil").asText());
        assertThat(Duration.between(Instant.now(), until).toDays()).isBetween(29L, 30L);
        JsonNode again = verify(token, approved, "APPROVED", "Premium activo");
        assertThat(millis(again.path("premiumUntil").asText())).isEqualTo(millis(ok.path("premiumUntil").asText()));
        JsonNode after = json(mvc.perform(get("/api/v1/payments/plan").header("Authorization", "Bearer " + token)).andReturn());
        assertThat(after.path("plan").asText()).isEqualTo("PREMIUM");
        assertThat(millis(after.path("premiumUntil").asText())).isEqualTo(millis(ok.path("premiumUntil").asText()));

        // 4. Renewing before expiry adds 30 days from the current expiry.
        String renewal = checkout(token);
        ORDERS.put(renewal, order(renewal, "APPROVED", "49900.00", "COP", "512321"));
        JsonNode renewed = verify(token, renewal, "APPROVED", "Premium activo");
        assertThat(Duration.between(until, Instant.parse(renewed.path("premiumUntil").asText())).toMinutes()).isBetween(43_199L, 43_200L);

        // 5. Another user cannot see or verify my payments.
        String other = register();
        mvc.perform(post("/api/v1/payments/verify").header("Authorization", "Bearer " + other)
            .contentType(MediaType.APPLICATION_JSON).content("{\"referenceCode\":\"" + approved + "\"}")).andExpect(status().isNotFound());
        JsonNode history = json(mvc.perform(get("/api/v1/payments").header("Authorization", "Bearer " + token)).andReturn());
        assertThat(history.size()).isEqualTo(5);

        // 6. Expiry: after the date passes it goes back to Free.
        String email = json(mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token)).andReturn())
            .path("email").asText();
        tx.executeWithoutResult(s -> {
            User user = users.findByEmailIgnoreCase(email).orElseThrow();
            user.extendPremium(Duration.ofDays(-120), Instant.now());
            users.save(user);
        });
        paymentService.housekeeping();
        assertThat(users.findByEmailIgnoreCase(email).orElseThrow().getPlan()).isEqualTo(Plan.FREE);
    }

    @Test
    void confirmationWebhookRequiresAValidSignatureAndStillQueriesPayu() throws Exception {
        String token = register();
        String reference = checkout(token);
        ORDERS.put(reference, order(reference, "APPROVED", "49900.00", "COP", "512321"));
        String base = "4Vj8eK4rloUd272L48hsrarnUA~508029~" + reference + "~49900.0~COP~4";
        String valid = "merchant_id=508029&reference_sale=" + reference + "&value=49900.00&currency=COP&state_pol=4&sign="
            + com.aquasense.payment.PayuGatewayProbe.md5(base);
        mvc.perform(post("/api/v1/payments/payu/confirmation").contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .content(valid.replace("sign=", "sign=0"))).andExpect(status().isBadRequest());
        assertThat(userPlan(token)).isEqualTo("FREE");
        mvc.perform(post("/api/v1/payments/payu/confirmation").contentType(MediaType.APPLICATION_FORM_URLENCODED).content(valid))
            .andExpect(status().isOk());
        assertThat(userPlan(token)).isEqualTo("PREMIUM");
    }

    @Test
    void adminAssignedPremiumCannotBeBoughtAgain() throws Exception {
        String token = register();
        String email = json(mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token)).andReturn())
            .path("email").asText();
        tx.executeWithoutResult(s -> {
            User user = users.findByEmailIgnoreCase(email).orElseThrow();
            user.changePlan(Plan.PREMIUM);
            users.save(user);
        });
        mvc.perform(post("/api/v1/payments/checkout").header("Authorization", "Bearer " + token)).andExpect(status().isConflict());
    }

    private static Instant millis(String text) {
        return Instant.parse(text).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    }

    private String checkout(String token) throws Exception {
        JsonNode checkout = json(mvc.perform(post("/api/v1/payments/checkout").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn());
        assertThat(checkout.path("actionUrl").asText()).startsWith("https://sandbox.checkout.payulatam.com/");
        JsonNode fields = checkout.path("fields");
        assertThat(fields.path("signature").asText()).hasSize(64);
        assertThat(fields.path("amount").asText()).isEqualTo("49900");
        assertThat(fields.path("responseUrl").asText()).isEqualTo("http://localhost:5500/#/billing");
        return checkout.path("referenceCode").asText();
    }

    private JsonNode verify(String token, String reference, String expectedStatus, String messagePart) throws Exception {
        JsonNode result = json(mvc.perform(post("/api/v1/payments/verify").header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"referenceCode\":\"" + reference + "\"}"))
            .andExpect(status().isOk()).andReturn());
        assertThat(result.path("status").asText()).isEqualTo(expectedStatus);
        assertThat(result.path("message").asText()).contains(messagePart);
        return result;
    }

    private String userPlan(String token) throws Exception {
        return json(mvc.perform(get("/api/v1/payments/plan").header("Authorization", "Bearer " + token)).andReturn())
            .path("plan").asText();
    }

    private String register() throws Exception {
        String email = "p" + UUID.randomUUID().toString().substring(0, 8) + "@aquasense.test";
        return json(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Prueba\",\"email\":\"" + email + "\",\"password\":\"Clave1234\"}")).andReturn())
            .path("token").asText();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
