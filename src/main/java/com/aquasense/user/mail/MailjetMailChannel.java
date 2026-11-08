package com.aquasense.user.mail;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Adapter for the Mailjet send API ({@code POST /v3.1/send}) with basic authentication. It works over HTTPS,
 * so it also works where the SMTP port is blocked.
 */
@Component
public class MailjetMailChannel implements MailChannel {

    private static final String USER_AGENT = "AquaSense-AI/1.0";
    private static final int MAX_ERROR = 300;

    private final MailDeliveryProperties.Mailjet config;
    private final ObjectMapper mapper;
    private final HttpClient http;

    @Autowired
    public MailjetMailChannel(MailDeliveryProperties properties, ObjectMapper mapper) {
        this(properties.mailjet(), mapper, HttpClient.newBuilder().proxy(ProxySelector.getDefault())
            .connectTimeout(Duration.ofSeconds(10)).build());
    }

    MailjetMailChannel(MailDeliveryProperties.Mailjet config, ObjectMapper mapper, HttpClient http) {
        this.config = config;
        this.mapper = mapper;
        this.http = http;
    }

    @Override
    public String id() {
        return "mailjet";
    }

    @Override
    public String label() {
        return "Mailjet (API HTTPS)";
    }

    @Override
    public boolean isConfigured() {
        return config.isConfigured();
    }

    @Override
    public void send(OutgoingMail mail, String fromEmail, String fromName) {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode message = root.putArray("Messages").addObject();
        message.putObject("From").put("Email", fromEmail).put("Name", fromName);
        message.putArray("To").addObject().put("Email", mail.to());
        message.put("Subject", mail.subject());
        message.put("TextPart", mail.text());
        if (mail.html() != null) {
            message.put("HTMLPart", mail.html());
        }
        String credentials = Base64.getEncoder().encodeToString((config.apiKey() + ":" + config.secretKey())
            .getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(config.baseUrl() + "/v3.1/send"))
            .timeout(Duration.ofSeconds(config.timeoutSeconds()))
            .header("Authorization", "Basic " + credentials)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .POST(HttpRequest.BodyPublishers.ofString(root.toString(), StandardCharsets.UTF_8))
            .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new MailDeliveryException("Mailjet no respondió: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new MailDeliveryException("Envío interrumpido.", ex);
        }
        String status = statusOf(response.body());
        if (response.statusCode() / 100 != 2 || !"success".equalsIgnoreCase(status)) {
            throw new MailDeliveryException("Mailjet rechazó el envío (" + response.statusCode() + "): " + errorOf(response.body()));
        }
    }

    private String statusOf(String body) {
        try {
            return mapper.readTree(body).path("Messages").path(0).path("Status").asText("");
        } catch (IOException ex) {
            return "";
        }
    }

    /** Mailjet error message (for example, sender not validated or invalid credentials), trimmed. */
    private String errorOf(String body) {
        String text;
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode first = root.path("Messages").path(0).path("Errors").path(0);
            text = first.path("ErrorMessage").asText("");
            if (text.isBlank()) {
                text = root.path("ErrorMessage").asText("");
            }
            if (text.isBlank()) {
                text = body;
            }
        } catch (IOException ex) {
            text = body == null ? "" : body;
        }
        return text.length() > MAX_ERROR ? text.substring(0, MAX_ERROR) : text;
    }
}
