package com.aquasense.intelligence.ai;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Guards a streamed answer. The HTTP client stops waiting once the response headers arrive, so a model that sends
 * its headers and then nothing (or stops in the middle of an answer) would block the request forever. The watchdog
 * closes the stream, which ends the blocked read, when:
 * <ul>
 *   <li>no text has arrived one and a half times the first-response wait after the request started, or</li>
 *   <li>an answer that started has sent nothing for {@code stall}.</li>
 * </ul>
 * {@link #fired()} then says why, so the caller can try the next model or tell the user.
 */
final class StreamWatchdog {

    private static final ScheduledExecutorService CLOCK = Executors.newSingleThreadScheduledExecutor(
        Thread.ofPlatform().name("ai-stream-watchdog").daemon(true).factory());

    private final Closeable stream;
    private final long firstTextDeadline;
    private final long stallNanos;
    private final ScheduledFuture<?> check;
    private volatile long lastProgress = System.nanoTime();
    private volatile boolean textArrived;
    private volatile String fired;

    StreamWatchdog(Closeable stream, long startedNanos, Duration firstResponse, Duration stall) {
        this.stream = stream;
        this.firstTextDeadline = startedNanos + firstResponse.toNanos() * 3 / 2;
        this.stallNanos = stall.toNanos();
        long period = Math.max(50, Math.min(1000, Math.min(firstResponse.toMillis(), stall.toMillis()) / 4));
        this.check = CLOCK.scheduleAtFixedRate(this::check, period, period, TimeUnit.MILLISECONDS);
    }

    /** Something (any line) arrived. */
    void progress() {
        lastProgress = System.nanoTime();
    }

    /** Answer text arrived. */
    void text() {
        textArrived = true;
        progress();
    }

    /** Why the stream was closed, or {@code null} if it was not. */
    String fired() {
        return fired;
    }

    void stop() {
        check.cancel(false);
    }

    private void check() {
        long now = System.nanoTime();
        String reason = null;
        if (!textArrived && now > firstTextDeadline) {
            reason = "no empezó a escribir la respuesta a tiempo.";
        } else if (textArrived && now - lastProgress > stallNanos) {
            reason = "dejó de enviar la respuesta a mitad de camino.";
        }
        if (reason != null && fired == null) {
            fired = reason;
            stop();
            try {
                stream.close();
            } catch (IOException ignored) {
                // The read that was blocked ends with an exception either way.
            }
        }
    }
}
