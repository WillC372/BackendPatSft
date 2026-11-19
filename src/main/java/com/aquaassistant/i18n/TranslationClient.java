package com.aquaassistant.i18n;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The backend's own texts (emails, push notices) in the person's language. The translations live in the shared
 * table ui_translations, which the AI service fills; what is already there is read directly, and only new texts are
 * asked to the AI service (internal key). If it does not answer in time (it sleeps when nobody uses it), the text
 * goes in Spanish this time and is translated for the next one.
 */
@Component
public class TranslationClient {

    private static final Logger LOG = LoggerFactory.getLogger(TranslationClient.class);
    private static final Pattern PROTECTED = Pattern.compile("</?\\d{1,2}/?>|\\{\\d{1,2}\\}");
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,:/]\\d+)*");
    private static final Pattern LETTER = Pattern.compile("\\p{L}");

    private final UiTranslationRepository repository;
    private final ObjectMapper mapper;
    private final String aiUrl;
    private final String key;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public TranslationClient(UiTranslationRepository repository, ObjectMapper mapper,
                             @Value("${aquasense.ai-service.url:}") String aiUrl, @Value("${aquasense.internal-key:}") String key) {
        this.repository = repository;
        this.mapper = mapper;
        this.aiUrl = aiUrl == null ? "" : aiUrl.replaceAll("/+$", "");
        this.key = key;
    }

    /** The texts in {@code lang}, in the same order; an item that cannot be translated now stays in Spanish. */
    public List<String> translateAll(String lang, List<String> texts) {
        if (Languages.isOriginal(lang) || texts.isEmpty()) {
            return texts;
        }
        List<String> keys = new ArrayList<>(texts.size());
        List<List<String>> numbers = new ArrayList<>(texts.size());
        for (String text : texts) {
            List<String> nums = new ArrayList<>();
            keys.add(text == null ? null : withNumberMarkers(text, nums));
            numbers.add(nums);
        }
        Map<String, String> known = new HashMap<>();
        List<String> ids = keys.stream().filter(k -> k != null && LETTER.matcher(k).find()).map(k -> key(lang, k)).distinct().toList();
        repository.findAllById(ids).forEach(t -> known.put(t.getSourceText(), t.getTargetText()));
        List<String> missing = keys.stream().filter(k -> k != null && LETTER.matcher(k).find() && !known.containsKey(k)).distinct().toList();
        if (!missing.isEmpty()) {
            known.putAll(ask(lang, missing));
        }
        List<String> out = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            String translated = keys.get(i) == null ? null : known.get(keys.get(i));
            out.add(translated == null ? texts.get(i) : fillNumbers(translated, numbers.get(i)));
        }
        return out;
    }

    private Map<String, String> ask(String lang, List<String> texts) {
        Map<String, String> out = new HashMap<>();
        if (aiUrl.isBlank() || key == null || key.isBlank()) {
            return out;
        }
        try {
            String body = mapper.writeValueAsString(Map.of("lang", lang, "texts", texts.subList(0, Math.min(texts.size(), 80))));
            HttpRequest request = HttpRequest.newBuilder(URI.create(aiUrl + "/api/v1/internal/translate"))
                .timeout(Duration.ofSeconds(25)).header("Content-Type", "application/json").header("X-Internal-Key", key)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode list = mapper.readTree(response.body()).path("translations");
                for (int i = 0; i < texts.size() && i < list.size(); i++) {
                    if (list.get(i).isTextual() && !list.get(i).asText().equals(texts.get(i))) {
                        out.put(texts.get(i), list.get(i).asText());
                    }
                }
            } else {
                LOG.warn("The AI service answered {} to a translation.", response.statusCode());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            LOG.warn("The AI service did not translate now ({}); the text goes in Spanish.", ex.getMessage());
        }
        return out;
    }

    /** "Mediciones: 4. Promedio: 7.65" -> "Mediciones: {0}. Promedio: {1}" (the same keys the AI service stores). */
    static String withNumberMarkers(String text, List<String> numbers) {
        if (PROTECTED.matcher(text).find()) {
            return text;
        }
        Matcher m = NUMBER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            numbers.add(m.group());
            m.appendReplacement(out, Matcher.quoteReplacement("{" + (numbers.size() - 1) + "}"));
        }
        m.appendTail(out);
        return out.toString();
    }

    static String fillNumbers(String text, List<String> numbers) {
        Matcher m = Pattern.compile("\\{(\\d{1,2})\\}").matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            int i = Integer.parseInt(m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(i < numbers.size() ? numbers.get(i) : m.group()));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** The id of a translation in ui_translations: the language and the start of the text's SHA-256 (as the AI service). */
    static String key(String lang, String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return lang + ":" + HexFormat.of().formatHex(hash, 0, 20);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
