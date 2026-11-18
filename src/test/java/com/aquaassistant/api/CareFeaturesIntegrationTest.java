package com.aquaassistant.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquaassistant.reminder.ReminderScheduler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = {"aquasense.reminders.initial-delay-ms=3600000"})
@AutoConfigureMockMvc
class CareFeaturesIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private ReminderScheduler scheduler;

    @Test
    void treatmentsAreLoggedPerPlace() throws Exception {
        String ana = register();
        mvc.perform(auth(post("/api/v1/treatments"), ana).contentType(MediaType.APPLICATION_JSON)
                .content("{\"product\":\"SODA_ASH\",\"amount\":120,\"unit\":\"g\",\"note\":\"Antes de nadar\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.productLabel").value("Carbonato de sodio (sube el pH)"));
        mvc.perform(auth(post("/api/v1/treatments"), ana).contentType(MediaType.APPLICATION_JSON)
            .content("{\"product\":\"SODA_ASH\",\"amount\":120}")).andExpect(status().isBadRequest());
        mvc.perform(auth(post("/api/v1/treatments"), ana).contentType(MediaType.APPLICATION_JSON)
            .content("{\"product\":\"FILTER_CLEANING\"}")).andExpect(status().isCreated());
        JsonNode list = json(mvc.perform(auth(get("/api/v1/treatments"), ana)).andExpect(status().isOk()).andReturn());
        assertThat(list).hasSize(2);
        // Another place starts with an empty log.
        mvc.perform(auth(post("/api/v1/places"), ana).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Acuario\",\"kind\":\"AQUARIUM\"}")).andExpect(status().isCreated());
        assertThat(json(mvc.perform(auth(get("/api/v1/treatments"), ana)).andReturn())).isEmpty();
        String other = register();
        mvc.perform(auth(delete("/api/v1/treatments/" + list.get(0).get("id").asLong()), other)).andExpect(status().isNotFound());
    }

    @Test
    void calibrationsAreKeptAsHistory() throws Exception {
        String ana = register();
        mvc.perform(auth(post("/api/v1/calibrations"), ana).contentType(MediaType.APPLICATION_JSON)
                .content("{\"deviceName\":\"ESP32 PH-4502C\",\"protocol\":\"AQUASENSE_ESP32\",\"point\":\"MID\",\"bufferValue\":7.0,\"reading\":7.12,\"response\":\"OK CAL MID\",\"success\":true}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.deviation").value(0.12))
            .andExpect(jsonPath("$.pointLabel").value("Punto medio (neutro)"));
        mvc.perform(auth(get("/api/v1/calibrations"), ana)).andExpect(jsonPath("$[0].deviceName").value("ESP32 PH-4502C"));
        mvc.perform(auth(get("/api/v1/calibrations"), register())).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void remindersBecomeDueAndRestartWhenDone() throws Exception {
        String ana = register();
        JsonNode created = json(mvc.perform(auth(post("/api/v1/reminders"), ana).contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"CLEAN_FILTER\",\"everyDays\":7,\"nextDue\":\"" + Instant.now().minusSeconds(60) + "\",\"byEmail\":true,\"byPush\":true}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("Limpia el filtro"))
            .andExpect(jsonPath("$.due").value(true)).andExpect(jsonPath("$.placeName").value("Mi agua")).andReturn());
        assertThat(scheduler.sendDue()).isGreaterThanOrEqualTo(1);
        // Once notified, the same due date is not notified again.
        long id = created.get("id").asLong();
        JsonNode done = json(mvc.perform(auth(post("/api/v1/reminders/" + id + "/done"), ana)).andExpect(status().isOk()).andReturn());
        assertThat(done.get("due").asBoolean()).isFalse();
        assertThat(Instant.parse(done.get("nextDue").asText())).isAfter(Instant.now().plusSeconds(6 * 86400));
        mvc.perform(auth(put("/api/v1/reminders/" + id), ana).contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"CUSTOM\",\"title\":\"Revisar la bomba\",\"everyDays\":30,\"byEmail\":false,\"byPush\":true}"))
            .andExpect(jsonPath("$.title").value("Revisar la bomba")).andExpect(jsonPath("$.everyDays").value(30));
        mvc.perform(auth(post("/api/v1/reminders"), ana).contentType(MediaType.APPLICATION_JSON)
            .content("{\"kind\":\"MEASURE\",\"everyDays\":0}")).andExpect(status().isBadRequest());
        mvc.perform(auth(delete("/api/v1/reminders/" + id), register())).andExpect(status().isNotFound());
        mvc.perform(auth(delete("/api/v1/reminders/" + id), ana)).andExpect(status().isNoContent());
    }

    @Test
    void devicesSubscribeToNotifications() throws Exception {
        String ana = register();
        String key = json(mvc.perform(auth(get("/api/v1/push/key"), ana)).andExpect(status().isOk()).andReturn()).get("publicKey").asText();
        assertThat(java.util.Base64.getUrlDecoder().decode(key)).hasSize(65);
        String body = "{\"endpoint\":\"https://push.example.com/abc\",\"keys\":{\"p256dh\":\"" + key + "\",\"auth\":\"AAAAAAAAAAAAAAAAAAAAAA\"},\"label\":\"Chrome en Android\"}";
        mvc.perform(auth(post("/api/v1/push/subscriptions"), ana).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.label").value("Chrome en Android"));
        mvc.perform(auth(get("/api/v1/push/devices"), ana)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(auth(post("/api/v1/push/subscriptions"), ana).contentType(MediaType.APPLICATION_JSON)
            .content(body.replace("https://push.example.com/abc", "http://inseguro"))).andExpect(status().isBadRequest());
        mvc.perform(auth(delete("/api/v1/push/subscriptions"), ana).contentType(MediaType.APPLICATION_JSON)
            .content("{\"endpoint\":\"https://push.example.com/abc\"}")).andExpect(jsonPath("$.removed").value(true));
        mvc.perform(auth(get("/api/v1/users/me/settings"), ana)).andExpect(jsonPath("$.emailAlerts").value(true));
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }

    private String register() throws Exception {
        String email = "c" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        MvcResult result = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Persona Prueba\",\"email\":\"" + email + "\",\"password\":\"Clave1234\"}"))
            .andExpect(status().isCreated()).andReturn();
        return json(result).get("token").asText();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }
}
