package com.aquaassistant.i18n;

import com.aquaassistant.intelligence.ai.AiAnswer;
import com.aquaassistant.intelligence.ai.AiPrompt;
import com.aquaassistant.intelligence.ai.FallbackAiProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Translates the Spanish texts of the interface (and of emails, notices and reports) into the language each person
 * chose. Every text is translated once per language with the AI and stored, so after the first time it is immediate
 * and free. Texts may carry markers like {@code <0>…</0>} or {@code <1/>} for the bold words, links and icons inside
 * a sentence; the translation must keep them, otherwise it is discarded.
 */
@Service
public class TranslationService {

    private static final Logger LOG = LoggerFactory.getLogger(TranslationService.class);

    public static final int MAX_TEXT = 1500;
    static final int CHUNK_ITEMS = 60;
    static final int CHUNK_CHARS = 7000;
    /** AI calls for translations at the same time: the free plans of the providers reject bursts. */
    private static final int PARALLEL_CALLS = 3;
    private static final Pattern LETTER = Pattern.compile("\\p{L}");
    private static final Pattern MARKER = Pattern.compile("</?\\d{1,2}/?>|\\{\\d{1,2}\\}");
    private static final Duration WAIT = Duration.ofSeconds(110);

    private static final Map<String, Object> SCHEMA = Map.of(
        "type", "object",
        "properties", Map.of("t", Map.of("type", "array", "items", Map.of("type", "string"))),
        "required", List.of("t"));

    private final UiTranslationRepository repository;
    private final FallbackAiProvider ai;
    private final ObjectMapper mapper;
    private final TranslationBudget budget;
    /** Language -> (Spanish text -> translation), loaded from the database the first time a language is used. */
    private final Map<String, Map<String, String>> dictionaries = new ConcurrentHashMap<>();
    /** Texts being translated right now, so two people opening the same screen do not pay for it twice. */
    private final Map<String, CompletableFuture<String>> inFlight = new ConcurrentHashMap<>();
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final java.util.concurrent.Semaphore calls = new java.util.concurrent.Semaphore(PARALLEL_CALLS, true);

    public TranslationService(UiTranslationRepository repository, FallbackAiProvider ai, ObjectMapper mapper,
                              TranslationBudget budget) {
        this.repository = repository;
        this.ai = ai;
        this.mapper = mapper;
        this.budget = budget;
    }

    /** Everything already translated into {@code lang}: the app loads it at once when that language is chosen. */
    public Map<String, String> dictionary(String lang) {
        return Map.copyOf(dictionaryOf(lang));
    }

    /**
     * Translations of {@code texts} in the same order. An item is null when it could not be translated now (the AI did
     * not answer, or the budget of new texts of {@code who} is used up); the caller shows the Spanish text meanwhile.
     */
    public List<String> translate(String lang, List<String> texts, String who) {
        List<String> result = new ArrayList<>(texts.size());
        if (Languages.isOriginal(lang)) {
            result.addAll(texts);
            return result;
        }
        Map<String, String> dictionary = dictionaryOf(lang);
        Map<String, CompletableFuture<String>> waiting = new LinkedHashMap<>();
        List<String> mine = new ArrayList<>();
        for (String text : texts) {
            if (text == null || text.isBlank() || text.length() > MAX_TEXT || !LETTER.matcher(text).find()) {
                continue;
            }
            if (dictionary.containsKey(text) || waiting.containsKey(text)) {
                continue;
            }
            CompletableFuture<String> future = new CompletableFuture<>();
            CompletableFuture<String> other = inFlight.putIfAbsent(key(lang, text), future);
            if (other == null) {
                mine.add(text);
                waiting.put(text, future);
            } else {
                waiting.put(text, other);
            }
        }
        int allowed = budget.take(who, mine.size());
        List<String> sent = mine.subList(0, allowed);
        for (String refused : mine.subList(allowed, mine.size())) {
            finish(lang, refused, null);
        }
        if (!sent.isEmpty()) {
            translateNew(lang, List.copyOf(sent), dictionary);
        }
        for (String text : texts) {
            if (text == null || text.isBlank() || text.length() > MAX_TEXT || !LETTER.matcher(text).find()) {
                result.add(text);
                continue;
            }
            String known = dictionary.get(text);
            if (known == null && waiting.containsKey(text)) {
                try {
                    known = waiting.get(text).get(WAIT.toSeconds(), TimeUnit.SECONDS);
                } catch (Exception ex) {
                    known = null;
                }
            }
            result.add(known);
        }
        return result;
    }

    /** One text, for the server's own messages: falls back to Spanish when it cannot be translated now. */
    public String translateOne(String lang, String text) {
        return translateAll(lang, List.of(text)).get(0);
    }

    /**
     * Several texts for the server's own messages (emails, notices, reports); untranslated items keep the Spanish text.
     * Numbers travel as {0}, {1}, like in the app, so "Mediciones: 4" and "Mediciones: 9" share one translation.
     */
    public List<String> translateAll(String lang, List<String> texts) {
        List<String> keys = new ArrayList<>(texts.size());
        List<List<String>> numbers = new ArrayList<>(texts.size());
        for (String text : texts) {
            List<String> nums = new ArrayList<>();
            keys.add(text == null ? null : withNumberMarkers(text, nums));
            numbers.add(nums);
        }
        List<String> out = translate(lang, keys, TranslationBudget.SERVER);
        List<String> safe = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            String translated = out.get(i);
            safe.add(translated == null ? texts.get(i) : fillNumbers(translated, numbers.get(i)));
        }
        return safe;
    }

    private static final Pattern PROTECTED = Pattern.compile("</?\\d{1,2}/?>|\\{\\d{1,2}\\}");
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,:/]\\d+)*");

    /** "Mediciones: 4. Promedio: 7.65" -> "Mediciones: {0}. Promedio: {1}" (markers like <0> are left alone). */
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

    private void translateNew(String lang, List<String> texts, Map<String, String> dictionary) {
        List<List<String>> chunks = new ArrayList<>();
        List<String> chunk = new ArrayList<>();
        int chars = 0;
        for (String text : texts) {
            if (!chunk.isEmpty() && (chunk.size() >= CHUNK_ITEMS || chars + text.length() > CHUNK_CHARS)) {
                chunks.add(chunk);
                chunk = new ArrayList<>();
                chars = 0;
            }
            chunk.add(text);
            chars += text.length();
        }
        chunks.add(chunk);
        List<Future<?>> jobs = new ArrayList<>();
        for (List<String> part : chunks) {
            jobs.add(workers.submit(() -> translateChunk(lang, part, dictionary)));
        }
        for (Future<?> job : jobs) {
            try {
                job.get(WAIT.toSeconds(), TimeUnit.SECONDS);
            } catch (Exception ex) {
                LOG.warn("Translation into {} did not finish: {}", lang, ex.getMessage());
            }
        }
        texts.forEach(text -> finish(lang, text, dictionary.get(text)));
    }

    private void translateChunk(String lang, List<String> texts, Map<String, String> dictionary) {
        List<String> translated;
        try {
            calls.acquire();
            try {
                translated = ask(lang, texts);
            } finally {
                calls.release();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return;
        } catch (RuntimeException ex) {
            LOG.warn("The AI could not translate {} texts into {}: {}", texts.size(), lang, ex.getMessage());
            return;
        }
        for (int i = 0; i < texts.size(); i++) {
            String source = texts.get(i);
            String target = i < translated.size() ? translated.get(i) : null;
            if (target == null || target.isBlank() || !sameMarkers(source, target) || target.length() > MAX_TEXT * 3) {
                continue;
            }
            dictionary.put(source, target);
            store(lang, source, target);
        }
    }

    List<String> ask(String lang, List<String> texts) {
        String system = """
            You translate the user interface of "Aqua Assistant AI", an app to measure and look after the pH of water \
            (swimming pools, aquariums, ponds, drinking water, irrigation, hydroponics, pH meters and their calibration).
            Translate every Spanish string of the JSON array into %s.
            Rules:
            - Answer only with a JSON object {"t": [...]} with exactly %d strings, in the same order.
            - Keep the markers like <0>, </0> and <1/> exactly as written, around the words they wrap in your translation.
            - {0}, {1}... stand for numbers: keep each one exactly once, where the number goes in your language.
            - Do not translate: numbers, units (pH, mg/L, ppm, °C, mL, L, m³, gal), e-mail addresses, web addresses, \
            the names "Aqua Assistant AI", "Aqua", "Premium", "USGS", "ESP32", product codes, and names of people or places.
            - If a string is not in Spanish, or is only a name, return it unchanged.
            - Use a friendly, simple tone, short like a phone app, and speak to the person informally when the language \
            has that form. Use the quotation marks that are usual in that language.
            """.formatted(Languages.englishName(lang) + " (" + lang + ")", texts.size());
        String user;
        try {
            user = mapper.writeValueAsString(texts);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        AiAnswer answer = ai.complete(new AiPrompt(null, "TRANSLATION", system, user, "translations", SCHEMA));
        try {
            JsonNode items = mapper.readTree(answer.text()).path("t");
            List<String> out = new ArrayList<>();
            items.forEach(item -> out.add(item.isTextual() ? item.asText() : null));
            if (out.size() != texts.size()) {
                throw new IllegalStateException("la IA devolvió " + out.size() + " textos de " + texts.size());
            }
            return out;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("respuesta de la IA ilegible", ex);
        }
    }

    static boolean sameMarkers(String source, String target) {
        return markers(source).equals(markers(target));
    }

    private static Map<String, Integer> markers(String text) {
        Map<String, Integer> found = new HashMap<>();
        Matcher m = MARKER.matcher(text);
        while (m.find()) {
            found.merge(m.group(), 1, Integer::sum);
        }
        return found;
    }

    private void store(String lang, String source, String target) {
        try {
            repository.saveAndFlush(new UiTranslation(key(lang, source), lang, source, target));
        } catch (DataIntegrityViolationException ex) {
            // Someone else stored the same text a moment ago.
        } catch (RuntimeException ex) {
            LOG.warn("Translation not stored: {}", ex.getMessage());
        }
    }

    private void finish(String lang, String text, String translation) {
        CompletableFuture<String> future = inFlight.remove(key(lang, text));
        if (future != null) {
            future.complete(translation);
        }
    }

    private Map<String, String> dictionaryOf(String lang) {
        return dictionaries.computeIfAbsent(lang, l -> {
            Map<String, String> loaded = new ConcurrentHashMap<>();
            repository.findByLang(l).forEach(t -> loaded.put(t.getSourceText(), t.getTargetText()));
            LOG.info("Loaded {} translations into {}.", loaded.size(), l);
            return loaded;
        });
    }

    static String key(String lang, String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return lang + ":" + HexFormat.of().formatHex(hash, 0, 20);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Tests only. */
    void forget() {
        dictionaries.clear();
    }
}
