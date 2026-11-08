package com.aquasense.user.mail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Adapter de la API de correo transaccional de Brevo ({@code POST /v3/smtp/email}, encabezado {@code api-key}).
 * Funciona por HTTPS, así que sirve también donde el puerto SMTP está bloqueado.
 */
@Component
public class BrevoMailChannel implements MailChannel {

    private static final String USER_AGENT = "AquaSense-AI/1.0";
    private static final int MAX_ERROR = 300;

    private final MailDeliveryProperties.Brevo config;
    private final ObjectMapper mapper;
    private final HttpClient http;

    @Autowired
    public BrevoMailChannel(MailDeliveryProperties properties, ObjectMapper mapper) {
        this(properties.brevo(), mapper, HttpClient.newBuilder().proxy(ProxySelector.getDefault())
            .connectTimeout(Duration.ofSeconds(10)).build());
    }

    BrevoMailChannel(MailDeliveryProperties.Brevo config, ObjectMapper mapper, HttpClient http) {
        this.config = config;
        this.mapper = mapper;
        this.http = http;
    }

    @Override
    public String id() {
        return "brevo";
    }

    @Override
    public String label() {
        return "Brevo (API HTTPS)";
    }

    @Override
    public boolean isConfigured() {
        return config.isConfigured();
    }

    @Override
    public void send(OutgoingMail mail, String fromEmail, String fromName) {
        ObjectNode body = mapper.createObjectNode();
        body.putObject("sender").put("name", fromName).put("email", fromEmail);
        body.putArray("to").addObject().put("email", mail.to());
        body.put("subject", mail.subject());
        body.put("textContent", mail.text());
        if (mail.html() != null) {
            body.put("htmlContent", mail.html());
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(config.baseUrl() + "/smtp/email"))
            .timeout(Duration.ofSeconds(config.timeoutSeconds()))
            .header("api-key", config.apiKey())
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
            .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new MailDeliveryException("Brevo no respondió: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new MailDeliveryException("Envío interrumpido.", ex);
        }
        if (response.statusCode() / 100 != 2) {
            throw new MailDeliveryException("Brevo rechazó el envío (" + response.statusCode() + "): " + brevoMessage(response.body()));
        }
    }

    /** Extrae el mensaje de error de Brevo (por ejemplo, remitente no verificado) sin devolver el cuerpo completo. */
    private String brevoMessage(String body) {
        try {
            String message = mapper.readTree(body).path("message").asText("");
            String text = message.isBlank() ? body : message;
            return text.length() > MAX_ERROR ? text.substring(0, MAX_ERROR) : text;
        } catch (IOException ex) {
            return body == null ? "" : body.substring(0, Math.min(MAX_ERROR, body.length()));
        }
    }
}
