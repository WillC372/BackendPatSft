package com.aquasense.intelligence.ai;

import com.aquasense.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Provider for APIs with the OpenAI "chat/completions" format. A single class serves
 * Google Gemini (endpoint /v1beta/openai) and Groq; only the configuration changes.
 * The key is sent in "Authorization: Bearer" and never leaves the server.
 */
public class OpenAiCompatibleProvider implements AiTextProvider {

    static final String USER_AGENT = "AquaSense-AI/1.0";
    static final long MAX_RETRY_WAIT_MS = 20_000;
    private static final java.util.regex.Pattern SECONDS = java.util.regex.Pattern.compile("([0-9]+(?:\\.[0-9]+)?)(ms|s)?");

    private final AppProperties.Provider config;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public OpenAiCompatibleProvider(AppProperties.Provider config, ObjectMapper mapper) {
        this(config, mapper, HttpClient.newBuilder().proxy(ProxySelector.getDefault())
            .connectTimeout(Duration.ofSeconds(10)).build());
    }

    OpenAiCompatibleProvider(AppProperties.Provider config, ObjectMapper mapper, HttpClient http) {
        this.config = config;
        this.mapper = mapper;
        this.http = http;
    }

    @Override
    public boolean isAvailable() {
        return config.isConfigured();
    }

    @Override
    public String providerName() {
        return config.label();
    }

    @Override
    public String modelName() {
        return config.model();
    }

    @Override
    public boolean supportsVision() {
        return config.vision();
    }

    @Override
    public boolean supportsTools() {
        return config.tools();
    }

    @Override
    public AiAnswer complete(AiPrompt prompt) {
        if (!isAvailable()) {
            throw new AiUnavailableException(config.label() + " no está configurado.");
        }
        return parse(send(requestBody(prompt)));
    }

    @Override
    public ToolTurn chatWithTools(ArrayNode messages, ArrayNode tools) {
        if (!isAvailable() || !supportsTools()) {
            throw new AiUnavailableException(config.label() + " (" + config.model() + ") no admite herramientas.");
        }
        ObjectNode body = mapper.createObjectNode();
        body.put("model", config.model());
        body.set("messages", messages);
        body.set("tools", tools);
        body.put("tool_choice", "required");
        try {
            JsonNode root = mapper.readTree(send(mapper.writeValueAsString(body)));
            JsonNode message = root.path("choices").path(0).path("message");
            if (message.isMissingNode() || !message.isObject()) {
                throw new AiUnavailableException(config.label() + ": respuesta sin mensaje.");
            }
            JsonNode usage = root.path("usage");
            return new ToolTurn(message, root.path("model").asText(config.model()), usage.path("prompt_tokens").asLong(0),
                usage.path("completion_tokens").asLong(0));
        } catch (IOException ex) {
            throw new AiUnavailableException(config.label() + ": respuesta con formato inesperado.", ex);
        }
    }

    /**
     * Retry: if the provider answers 429 or 503 and asks for a short wait (Retry-After or x-ratelimit-reset-*),
     * it waits that long and retries once. If the wait is long (for example, the daily quota is exhausted),
     * it fails right away so the chain moves to the next provider.
     */
    private String send(String json) {
        HttpResponse<String> response = exchange(json);
        int status = response.statusCode();
        if (status == 429 || status == 503) {
            long waitMs = retryDelayMs(response, status);
            if (waitMs >= 0 && waitMs <= MAX_RETRY_WAIT_MS) {
                sleep(waitMs);
                response = exchange(json);
            }
        }
        return check(response);
    }

    static long retryDelayMs(HttpResponse<?> response, int status) {
        for (String header : new String[] {"retry-after", "x-ratelimit-reset-tokens", "x-ratelimit-reset-requests"}) {
            var value = response.headers().firstValue(header);
            if (value.isPresent()) {
                java.util.regex.Matcher m = SECONDS.matcher(value.get().trim());
                if (m.lookingAt()) {
                    double amount = Double.parseDouble(m.group(1));
                    return (long) Math.ceil("ms".equals(m.group(2)) ? amount : amount * 1000) + 250;
                }
                return -1;
            }
        }
        return status == 503 ? 2000 : -1;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException("Espera interrumpida.", ex);
        }
    }

    private HttpResponse<String> exchange(String json) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(config.baseUrl().replaceAll("/$", "") + "/chat/completions"))
            .timeout(Duration.ofSeconds(config.timeoutSeconds() > 0 ? config.timeoutSeconds() : 45))
            .header("Authorization", "Bearer " + config.apiKey())
            .header("Content-Type", "application/json")
            .header("User-Agent", USER_AGENT)
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            throw new AiUnavailableException(config.label() + ": no se pudo conectar (" + ex.getClass().getSimpleName() + ").", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException(config.label() + ": solicitud interrumpida.", ex);
        }
    }

    private String check(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status == 429) {
            throw new AiUnavailableException(config.label() + ": límite de uso del plan gratuito alcanzado.");
        }
        if (status >= 500) {
            throw new AiUnavailableException(config.label() + ": servicio saturado o no disponible (" + status + ").");
        }
        if (status >= 400) {
            throw new AiUnavailableException(config.label() + ": solicitud rechazada (" + status + ").");
        }
        return response.body();
    }

    String requestBody(AiPrompt prompt) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", config.model());
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", prompt.system());
        prompt.history().forEach(turn -> messages.addObject().put("role", turn.assistant() ? "assistant" : "user")
            .put("content", turn.content()));
        if (prompt.hasImage()) {
            ObjectNode user = messages.addObject().put("role", "user");
            ArrayNode content = user.putArray("content");
            content.addObject().put("type", "text").put("text", prompt.user());
            content.addObject().put("type", "image_url").putObject("image_url").put("url",
                "data:" + prompt.image().mimeType() + ";base64," + java.util.Base64.getEncoder().encodeToString(prompt.image().data()));
        } else {
            messages.addObject().put("role", "user").put("content", prompt.user());
        }
        if (prompt.expectsJson()) {
            ObjectNode format = body.putObject("response_format");
            format.put("type", "json_schema");
            ObjectNode schema = format.putObject("json_schema");
            schema.put("name", prompt.schemaName() == null ? "response" : prompt.schemaName());
            schema.put("strict", true);
            schema.set("schema", mapper.valueToTree(prompt.responseSchema()));
        }
        try {
            return mapper.writeValueAsString(body);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private AiAnswer parse(String raw) {
        try {
            JsonNode root = mapper.readTree(raw);
            JsonNode message = root.path("choices").path(0).path("message");
            String text = message.path("content").asText("").trim();
            if (text.isEmpty()) {
                String reason = root.path("choices").path(0).path("finish_reason").asText("desconocido");
                throw new AiUnavailableException(config.label() + ": respuesta vacía (" + reason + ").");
            }
            JsonNode usage = root.path("usage");
            String model = root.path("model").asText(config.model());
            return new AiAnswer(text, config.label(), model, usage.path("prompt_tokens").asLong(0),
                usage.path("completion_tokens").asLong(0), false);
        } catch (IOException ex) {
            throw new AiUnavailableException(config.label() + ": respuesta con formato inesperado.", ex);
        }
    }
}
