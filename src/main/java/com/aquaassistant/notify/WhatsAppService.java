package com.aquaassistant.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * WhatsApp messages through the WhatsApp Cloud API of Meta. A message that the business starts must use a template
 * approved by Meta: the alarm uses one with two variables, {{1}} = the title ("Alteración del pH en el agua") and
 * {{2}} = what happened and whether it is dangerous. Without {@code WHATSAPP_TOKEN} and
 * {@code WHATSAPP_PHONE_NUMBER_ID} it is off and says so; nothing is pretended to be sent.
 */
@Service
public class WhatsAppService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppService.class);
    /** E.164: a plus sign, the country code and the number, 8 to 15 digits in all. */
    private static final Pattern E164 = Pattern.compile("\\+[1-9]\\d{7,14}");

    public record Status(boolean configured, String template) {
    }

    public record Result(boolean sent, String detail) {
    }

    private final String token;
    private final String phoneNumberId;
    private final String template;
    private final String templateLanguage;
    private final String apiUrl;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public WhatsAppService(@Value("${aquasense.whatsapp.token:}") String token,
                           @Value("${aquasense.whatsapp.phone-number-id:}") String phoneNumberId,
                           @Value("${aquasense.whatsapp.template:alerta_ph}") String template,
                           @Value("${aquasense.whatsapp.template-language:es}") String templateLanguage,
                           @Value("${aquasense.whatsapp.api-url:https://graph.facebook.com/v21.0}") String apiUrl,
                           ObjectMapper mapper) {
        this.token = token == null ? "" : token.trim();
        this.phoneNumberId = phoneNumberId == null ? "" : phoneNumberId.trim();
        this.template = template;
        this.templateLanguage = templateLanguage;
        this.apiUrl = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
        this.mapper = mapper;
    }

    public Status status() {
        return new Status(configured(), template);
    }

    public boolean configured() {
        return !token.isEmpty() && !phoneNumberId.isEmpty();
    }

    /**
     * The number as it is stored: E.164 without spaces or dashes. A Colombian mobile written without the country code
     * (10 digits starting with 3) gets +57. Returns {@code null} for an empty value and throws for an invalid one.
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String digits = raw.trim().replaceAll("[\\s().-]", "");
        if (digits.startsWith("00")) {
            digits = "+" + digits.substring(2);
        }
        if (digits.matches("3\\d{9}")) {
            digits = "+57" + digits;
        }
        if (!E164.matcher(digits).matches()) {
            throw new IllegalArgumentException("Escribe el celular con el indicativo del país, por ejemplo +57 300 123 4567.");
        }
        return digits;
    }

    /** Sends the alarm template to {@code phone}; never throws (a failed message must not stop the other notices). */
    public Result send(String phone, String title, String body) {
        if (!configured()) {
            return new Result(false, "WhatsApp no está configurado en el servidor.");
        }
        if (phone == null) {
            return new Result(false, "La cuenta no tiene número de celular.");
        }
        try {
            Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "to", phone.substring(1),
                "type", "template",
                "template", Map.of(
                    "name", template,
                    "language", Map.of("code", templateLanguage),
                    "components", List.of(Map.of("type", "body", "parameters", List.of(
                        Map.of("type", "text", "text", parameter(title, 60)),
                        Map.of("type", "text", "text", parameter(body, 900)))))));
            HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl + "/" + phoneNumberId + "/messages"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 == 2) {
                return new Result(true, "Enviado");
            }
            String reason = mapper.readTree(response.body()).path("error").path("message").asText("");
            log.warn("WhatsApp to {} failed: HTTP {} {}", masked(phone), response.statusCode(), reason);
            return new Result(false, "WhatsApp respondió " + response.statusCode() + (reason.isBlank() ? "" : ": " + reason));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new Result(false, "Se interrumpió el envío.");
        } catch (Exception ex) {
            log.warn("WhatsApp to {} failed: {}", masked(phone), ex.getMessage());
            return new Result(false, "No se pudo conectar con WhatsApp.");
        }
    }

    /** Template variables cannot have line breaks, tabs or more than four spaces in a row. */
    static String parameter(String text, int max) {
        String clean = String.valueOf(text == null ? "" : text).replaceAll("[\\r\\n\\t]+", " ").replaceAll(" {2,}", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, max - 1) + "…";
    }

    static String masked(String phone) {
        return phone == null || phone.length() < 6 ? "***" : phone.substring(0, 4) + "***" + phone.substring(phone.length() - 2);
    }

    /** For messages: the number with only the last digits visible. */
    public static String hint(String phone) {
        return phone == null ? null : masked(phone).toLowerCase(Locale.ROOT);
    }
}
