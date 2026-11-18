package com.aquaassistant.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class PlacesIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void everyAccountStartsWithMiAguaAndMeasurementsFollowTheActivePlace() throws Exception {
        String ana = register();
        JsonNode places = json(mvc.perform(auth(get("/api/v1/places"), ana)).andExpect(status().isOk()).andReturn());
        assertThat(places).hasSize(1);
        assertThat(places.get(0).get("name").asText()).isEqualTo("Mi agua");
        assertThat(places.get(0).get("active").asBoolean()).isTrue();
        measure(ana, "{\"ph\":7.0}").andExpect(status().isCreated());

        JsonNode pool = json(mvc.perform(auth(post("/api/v1/places"), ana).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Piscina de la casa\",\"kind\":\"POOL\",\"volumeLiters\":40000}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.profileName").value("Piscina")).andReturn());
        // The new place starts empty and uses the pool profile; extra parameters are stored.
        mvc.perform(auth(get("/api/v1/measurements"), ana)).andExpect(jsonPath("$.totalElements").value(0));
        measure(ana, "{\"ph\":7.4,\"freeChlorine\":2.5,\"totalAlkalinity\":90,\"calciumHardness\":250,\"cyanuricAcid\":40}")
            .andExpect(status().isCreated()).andExpect(jsonPath("$.placeId").value(pool.get("id").asLong()))
            .andExpect(jsonPath("$.params.freeChlorine").value(2.5)).andExpect(jsonPath("$.profileName").value("Piscina"));
        mvc.perform(auth(get("/api/v1/dashboard"), ana)).andExpect(jsonPath("$.measurementCount").value(1));

        mvc.perform(auth(post("/api/v1/places/" + places.get(0).get("id").asLong() + "/activate"), ana)).andExpect(status().isOk());
        mvc.perform(auth(get("/api/v1/measurements"), ana)).andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].ph").value(7.0));
        measure(ana, "{\"ph\":7.2,\"freeChlorine\":25}").andExpect(status().isBadRequest());
    }

    @Test
    void aPlaceIsSharedWithACodeToSeeOrToMeasure() throws Exception {
        String owner = register();
        String guest = register();
        JsonNode pool = json(mvc.perform(auth(post("/api/v1/places"), owner).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Piscina compartida\",\"kind\":\"POOL\"}")).andExpect(status().isCreated()).andReturn());
        long id = pool.get("id").asLong();
        measure(owner, "{\"ph\":7.5}").andExpect(status().isCreated());

        String code = json(mvc.perform(auth(post("/api/v1/places/" + id + "/share"), owner).contentType(MediaType.APPLICATION_JSON)
            .content("{\"role\":\"VIEWER\"}")).andExpect(status().isOk()).andReturn()).get("shareCode").asText();
        assertThat(code).hasSize(8);
        // The guest cannot see the place before joining.
        mvc.perform(auth(get("/api/v1/places/" + id), guest)).andExpect(status().isNotFound());

        mvc.perform(auth(post("/api/v1/places/join"), guest).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code.toLowerCase().substring(0, 4) + "-" + code.toLowerCase().substring(4) + "\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("VIEWER")).andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.shareCode").doesNotExist());
        mvc.perform(auth(get("/api/v1/measurements"), guest)).andExpect(jsonPath("$.content[0].ph").value(7.5));
        measure(guest, "{\"ph\":7.1}").andExpect(status().isForbidden());

        // With an editor code the guest can measure; the owner sees it and can remove the guest.
        String editorCode = json(mvc.perform(auth(post("/api/v1/places/" + id + "/share"), owner).contentType(MediaType.APPLICATION_JSON)
            .content("{\"role\":\"EDITOR\"}")).andReturn()).get("shareCode").asText();
        mvc.perform(auth(post("/api/v1/places/join"), guest).contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"" + editorCode + "\"}")).andExpect(jsonPath("$.role").value("EDITOR"));
        measure(guest, "{\"ph\":7.1}").andExpect(status().isCreated());
        mvc.perform(auth(get("/api/v1/measurements"), owner)).andExpect(jsonPath("$.totalElements").value(2));
        JsonNode members = json(mvc.perform(auth(get("/api/v1/places/" + id + "/members"), owner)).andReturn());
        assertThat(members).hasSize(2);
        long guestId = members.get(1).get("userId").asLong();
        mvc.perform(auth(delete("/api/v1/places/" + id + "/members/" + guestId), guest)).andExpect(status().isForbidden());
        mvc.perform(auth(delete("/api/v1/places/" + id + "/members/" + guestId), owner)).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/v1/places/" + id), guest)).andExpect(status().isNotFound());
        // Back in their own place, the guest measures as before.
        measure(guest, "{\"ph\":6.9}").andExpect(status().isCreated());

        mvc.perform(auth(delete("/api/v1/places/" + id + "/share"), owner)).andExpect(jsonPath("$.shareCode").doesNotExist());
        mvc.perform(auth(post("/api/v1/places/join"), guest).contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"" + editorCode + "\"}")).andExpect(status().isNotFound());
    }

    @Test
    void theLastOwnPlaceCannotBeDeleted() throws Exception {
        String ana = register();
        long mine = json(mvc.perform(auth(get("/api/v1/places"), ana)).andReturn()).get(0).get("id").asLong();
        mvc.perform(auth(delete("/api/v1/places/" + mine), ana)).andExpect(status().isConflict());
        mvc.perform(auth(post("/api/v1/places"), ana).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Acuario\",\"kind\":\"AQUARIUM\"}")).andExpect(status().isCreated());
        mvc.perform(auth(delete("/api/v1/places/" + mine), ana)).andExpect(status().isNoContent());
    }

    private org.springframework.test.web.servlet.ResultActions measure(String token, String body) throws Exception {
        return mvc.perform(auth(post("/api/v1/measurements"), token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }

    private String register() throws Exception {
        String email = "p" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        MvcResult result = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Persona Prueba\",\"email\":\"" + email + "\",\"password\":\"Clave1234\"}"))
            .andExpect(status().isCreated()).andReturn();
        return json(result).get("token").asText();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }
}
