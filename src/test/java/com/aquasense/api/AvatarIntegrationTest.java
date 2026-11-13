package com.aquasense.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/** Profile photo: stored as a 256 px square JPEG, private to its owner and not changeable on demo accounts. */
@SpringBootTest
@AutoConfigureMockMvc
class AvatarIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void aPhotoIsCroppedToASquareStoredAndRemoved() throws Exception {
        String token = register();
        // 400 × 300: the left and right borders are red, the center blue; the center square must be blue only.
        BufferedImage wide = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = wide.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 400, 300);
        g.setColor(Color.BLUE);
        g.fillRect(50, 0, 300, 300);
        g.dispose();
        JsonNode user = json(mvc.perform(upload(token, "foto.png", "image/png", png(wide))).andExpect(status().isOk()).andReturn());
        assertThat(user.path("avatarVersion").isNumber()).isTrue();

        MvcResult image = mvc.perform(get("/api/v1/users/me/avatar").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn();
        assertThat(image.getResponse().getContentType()).isEqualTo("image/jpeg");
        BufferedImage stored = ImageIO.read(new ByteArrayInputStream(image.getResponse().getContentAsByteArray()));
        assertThat(stored.getWidth()).isEqualTo(256);
        assertThat(stored.getHeight()).isEqualTo(256);
        Color corner = new Color(stored.getRGB(3, 3));
        assertThat(corner.getBlue()).isGreaterThan(200);
        assertThat(corner.getRed()).isLessThan(60);

        JsonNode me = json(mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token)).andReturn());
        assertThat(me.path("avatarVersion").asLong()).isEqualTo(user.path("avatarVersion").asLong());

        JsonNode removed = json(mvc.perform(delete("/api/v1/users/me/avatar").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn());
        assertThat(removed.path("avatarVersion").isNull()).isTrue();
        mvc.perform(get("/api/v1/users/me/avatar").header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
    }

    @Test
    void filesThatAreNotPhotosAndDemoAccountsAreRejected() throws Exception {
        String token = register();
        mvc.perform(upload(token, "nota.txt", "text/plain", "no es una imagen".getBytes()))
            .andExpect(status().isUnsupportedMediaType());
        BufferedImage tiny = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        mvc.perform(upload(token, "mini.png", "image/png", png(tiny))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/users/me/avatar")).andExpect(status().isUnauthorized());

        JsonNode demos = json(mvc.perform(get("/api/v1/auth/demo-accounts")).andReturn());
        String demo = json(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + demos.get(0).path("email").asText() + "\",\"password\":\"" + demos.get(0).path("password").asText() + "\"}"))
            .andReturn()).path("token").asText();
        mvc.perform(upload(demo, "foto.png", "image/png", png(new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB))))
            .andExpect(status().isForbidden());
    }

    private MockMultipartHttpServletRequestBuilder upload(String token, String name, String type, byte[] bytes) {
        MockMultipartHttpServletRequestBuilder builder = multipart("/api/v1/users/me/avatar").file(new MockMultipartFile("file", name, type, bytes));
        builder.with(request -> {
            request.setMethod("PUT");
            return request;
        });
        builder.header("Authorization", "Bearer " + token);
        return builder;
    }

    private static byte[] png(BufferedImage image) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private String register() throws Exception {
        return json(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Usuario Foto\",\"email\":\"foto-" + UUID.randomUUID() + "@aquasense.test\",\"password\":\"Clave1234\"}"))
            .andExpect(status().isCreated()).andReturn()).path("token").asText();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }
}
