package com.aquaassistant.config;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Optional: keeps the free Render instance awake. Render stops a free web service after 15 minutes without incoming
 * requests; with KEEP_AWAKE=true the server asks its own public address for the liveness probe every 10 minutes (the
 * request goes out through the internet and comes back in through Render, so it counts as traffic).
 *
 * <p>Off by default: the 750 free hours a month of Render are shared by the backend and the AI service, and a backend
 * awake all month uses about 730 of them. Asleep, the first visit waits about a minute ("Activando el servidor...")
 * and the GitHub workflow WakeUp.yml wakes it every 3 hours to send due reminders and import the USGS data.
 */
@Component
public class KeepAwake {

    private static final Logger log = LoggerFactory.getLogger(KeepAwake.class);
    private static final String PATH = "/actuator/health/liveness";

    private final String target;
    private final HttpClient http;
    private int failuresInARow;

    @Autowired
    public KeepAwake(@Value("${aquasense.keep-awake.enabled:${KEEP_AWAKE:false}}") boolean enabled,
                     @Value("${aquasense.keep-awake.url:${RENDER_EXTERNAL_URL:}}") String baseUrl) {
        this(enabled ? baseUrl : null, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build());
    }

    KeepAwake(String baseUrl, HttpClient http) {
        this.target = baseUrl == null || baseUrl.isBlank() ? null : baseUrl.replaceAll("/+$", "") + PATH;
        this.http = http;
        if (target != null) {
            log.info("Keep-awake enabled: {} every 10 minutes.", target);
        }
    }

    public boolean enabled() {
        return target != null;
    }

    String target() {
        return target;
    }

    @Scheduled(initialDelayString = "${aquasense.keep-awake.initial-delay-ms:120000}",
        fixedDelayString = "${aquasense.keep-awake.interval-ms:600000}")
    public void ping() {
        if (target == null) {
            return;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(target)).timeout(Duration.ofSeconds(30))
                .header("User-Agent", "AquaAssistant-AI/1.0 keep-awake").GET().build();
            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status >= 400) {
                warn("answered " + status);
            } else {
                failuresInARow = 0;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            warn(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void warn(String reason) {
        failuresInARow++;
        // One line per problem streak is enough; the next ping tries again.
        if (failuresInARow == 1 || failuresInARow % 6 == 0) {
            log.warn("Keep-awake request to {} failed ({} in a row): {}", target, failuresInARow, reason);
        }
    }
}
