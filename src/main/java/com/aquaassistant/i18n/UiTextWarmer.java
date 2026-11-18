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
 * scripts/extract-ui-texts.mjs). It starts a little after the server does and again every few hours, and only sends
 * what is still missing, slowly, so the free plans of the AI providers are not exceeded.
 */
@Component
public class UiTextWarmer {

    private static final Logger LOG = LoggerFactory.getLogger(UiTextWarmer.class);
    static final int BATCH = 60;

    private final TranslationService translations;
    private final FallbackAiProvider ai;
    private final boolean enabled;
    private final long pauseMs;
    private final AtomicBoolean running = new AtomicBoolean();

    public UiTextWarmer(TranslationService translations, FallbackAiProvider ai,
                        @Value("${aquasense.i18n.warm-enabled:true}") boolean enabled,
                        @Value("${aquasense.i18n.warm-pause-ms:4000}") long pauseMs) {
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

    @Scheduled(initialDelay = 6 * 3_600_000L, fixedDelay = 6 * 3_600_000L)
    public void again() {
        if (enabled) {
            warm();
        }
    }

    /** Translates what is missing in every language; returns how many texts it translated. */
    public int warm() {
        if (!ai.isAvailable() || !running.compareAndSet(false, true)) {
            return 0;
        }
        int done = 0;
        try {
            List<String> all = texts();
            for (String lang : Languages.codes()) {
                if (Languages.isOriginal(lang)) {
                    continue;
                }
                Map<String, String> known = translations.dictionary(lang);
                List<String> missing = new ArrayList<>(all.stream().filter(t -> !known.containsKey(t)).toList());
                if (missing.isEmpty()) {
                    continue;
                }
                LOG.info("Translating {} interface texts into {} in advance.", missing.size(), lang);
                int before = done;
                for (int i = 0; i < missing.size(); i += BATCH) {
                    List<String> batch = missing.subList(i, Math.min(i + BATCH, missing.size()));
                    done += (int) translations.translate(lang, batch, TranslationBudget.SERVER).stream()
                        .filter(java.util.Objects::nonNull).count();
                    sleep(pauseMs);
                }
                LOG.info("{}: {} of {} texts translated in advance.", lang, done - before, missing.size());
            }
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
