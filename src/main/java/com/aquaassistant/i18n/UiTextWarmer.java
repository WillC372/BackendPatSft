package com.aquaassistant.i18n;

import com.aquaassistant.intelligence.ai.FallbackAiProvider;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps every text of the interface translated in advance into each language, so changing the language is immediate
 * and no screen waits for the AI. The texts come from i18n/ui-texts.txt (extracted from the app's code with
 * scripts/extract-ui-texts.mjs). It starts a little after the server does and again every hour, and only sends what
 * is still missing, slowly (small batches with a pause), so the per-minute limits of the free AI plans are respected.
 */
@Component
public class UiTextWarmer {

    private static final Logger LOG = LoggerFactory.getLogger(UiTextWarmer.class);
    static final int BATCH = 40;
    /** Batches in a row that came back empty (the AI is out of quota): the run stops and is tried again later. */
    static final int GIVE_UP_AFTER = 3;

    private final TranslationService translations;
    private final FallbackAiProvider ai;
    private final boolean enabled;
    private final long pauseMs;
    private final AtomicBoolean running = new AtomicBoolean();

    public UiTextWarmer(TranslationService translations, FallbackAiProvider ai,
                        @Value("${aquasense.i18n.warm-enabled:true}") boolean enabled,
                        @Value("${aquasense.i18n.warm-pause-ms:12000}") long pauseMs) {
        this.translations = translations;
        this.ai = ai;
        this.enabled = enabled;
        this.pauseMs = pauseMs;
    }

    static List<String> texts() {
        try (InputStream in = UiTextWarmer.class.getClassLoader().getResourceAsStream("i18n/ui-texts.txt")) {
            if (in == null) {
                return List.of();
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().map(String::strip)
                .filter(line -> !line.isEmpty()).toList();
        } catch (IOException ex) {
            return List.of();
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStart() {
        if (enabled) {
            Thread.ofVirtual().name("ui-text-warmer").start(() -> {
                sleep(90_000);
                warm();
            });
        }
    }

    @Scheduled(initialDelay = 3_600_000L, fixedDelay = 3_600_000L)
    public void again() {
        if (enabled) {
            warm();
        }
    }

    /**
     * Translates what is missing, one batch per language in turn so every language advances at the same pace; returns
     * how many texts it translated. When the AI keeps answering nothing (no quota left today) it stops until the next run.
     */
    public int warm() {
        if (!ai.isAvailable() || !running.compareAndSet(false, true)) {
            return 0;
        }
        int done = 0;
        try {
            List<String> all = texts();
            Map<String, List<String>> missing = new java.util.LinkedHashMap<>();
            for (String lang : Languages.codes()) {
                if (Languages.isOriginal(lang)) {
                    continue;
                }
                Map<String, String> known = translations.dictionary(lang);
                List<String> left = all.stream().filter(t -> !known.containsKey(t)).toList();
                if (!left.isEmpty()) {
                    missing.put(lang, left);
                }
            }
            if (missing.isEmpty()) {
                return 0;
            }
            LOG.info("Translating interface texts in advance: {}", missing.entrySet().stream()
                .map(e -> e.getKey() + " " + e.getValue().size()).toList());
            int empty = 0;
            for (int from = 0; !missing.isEmpty() && empty < GIVE_UP_AFTER; from += BATCH) {
                final int start = from;
                missing.entrySet().removeIf(e -> start >= e.getValue().size());
                for (Map.Entry<String, List<String>> entry : missing.entrySet()) {
                    if (!translations.aiAvailable(true)) {
                        LOG.info("Translating in advance waits: today's share of the AI is used (Aqua's answers come first).");
                        empty = GIVE_UP_AFTER;
                        break;
                    }
                    List<String> batch = entry.getValue().subList(start, Math.min(start + BATCH, entry.getValue().size()));
                    int ok = (int) translations.translate(entry.getKey(), batch, TranslationBudget.SERVER).stream()
                        .filter(java.util.Objects::nonNull).count();
                    done += ok;
                    empty = ok == 0 ? empty + 1 : 0;
                    if (empty >= GIVE_UP_AFTER) {
                        LOG.warn("The AI did not translate the last {} batches (no quota left?); trying again in an hour.", GIVE_UP_AFTER);
                        break;
                    }
                    sleep(pauseMs);
                }
            }
            LOG.info("{} interface texts translated in advance in this run.", done);
        } catch (RuntimeException ex) {
            LOG.warn("Translating in advance stopped: {}", ex.getMessage());
        } finally {
            running.set(false);
        }
        return done;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
