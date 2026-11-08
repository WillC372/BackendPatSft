package com.aquasense.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquasense.user.User;
import com.aquasense.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Administration screen: search, plan with and without expiry, role, deactivation and the rules that protect the
 * administrator's own account, the main administrator (ADMIN_EMAIL) and disabled accounts.
 */
@SpringBootTest(properties = "aquasense.security.admin-email=main-admin@aquasense.test")
@AutoConfigureMockMvc
class AdminIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private UserRepository users;

    @Autowired
    private TransactionTemplate tx;

    private String admin;
    private long adminId;
    private String tag;

    @BeforeEach
    void setUp() throws Exception {
        tag = UUID.randomUUID().toString().substring(0, 8);
        String email = "adm-" + tag + "@aquasense.test";
        register(email, "Admin " + tag);
        adminId = promote(email);
        admin = login(email).path("token").asText();
    }

    @Test
    void nonAdminsCannotUseTheAdminApi() throws Exception {
        String user = register("u-" + tag + "@aquasense.test", "Usuario").path("token").asText();
        for (String path : new String[] {"/api/v1/admin/summary", "/api/v1/admin/users", "/api/v1/admin/payments",
            "/api/v1/admin/activity"}) {
            mvc.perform(get(path).header("Authorization", "Bearer " + user)).andExpect(status().isForbidden());
        }
    }

    @Test
    void searchFiltersAndPaginates() throws Exception {
        register("ana-" + tag + "@aquasense.test", "Ana Busqueda " + tag);
        register("beto-" + tag + "@aquasense.test", "Beto " + tag);
        JsonNode found = body(call(get("/api/v1/admin/users").param("q", "busqueda " + tag)));
        assertThat(found.path("total").asLong()).isEqualTo(1);
        assertThat(found.path("items").get(0).path("email").asText()).isEqualTo("ana-" + tag + "@aquasense.test");
        assertThat(found.path("items").get(0).path("self").asBoolean()).isFalse();

        JsonNode page = body(call(get("/api/v1/admin/users").param("q", tag).param("size", "1")));
        assertThat(page.path("total").asLong()).isEqualTo(3);
        assertThat(page.path("totalPages").asInt()).isEqualTo(3);
        assertThat(page.path("items").size()).isEqualTo(1);

        JsonNode admins = body(call(get("/api/v1/admin/users").param("q", tag).param("role", "ADMIN")));
        assertThat(admins.path("total").asLong()).isEqualTo(1);
        assertThat(admins.path("items").get(0).path("self").asBoolean()).isTrue();
        // SQL wildcards are searched as text; they do not widen the search.
        assertThat(body(call(get("/api/v1/admin/users").param("q", "%_" + tag))).path("total").asLong()).isZero();
        call(get("/api/v1/admin/users").param("role", "JEFE")).andExpect(status().isBadRequest());

        JsonNode summary = body(call(get("/api/v1/admin/summary")));
        assertThat(summary.path("users").asLong()).isGreaterThanOrEqualTo(3);
        assertThat(summary.path("admins").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(summary.path("plan").path("testMode").asBoolean()).isTrue();
    }

    @Test
    void planCanBeGrantedWithOrWithoutExpiry() throws Exception {
        long id = register("plan-" + tag + "@aquasense.test", "Plan " + tag).path("user").path("id").asLong();
        JsonNode timed = body(call(put("/api/v1/admin/users/" + id + "/plan").contentType(MediaType.APPLICATION_JSON)
            .content("{\"plan\":\"PREMIUM\",\"days\":30}")));
        Instant until = Instant.parse(timed.path("user").path("premiumUntil").asText());
        assertThat(Duration.between(Instant.now(), until).toDays()).isBetween(29L, 30L);
        assertThat(timed.path("user").path("manualPremium").asBoolean()).isFalse();

        JsonNode open = body(call(put("/api/v1/admin/users/" + id + "/plan").contentType(MediaType.APPLICATION_JSON)
            .content("{\"plan\":\"PREMIUM\"}")));
        assertThat(open.path("user").path("manualPremium").asBoolean()).isTrue();
        assertThat(open.path("user").path("premiumUntil").isNull()).isTrue();

        JsonNode free = body(call(put("/api/v1/admin/users/" + id + "/plan").contentType(MediaType.APPLICATION_JSON)
            .content("{\"plan\":\"FREE\",\"days\":30}")));
        assertThat(free.path("user").path("plan").asText()).isEqualTo("FREE");
        assertThat(free.path("activity").get(0).path("action").asText()).isEqualTo("ACCOUNT_PLAN_CHANGED");
        assertThat(free.path("activity").get(0).path("detail").asText()).contains("Premium sin vencimiento → Gratuito");

        call(put("/api/v1/admin/users/" + id + "/plan").contentType(MediaType.APPLICATION_JSON)
            .content("{\"plan\":\"PREMIUM\",\"days\":0}")).andExpect(status().isBadRequest());
        JsonNode log = body(call(get("/api/v1/admin/activity")));
        assertThat(log.get(0).path("action").asText()).isEqualTo("ADMIN_PLAN_CHANGED");
        assertThat(log.get(0).path("label").asText()).isEqualTo("Cambió el plan de un usuario");
        assertThat(log.get(0).path("userEmail").asText()).isEqualTo("adm-" + tag + "@aquasense.test");
    }

    @Test
    void disabledAccountsCannotSignInUntilReactivated() throws Exception {
        String email = "off-" + tag + "@aquasense.test";
        JsonNode reg = register(email, "Apagada " + tag);
        long id = reg.path("user").path("id").asLong();
        String token = reg.path("token").asText();

        JsonNode off = body(call(put("/api/v1/admin/users/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
            .content("{\"enabled\":false,\"reason\":\"Uso indebido de prueba\"}")));
        assertThat(off.path("user").path("enabled").asBoolean()).isFalse();
        assertThat(off.path("disabledReason").asText()).isEqualTo("Uso indebido de prueba");
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"Clave1234\"}"))
            .andExpect(status().isForbidden());
        // With a wrong password the response does not reveal that the account exists and is disabled.
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"Otra12345\"}"))
            .andExpect(status().isUnauthorized());
        assertThat(body(call(get("/api/v1/admin/users").param("q", tag).param("status", "DISABLED"))).path("total").asLong())
            .isEqualTo(1);
        call(put("/api/v1/admin/users/" + id + "/role").contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
            .andExpect(status().isConflict());

        call(put("/api/v1/admin/users/" + id + "/status").contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
            .andExpect(status().isOk());
        assertThat(login(email).path("token").asText()).isNotBlank();
        JsonNode detail = body(call(get("/api/v1/admin/users/" + id)));
        assertThat(detail.path("user").path("lastLoginAt").isNull()).isFalse();
        assertThat(detail.path("activity").toString()).contains("ACCOUNT_DISABLED").contains("ACCOUNT_ENABLED").contains("LOGIN_BLOCKED");
    }

    @Test
    void rolesChangeImmediatelyAndProtectedAccountsStayAdmin() throws Exception {
        JsonNode reg = register("rol-" + tag + "@aquasense.test", "Rol " + tag);
        long id = reg.path("user").path("id").asLong();
        String old = reg.path("token").asText();
        JsonNode promoted = body(call(put("/api/v1/admin/users/" + id + "/role").contentType(MediaType.APPLICATION_JSON)
            .content("{\"role\":\"ADMIN\"}")));
        assertThat(promoted.path("user").path("role").asText()).isEqualTo("ADMIN");
        // The role change closes their sessions: the previous token stops being valid.
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + old)).andExpect(status().isUnauthorized());
        call(put("/api/v1/admin/users/" + id + "/role").contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"USER\"}"))
            .andExpect(status().isOk());

        // On their own account: neither change the role nor disable it.
        JsonNode self = body(call(get("/api/v1/admin/users/" + adminId)));
        assertThat(self.path("guards").path("roleBlockedReason").asText()).contains("propio rol");
        call(put("/api/v1/admin/users/" + adminId + "/role").contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"USER\"}"))
            .andExpect(status().isConflict());
        call(put("/api/v1/admin/users/" + adminId + "/status").contentType(MediaType.APPLICATION_JSON)
            .content("{\"enabled\":false}")).andExpect(status().isConflict());

        // The main administrator (ADMIN_EMAIL) is not demoted or disabled from the screen.
        String main = "main-admin@aquasense.test";
        if (users.findByEmailIgnoreCase(main).isEmpty()) {
            register(main, "Principal");
        }
        long mainId = promote(main);
        JsonNode mainDetail = body(call(get("/api/v1/admin/users/" + mainId)));
        assertThat(mainDetail.path("user").path("configuredAdmin").asBoolean()).isTrue();
        assertThat(mainDetail.path("guards").path("statusBlockedReason").asText()).contains("ADMIN_EMAIL");
        call(put("/api/v1/admin/users/" + mainId + "/role").contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"USER\"}"))
            .andExpect(status().isConflict());
        call(put("/api/v1/admin/users/" + mainId + "/status").contentType(MediaType.APPLICATION_JSON)
            .content("{\"enabled\":false}")).andExpect(status().isConflict());
        call(get("/api/v1/admin/users/999999")).andExpect(status().isNotFound());
        call(get("/api/v1/admin/payments")).andExpect(status().isOk());
    }

    @Autowired
    private com.aquasense.payment.PaymentRepository payments;

    @Autowired
    private com.aquasense.measurement.MeasurementRepository measurements;

    @Test
    void accountsAreDeletedWithTheirDataUnlessProtected() throws Exception {
        JsonNode reg = register("borrar-" + tag + "@aquasense.test", "Borrar " + tag);
        long id = reg.path("user").path("id").asLong();
        String token = reg.path("token").asText();
        mvc.perform(post("/api/v1/measurements").header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"ph\":7.2}")).andExpect(status().isCreated());
        assertThat(measurements.countByUserId(id)).isEqualTo(1);
        assertThat(body(call(get("/api/v1/admin/users/" + id))).path("guards").path("deleteBlockedReason").isNull()).isTrue();

        call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/admin/users/" + id))
            .andExpect(status().isNoContent());
        assertThat(users.findById(id)).isEmpty();
        assertThat(measurements.countByUserId(id)).isZero();
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        assertThat(body(call(get("/api/v1/admin/activity"))).get(0).path("action").asText()).isEqualTo("ADMIN_USER_DELETED");

        // Own account (use Settings), and accounts with real approved payments, are kept.
        call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/admin/users/" + adminId))
            .andExpect(status().isConflict());
        long paid = register("pagado-" + tag + "@aquasense.test", "Pagado " + tag).path("user").path("id").asLong();
        tx.executeWithoutResult(s -> {
            var p = new com.aquasense.payment.Payment(paid, "PAYU", "REAL-" + tag, new java.math.BigDecimal("49900"), "COP", 30, false);
            p.update(com.aquasense.payment.PaymentStatus.APPROVED, "1", "tx", "APPROVED", "VISA");
            payments.save(p);
        });
        assertThat(body(call(get("/api/v1/admin/users/" + paid))).path("guards").path("deleteBlockedReason").asText())
            .contains("pagos reales");
        call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/admin/users/" + paid))
            .andExpect(status().isConflict());
    }

    @Test
    void usersDeleteTheirOwnAccountWithTheirPassword() throws Exception {
        String email = "yo-" + tag + "@aquasense.test";
        JsonNode reg = register(email, "Yo " + tag);
        String token = reg.path("token").asText();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/users/me")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"Otra12345\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/users/me")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"Clave1234\"}"))
            .andExpect(status().isNoContent());
        assertThat(users.findByEmailIgnoreCase(email)).isEmpty();
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"Clave1234\"}")).andExpect(status().isUnauthorized());
        // The email can be registered again as a new, empty account.
        register(email, "Yo otra vez");
    }

    private ResultActions call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + admin));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return mapper.readTree(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private long promote(String email) {
        return tx.execute(s -> {
            User user = users.findByEmailIgnoreCase(email).orElseThrow();
            if (user.getRole() != com.aquasense.user.Role.ADMIN) {
                user.promoteToAdmin();
            }
            return user.getId();
        });
    }

    private JsonNode register(String email, String name) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"Clave1234\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode login(String email) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"Clave1234\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
