package com.aquasense.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
class ReportApiIntegrationTest {

    private static final String POOL = "00000000-0000-0000-0000-000000000002";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("delete from users");
    }

    private JsonNode register(String email, String name) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"" + name + "\",\"email\":\"" + email
                        + "\",\"password\":\"Clave1234\",\"confirmPassword\":\"Clave1234\"}"))
                .andReturn().getResponse().getContentAsString());
    }

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"" + email + "\",\"password\":\"Clave1234\"}"))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + mapper.readTree(body).get("accessToken").asText();
    }

    private String seededUser(String email, String name) throws Exception {
        register(email, name);
        String t = login(email);
        String dev = mapper.readTree(mvc.perform(post("/api/v1/devices").header("Authorization", t)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"M\",\"externalId\":\"d1\",\"connectionType\":\"SIMULATOR\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        Instant base = Instant.now().minus(4, ChronoUnit.DAYS);
        for (int i = 0; i < 9; i++) {
            mvc.perform(post("/api/v1/measurements").header("Authorization", t)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"deviceId\":\"" + dev + "\",\"profileId\":\"" + POOL + "\",\"ph\":"
                            + (7.6 - 0.05 * i) + ",\"measuredAt\":\"" + base.plus(i * 10, ChronoUnit.HOURS) + "\"}"))
                    .andExpect(status().isCreated());
        }
        return t;
    }

    private JsonNode generate(String t, String body, int expected) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/v1/reports").header("Authorization", t)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void freeUserGetsMarkdownReportWithRealDataAndNoForecast() throws Exception {
        String t = seededUser("a@example.com", "Ana Pérez");
        JsonNode meta = generate(t, "{\"profileId\":\"" + POOL + "\"}", 201);
        assertThat(meta.get("format").asText()).isEqualTo("MARKDOWN");
        assertThat(meta.get("measurementCount").asInt()).isEqualTo(9);
        String content = mvc.perform(get("/api/v1/reports/" + meta.get("id").asText() + "/content")
                .header("Authorization", t)).andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(content).contains("Ana Pérez").contains("a@example.com").contains("SIMULACIÓN")
                .contains("Tendencia").contains("no demuestra").contains("Premium");
        mvc.perform(get("/api/v1/reports/" + meta.get("id").asText() + "/content?download=true")
                .header("Authorization", t)).andExpect(header().string("Content-Disposition",
                org.hamcrest.Matchers.containsString("attachment")));
    }

    @Test
    void freeUserCannotRequestHtmlChartOrMoreThanMonthlyQuota() throws Exception {
        String t = seededUser("a@example.com", "Ana");
        generate(t, "{\"profileId\":\"" + POOL + "\",\"format\":\"HTML\"}", 402);
        generate(t, "{\"profileId\":\"" + POOL + "\",\"includeChart\":true}", 402);
        for (int i = 0; i < 3; i++) {
            generate(t, "{\"profileId\":\"" + POOL + "\"}", 201);
        }
        JsonNode denied = generate(t, "{\"profileId\":\"" + POOL + "\"}", 402);
        assertThat(denied.get("code").asText()).isEqualTo("PLAN_LIMIT_REACHED");
        mvc.perform(get("/api/v1/subscriptions/me").header("Authorization", t))
                .andExpect(jsonPath("$.usage.reportsThisMonth").value(3));
    }

    @Test
    void premiumUserGetsHtmlReportWithChartAndForecast() throws Exception {
        String t = seededUser("a@example.com", "Ana <script>");
        String userId = mapper.readTree(mvc.perform(get("/api/v1/users/me").header("Authorization", t))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        register("admin@example.com", "Admin");
        jdbc.update("insert into user_roles (user_id, role) select id, 'ADMIN' from users where email = ?",
                "admin@example.com");
        String admin = login("admin@example.com");
        mvc.perform(put("/api/v1/subscriptions/" + userId).header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"plan\":\"PREMIUM\",\"days\":30}"))
                .andExpect(status().isOk());

        JsonNode meta = generate(t, "{\"profileId\":\"" + POOL + "\",\"format\":\"HTML\",\"includeChart\":true}",
                201);
        String html = mvc.perform(get("/api/v1/reports/" + meta.get("id").asText() + "/content")
                .header("Authorization", t)).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/html")))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(html).contains("<svg").contains("<polyline").contains("intervalo 95")
                .doesNotContain("<script>").contains("Ana &lt;script&gt;");
    }

    @Test
    void reportsAreIsolatedBetweenUsers() throws Exception {
        String ana = seededUser("ana@example.com", "Ana");
        register("luis@example.com", "Luis");
        String luis = login("luis@example.com");
        JsonNode meta = generate(ana, "{\"profileId\":\"" + POOL + "\"}", 201);
        String id = meta.get("id").asText();
        mvc.perform(get("/api/v1/reports/" + id).header("Authorization", luis)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/reports/" + id + "/content").header("Authorization", luis))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/reports").header("Authorization", luis)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/reports")).andExpect(status().isUnauthorized());
        generate(luis, "{\"profileId\":\"" + POOL + "\"}", 201);
    }
}
