package com.aquasense.intelligence.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.aquasense.config.AppProperties;
import java.time.Duration;
import java.util.stream.Collectors;

/**
 * Real provider: Claude API (Anthropic) with the official Java SDK.
 * The key is read from ANTHROPIC_API_KEY on the server and never reaches the frontend.
 */
public class ClaudeTextProvider implements AiTextProvider {

    private static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

    private final AppProperties.Ai config;
    private final AnthropicClient client;

    public ClaudeTextProvider(AppProperties properties) {
        this.config = properties.ai();
        this.client = config.isConfigured()
            ? AnthropicOkHttpClient.builder().apiKey(config.apiKey()).timeout(Duration.ofSeconds(60)).maxRetries(2).build()
            : null;
    }

    @Override
    public boolean isAvailable() {
        return client != null;
    }

    @Override
    public String providerName() {
        return "Anthropic Claude";
    }

    @Override
    public String modelName() {
        return config.model();
    }

    @Override
    public AiAnswer complete(AiPrompt prompt) {
        if (client == null) {
            throw new AiUnavailableException("La IA generativa no está configurada en el servidor (ANTHROPIC_API_KEY).");
        }
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
            .model(config.model())
            .maxTokens(config.maxTokens())
            .system(prompt.system());
        prompt.history().forEach(turn -> {
            if (turn.assistant()) {
                builder.addAssistantMessage(turn.content());
            } else {
                builder.addUserMessage(turn.content());
            }
        });
        MessageCreateParams params = builder
            .addUserMessage(prompt.user())
            .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.of(config.effort())).build())
            .addBeta(FALLBACK_BETA)
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            .build();
        try {
            BetaMessage response = client.beta().messages().create(params);
            if (response.stopReason().map(BetaStopReason.REFUSAL::equals).orElse(false)) {
                throw new AiUnavailableException("El modelo no pudo responder esta consulta. Reformúlala sobre la calidad del agua.");
            }
            String text = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(textBlock -> textBlock.text())
                .collect(Collectors.joining("\n")).trim();
            if (text.isEmpty()) {
                throw new AiUnavailableException("El modelo devolvió una respuesta vacía.");
            }
            return new AiAnswer(text, providerName(), response.model().asString(), response.usage().inputTokens(),
                response.usage().outputTokens(), false);
        } catch (RateLimitException ex) {
            throw new AiUnavailableException("El proveedor de IA esta saturado. Intenta en unos minutos.", ex);
        } catch (AnthropicServiceException ex) {
            throw new AiUnavailableException("El proveedor de IA respondió con un error (" + ex.statusCode() + ").", ex);
        } catch (AnthropicException ex) {
            throw new AiUnavailableException("No se pudo contactar al proveedor de IA.", ex);
        }
    }
}
