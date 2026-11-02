package com.aquasense.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FreemiumIntegrationTest {

    private static final String POOL = "00000000-0000-0000-0000-000000000002";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("delete from users");
    }

    private JsonNode register(String email) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(
                "{\"displayName\":\"U\",\"email\":\"" + email
                        + "\",\"password\":\"Clave1234\",\"confirmPassword\":\"Clave1234\"}"))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"" + email + "\",\"password\":\"Clave1234\"}"))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + mapper.readTree(body).get("accessToken").asText();
    }

    private String token(String email) throws Exception {
        register(email);
        return login(email);
    }

    private String device(String t, String id) throws Exception {
        String body = mvc.perform(post("/api/v1/devices").header("Authorization", t)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"M\",\"externalId\":\"" + id + "\",\"connectionType\":\"USB_SERIAL\"}"))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("id").asText();
    }

    private void seedDecline(String t, String dev) throws Exception {
        Instant base = Instant.now().minus(5, ChronoUnit.DAYS);
        for (int i = 0; i < 10; i++) {
            double ph = 7.8 - 0.05 * i + (i % 2 == 0 ? 0.01 : -0.01);
            mvc.perform(post("/api/v1/measurements").header("Authorization", t)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"deviceId\":\"" + dev + "\",\"profileId\":\"" + POOL + "\",\"ph\":" + ph
                            + ",\"measuredAt\":\"" + base.plus(i * 12, ChronoUnit.HOURS) + "\"}"))
                    .andExpect(status().isCreated());
        }
    }

    private JsonNode analyze(String t) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/v1/analysis").header("Authorization", t)
                .contentType(MediaType.APPLICATION_JSON).content("{\"profileId\":\"" + POOL + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String promoteAdmin(String email) throws Exception {
        jdbc.update("insert into user_roles (user_id, role) select id, 'ADMIN' from users where lower(email) = ?",
                email);
        return login(email);
    }

    @Test
    void freePlanIsTheDefaultAndExposesLimitsAndUsage() throws Exception {
        String t = token("a@example.com");
        mvc.perform(get("/api/v1/subscriptions/me").header("Authorization", t))
                .andExpect(status().isOk()).andExpect(jsonPath("$.plan").value("FREE"))
                .andExpect(jsonPath("$.limits.maxDevices").value(2))
                .andExpect(jsonPath("$.limits.forecastEnabled").value(false))
                .andExpect(jsonPath("$.usage.devices").value(0))
                .andExpect(jsonPath("$.onlinePaymentsAvailable").value(false));
    }

    @Test
    void deviceAndCustomProfileLimitsAreEnforcedByTheServer() throws Exception {
        String t = token("a@example.com");
        device(t, "d1");
        device(t, "d2");
        mvc.perform(post("/api/v1/devices").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"M\",\"externalId\":\"d3\",\"connectionType\":\"USB_SERIAL\"}"))
                .andExpect(status().isPaymentRequired()).andExpect(jsonPath("$.code").value("PLAN_LIMIT_REACHED"));
        String body = "{\"name\":\"P\",\"description\":\"d\",\"optimalMin\":6.5,\"optimalMax\":7.5,"
                + "\"cautionMin\":6.0,\"cautionMax\":8.0}";
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/profiles").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
                    .content(body)).andExpect(status().isCreated());
        }
        mvc.perform(post("/api/v1/profiles").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isPaymentRequired());
        mvc.perform(post("/api/v1/profiles/" + POOL + "/duplicate").header("Authorization", t)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}"))
                .andExpect(status().isPaymentRequired());
    }

    @Test
    void freeAnalysisHasNoForecastAndIsLimitedPerDay() throws Exception {
        String t = token("a@example.com");
        seedDecline(t, device(t, "d1"));
        JsonNode first = analyze(t);
        assertThat(first.at("/report/sufficientData").asBoolean()).isTrue();
        assertThat(first.at("/report/forecast/available").asBoolean()).isFalse();
        assertThat(first.at("/report/forecast/reason").asText()).contains("Premium");
        assertThat(first.at("/report/limitations").toString()).contains("Plan gratuito");
        analyze(t);
        analyze(t);
        mvc.perform(post("/api/v1/analysis").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"profileId\":\"" + POOL + "\"}"))
                .andExpect(status().isPaymentRequired()).andExpect(jsonPath("$.code").value("PLAN_LIMIT_REACHED"));
    }

    @Test
    void onlyAdminsCanAssignPremiumAndPremiumUnlocksForecastAndLimits() throws Exception {
        JsonNode user = register("user@example.com");
        String userToken = login("user@example.com");
        String userId = user.get("id").asText();
        seedDecline(userToken, device(userToken, "d1"));

        mvc.perform(put("/api/v1/subscriptions/" + userId).header("Authorization", userToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"plan\":\"PREMIUM\",\"days\":30}"))
                .andExpect(status().isForbidden());

        register("admin@example.com");
        String adminToken = promoteAdmin("admin@example.com");
        mvc.perform(put("/api/v1/subscriptions/" + userId).header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"plan\":\"PREMIUM\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DURATION"));
        mvc.perform(put("/api/v1/subscriptions/" + userId).header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"plan\":\"PREMIUM\",\"days\":30}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.plan").value("PREMIUM"))
                .andExpect(jsonPath("$.limits.maxDevices").value(10));

        JsonNode premium = analyze(userToken);
        assertThat(premium.at("/report/forecast/available").asBoolean()).isTrue();
        device(userToken, "d2");
        device(userToken, "d3");
        mvc.perform(get("/api/v1/subscriptions/me").header("Authorization", userToken))
                .andExpect(jsonPath("$.usage.devices").value(3));
    }

    @Test
    void expiredPremiumFallsBackToFree() throws Exception {
        JsonNode user = register("user@example.com");
        String userToken = login("user@example.com");
        register("admin@example.com");
        String adminToken = promoteAdmin("admin@example.com");
        mvc.perform(put("/api/v1/subscriptions/" + user.get("id").asText()).header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"plan\":\"PREMIUM\",\"days\":1}"))
                .andExpect(status().isOk());
        jdbc.update("update subscriptions set current_period_end = now() - interval '1 hour'");
        mvc.perform(get("/api/v1/subscriptions/me").header("Authorization", userToken))
                .andExpect(jsonPath("$.plan").value("FREE"));
    }

    @Test
    void usersAreIsolatedInLimitCounting() throws Exception {
        String ana = token("ana@example.com");
        String luis = token("luis@example.com");
        device(ana, "a1");
        device(ana, "a2");
        mvc.perform(post("/api/v1/devices").header("Authorization", luis).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"M\",\"externalId\":\"l1\",\"connectionType\":\"USB_SERIAL\"}"))
                .andExpect(status().isCreated());
    }
}
