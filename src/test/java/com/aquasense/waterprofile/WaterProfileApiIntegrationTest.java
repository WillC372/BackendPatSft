package com.aquasense.waterprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class WaterProfileApiIntegrationTest {

    private static final String POOL_ID = "00000000-0000-0000-0000-000000000002";
    private static final String BODY = """
            {"name":"Acuario discos","description":"Especie sensible","optimalMin":6.0,"optimalMax":6.8,
             "cautionMin":5.5,"cautionMax":7.2}""";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("delete from users");
    }

    private String tokenFor(String email) throws Exception {
        String reg = "{\"displayName\":\"U\",\"email\":\"" + email
                + "\",\"password\":\"Clave1234\",\"confirmPassword\":\"Clave1234\"}";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(reg));
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"" + email + "\",\"password\":\"Clave1234\"}"))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + mapper.readTree(body).get("accessToken").asText();
    }

    private String createCustom(String token) throws Exception {
        String body = mvc.perform(post("/api/v1/profiles").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("id").asText();
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/profiles")).andExpect(status().isUnauthorized());
    }

    @Test
    void listsFiveSystemProfilesWithSources() throws Exception {
        String token = tokenFor("a@example.com");
        String body = mvc.perform(get("/api/v1/profiles").header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode list = mapper.readTree(body);
        assertThat(list).hasSize(5);
        list.forEach(p -> {
            assertThat(p.get("system").asBoolean()).isTrue();
            assertThat(p.get("sourceReference").asText()).isNotBlank();
        });
    }

    @Test
    void customProfilesAreVisibleOnlyToTheirOwner() throws Exception {
        String ana = tokenFor("ana@example.com");
        String luis = tokenFor("luis@example.com");
        String id = createCustom(ana);
        mvc.perform(get("/api/v1/profiles/" + id).header("Authorization", ana)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/profiles/" + id).header("Authorization", luis)).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/profiles/" + id).header("Authorization", luis)
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/profiles/" + id).header("Authorization", luis)).andExpect(status().isNotFound());
        String luisList = mvc.perform(get("/api/v1/profiles").header("Authorization", luis))
                .andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(luisList)).hasSize(5);
    }

    @Test
    void systemProfilesAreReadOnlyButCanBeDuplicated() throws Exception {
        String token = tokenFor("a@example.com");
        mvc.perform(put("/api/v1/profiles/" + POOL_ID).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/profiles/" + POOL_ID).header("Authorization", token))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/profiles/" + POOL_ID + "/duplicate").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Mi piscina\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.type").value("CUSTOM"))
                .andExpect(jsonPath("$.system").value(false)).andExpect(jsonPath("$.optimalMin").value(7.2));
    }

    @Test
    void updateAndDeleteOwnProfile() throws Exception {
        String token = tokenFor("a@example.com");
        String id = createCustom(token);
        mvc.perform(put("/api/v1/profiles/" + id).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Otro\",\"description\":\"d\",\"optimalMin\":6.2,\"optimalMax\":6.9,"
                        + "\"cautionMin\":5.5,\"cautionMax\":7.2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Otro"));
        mvc.perform(post("/api/v1/profiles").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Mal\",\"description\":\"d\",\"optimalMin\":7.0,\"optimalMax\":6.0,"
                        + "\"cautionMin\":5.5,\"cautionMax\":7.2}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_RANGES"));
        mvc.perform(delete("/api/v1/profiles/" + id).header("Authorization", token))
                .andExpect(status().isNoContent());
    }

    @Test
    void classifyEndpointExplainsAndWarnsAboutLimits() throws Exception {
        String token = tokenFor("a@example.com");
        mvc.perform(get("/api/v1/profiles/" + POOL_ID + "/classify?ph=5.8").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CRITICAL"))
                .andExpect(jsonPath("$.acidity").value("ACIDIC"))
                .andExpect(jsonPath("$.withinRecommendedRange").value(false))
                .andExpect(jsonPath("$.disclaimer").value(org.hamcrest.Matchers.containsString("no demuestra")));
        mvc.perform(get("/api/v1/profiles/" + POOL_ID + "/classify?ph=15").header("Authorization", token))
                .andExpect(status().isBadRequest());
    }
}
