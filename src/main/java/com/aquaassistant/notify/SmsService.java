package com.aquaassistant.notify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Text messages (SMS) through the Twilio API, used for the pH alarm. Without {@code TWILIO_ACCOUNT_SID},
 * {@code TWILIO_AUTH_TOKEN} and {@code TWILIO_FROM_NUMBER} it is off and says so; nothing is pretended to be sent.
 * With a Twilio trial account messages only reach numbers verified in Twilio, and Twilio adds a line saying so.
 */
@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);
    /** E.164: a plus sign, the country code and the number, 8 to 15 digits in all. */
    private static final Pattern E164 = Pattern.compile("\\+[1-9]\\d{7,14}");
    /** Longest text sent: with accents an SMS part holds 67 characters, so this is at most five parts. */
    static final int MAX_LENGTH = 320;

    public record Result(boolean sent, String detail) {
    }

    private final String accountSid;
    private final String authToken;
    private final String fromNumber;
    private final String apiUrl;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public SmsService(@Value("${aquasense.sms.twilio.account-sid:}") String accountSid,
                      @Value("${aquasense.sms.twilio.auth-token:}") String authToken,
                      @Value("${aquasense.sms.twilio.from-number:}") String fromNumber,
                      @Value("${aquasense.sms.twilio.api-url:https://api.twilio.com}") String apiUrl,
                      ObjectMapper mapper) {
        this.accountSid = trim(accountSid);
        this.authToken = trim(authToken);
        this.fromNumber = trim(fromNumber);
        this.apiUrl = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
        this.mapper = mapper;
    }

    public boolean configured() {
        return !accountSid.isEmpty() && !authToken.isEmpty() && !fromNumber.isEmpty();
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

    /** Sends {@code text} to {@code phone}; never throws (a failed message must not stop the other notices). */
    public Result send(String phone, String text) {
        if (!configured()) {
            return new Result(false, "Los SMS no están activos en el servidor todavía.");
        }
        if (phone == null) {
            return new Result(false, "La cuenta no tiene número de celular.");
        }
        try {
            String form = "To=" + encode(phone) + "&From=" + encode(fromNumber) + "&Body=" + encode(shorten(text));
            String credentials = Base64.getEncoder().encodeToString((accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl + "/2010-04-01/Accounts/" + accountSid + "/Messages.json"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Basic " + credentials)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 == 2) {
                return new Result(true, "Enviado");
            }
            JsonNode error = mapper.readTree(response.body());
            int code = error.path("code").asInt(0);
            log.warn("SMS to {} failed: HTTP {} code {} {}", masked(phone), response.statusCode(), code, error.path("message").asText(""));
            return new Result(false, explain(code, response.statusCode()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new Result(false, "Se interrumpió el envío.");
        } catch (Exception ex) {
            log.warn("SMS to {} failed: {}", masked(phone), ex.getMessage());
            return new Result(false, "No se pudo conectar con el servicio de SMS.");
        }
    }

    /** The reasons a person can act on, in plain words (Twilio error codes). */
    static String explain(int code, int status) {
        return switch (code) {
            case 21608 -> "La cuenta de prueba de Twilio solo envía a números verificados en Twilio. Verifica este número allí o activa la cuenta.";
            case 21211, 21614 -> "Twilio dice que este número no es un celular válido.";
            case 21408 -> "Twilio no tiene permitido enviar SMS a ese país. Actívalo en Messaging → Geo permissions.";
            case 21610 -> "Este número pidió no recibir mensajes (respondió STOP).";
            case 20003 -> "Las credenciales de Twilio no son correctas.";
            default -> "El servicio de SMS respondió " + status + (code == 0 ? "" : " (código " + code + ")") + ".";
        };
    }

    static String shorten(String text) {
        String clean = String.valueOf(text == null ? "" : text).replaceAll("\\s+", " ").trim();
        return clean.length() <= MAX_LENGTH ? clean : clean.substring(0, MAX_LENGTH - 1) + "…";
    }

    static String masked(String phone) {
        return phone == null || phone.length() < 6 ? "***" : phone.substring(0, 4) + "***" + phone.substring(phone.length() - 2);
    }

    /** For messages: the number with only the last digits visible. */
    public static String hint(String phone) {
        return phone == null ? null : masked(phone).toLowerCase(Locale.ROOT);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
