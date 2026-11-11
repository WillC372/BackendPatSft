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
    public boolean isFast() {
        return config.fast();
    }

    /**
     * Streaming ("stream": true): the API sends server-sent events with fragments of the answer; each fragment is handed
     * to {@code onDelta} as soon as it arrives. Structured (JSON) and image requests use the normal call.
     */
    @Override
    public AiAnswer stream(AiPrompt prompt, java.util.function.Consumer<String> onDelta) {
        if (!isAvailable()) {
            throw new AiUnavailableException(config.label() + " no está configurado.");
        }
        if (prompt.expectsJson() || prompt.hasImage()) {
            AiAnswer answer = complete(prompt);
            onDelta.accept(answer.text());
            return answer;
        }
        String json;
        try {
            ObjectNode body = (ObjectNode) mapper.readTree(requestBody(prompt));
            body.put("stream", true);
            body.putObject("stream_options").put("include_usage", true);
            json = mapper.writeValueAsString(body);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        long started = System.nanoTime();
        HttpResponse<java.io.InputStream> response;
        try {
            Duration wait = streamFirstResponse.compareTo(timeout()) < 0 ? streamFirstResponse : timeout();
            response = http.send(request(json, wait), HttpResponse.BodyHandlers.ofInputStream());
        } catch (java.net.http.HttpTimeoutException ex) {
            throw new AiUnavailableException(config.label() + ": no empezó a responder en " + wait(streamFirstResponse) + " s.", ex);
        } catch (IOException ex) {
            throw new AiUnavailableException(config.label() + ": no se pudo conectar (" + ex.getClass().getSimpleName() + ").", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException(config.label() + ": solicitud interrumpida.", ex);
        }
        int status = response.statusCode();
        if (status >= 400) {
            closeQuietly(response.body());
            throw new AiUnavailableException(config.label() + (status == 429 ? ": límite de uso del plan gratuito alcanzado."
                : status >= 500 ? ": servicio saturado o no disponible (" + status + ")." : ": solicitud rechazada (" + status + ")."));
        }
        StringBuilder text = new StringBuilder();
        long[] tokens = new long[2];
        String[] model = {config.model()};
        // The HTTP client has no read timeout once the headers arrived: a watchdog closes the stream if no text arrives
        // in time (the next model is then tried) or if an answer that started stops sending anything.
        StreamWatchdog watchdog = new StreamWatchdog(response.body(), started, streamFirstResponse, streamStall);
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(response.body(), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if (data.isEmpty() || "[DONE]".equals(data)) {
                    continue;
                }
                JsonNode node;
                try {
                    node = mapper.readTree(data);
                } catch (IOException ex) {
                    throw new AiUnavailableException(config.label() + ": respuesta con formato inesperado.", ex);
                }
                String delta = node.path("choices").path(0).path("delta").path("content").asText("");
                if (!delta.isEmpty()) {
                    watchdog.text();
                    text.append(delta);
                    onDelta.accept(delta);
                }
                JsonNode usage = node.path("usage");
                if (usage.isObject()) {
                    tokens[0] = usage.path("prompt_tokens").asLong(tokens[0]);
                    tokens[1] = usage.path("completion_tokens").asLong(tokens[1]);
                }
                model[0] = node.path("model").asText(model[0]);
            }
        } catch (IOException ex) {
            if (watchdog.fired() != null) {
                throw new AiUnavailableException(config.label() + ": " + watchdog.fired(), ex);
            }
            throw new AiUnavailableException(config.label() + ": se cortó la conexión (" + ex.getClass().getSimpleName() + ").", ex);
        } finally {
            watchdog.stop();
        }
        if (watchdog.fired() != null) {
            throw new AiUnavailableException(config.label() + ": " + watchdog.fired());
        }
        if (text.toString().isBlank()) {
            throw new AiUnavailableException(config.label() + ": respuesta vacía.");
        }
        return new AiAnswer(text.toString().trim(), config.label(), model[0], tokens[0], tokens[1], false);
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

    /**
     * Longest wait for a streamed answer to start. A model that takes longer is skipped and the next one is tried, so
     * the user does not wait for the full timeout of a slow model; an answer that already started is not cut.
     */
    static final Duration STREAM_FIRST_RESPONSE = Duration.ofSeconds(8);
    /**
     * Longest pause in the text of an answer that already started. Measured in production, normal pauses stay under
     * 1.2 s while a frozen model pauses 14 s or more; after this, the next model writes the answer again.
     */
    static final Duration STREAM_STALL = Duration.ofSeconds(6);
    private Duration streamFirstResponse = STREAM_FIRST_RESPONSE;
    private Duration streamStall = STREAM_STALL;

    /** Tests only: shorter waits for the start of a streamed answer and for a silence in the middle of it. */
    OpenAiCompatibleProvider withStreamFirstResponse(Duration wait) {
        this.streamFirstResponse = wait;
        return this;
    }

    OpenAiCompatibleProvider withStreamStall(Duration stall) {
        this.streamStall = stall;
        return this;
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ignored) {
            // Already closed.
        }
    }

    private static long wait(Duration d) {
        return Math.max(1, d.toSeconds());
    }

    private Duration timeout() {
        return Duration.ofSeconds(config.timeoutSeconds() > 0 ? config.timeoutSeconds() : 45);
    }

    private HttpRequest request(String json) {
        return request(json, timeout());
    }

    private HttpRequest request(String json, Duration timeout) {
        return HttpRequest.newBuilder(URI.create(config.baseUrl().replaceAll("/$", "") + "/chat/completions"))
            .timeout(timeout)
            .header("Authorization", "Bearer " + config.apiKey())
            .header("Content-Type", "application/json")
            .header("User-Agent", USER_AGENT)
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
    }

    private HttpResponse<String> exchange(String json) {
        try {
            return http.send(request(json), HttpResponse.BodyHandlers.ofString());
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
        if (config.reasoningEffort() != null && !config.reasoningEffort().isBlank()) {
            body.put("reasoning_effort", config.reasoningEffort());
        }
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
