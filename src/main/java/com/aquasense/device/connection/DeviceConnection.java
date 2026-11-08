package com.aquasense.device.connection;

import com.aquasense.config.AppProperties;
import com.aquasense.device.hardware.CalibrationPoint;
import com.aquasense.device.hardware.DeviceException;
import com.aquasense.device.hardware.PhMeter;
import com.aquasense.device.hardware.PhReading;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Contexto del patron State. Representa el único puerto físico que controla este backend.
 * Un hilo programado produce lecturas en vivo y las entrega a los observadores registrados.
 */
@Component
public class DeviceConnection {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceConnection.class);
    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    private static final long SAMPLE_GAP_MS = 150;

    private final ReentrantLock lock = new ReentrantLock();
    private final List<DeviceListener> listeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "aquasense-device-reader");
        thread.setDaemon(true);
        return thread;
    });
    private final long sampleIntervalMs;

    private volatile ConnectionState state = new DisconnectedState();
    private PhMeter meter;
    private ConnectionSession session;
    private Instant connectedAt;
    private String lastError;
    private ScheduledFuture<?> liveTask;
    private int consecutiveFailures;

    public DeviceConnection(AppProperties properties) {
        this.sampleIntervalMs = properties.device().sampleIntervalMs();
    }

    public void addListener(DeviceListener listener) {
        listeners.add(listener);
    }

    public void connect(PhMeter newMeter, ConnectionSession newSession) {
        withLock(() -> state.connect(this, newMeter, newSession));
    }

    public void disconnect() {
        withLock(() -> state.disconnect(this));
    }

    public PhReading measure(int samples) {
        lock.lock();
        try {
            return state.measure(this, samples);
        } finally {
            lock.unlock();
        }
    }

    public void startLive() {
        withLock(() -> state.startLive(this));
    }

    public void stopLive() {
        withLock(() -> state.stopLive(this));
    }

    public String calibrate(CalibrationPoint point, double buffer) {
        lock.lock();
        try {
            return state.calibrate(this, point, buffer);
        } finally {
            lock.unlock();
        }
    }

    public DeviceSnapshot snapshot() {
        lock.lock();
        try {
            return new DeviceSnapshot(state.id(), session == null ? null : session.ownerId(),
                session == null ? null : session.deviceId(), session == null ? null : session.deviceName(),
                meter == null ? null : meter.port(), meter == null ? null : meter.protocol(),
                meter != null && meter.isSimulated(), meter != null && meter.supportsCalibration(), connectedAt, lastError);
        } finally {
            lock.unlock();
        }
    }

    public DeviceState currentState() {
        return state.id();
    }

    void openMeter(PhMeter newMeter, ConnectionSession newSession) {
        try {
            newMeter.open();
        } catch (DeviceException ex) {
            lastError = ex.getMessage();
            throw ex;
        }
        this.meter = newMeter;
        this.session = newSession;
        this.connectedAt = Instant.now();
        this.lastError = null;
        this.consecutiveFailures = 0;
    }

    void closeMeter() {
        if (meter != null) {
            try {
                meter.close();
            } catch (RuntimeException ex) {
                LOG.warn("Error while closing meter on {}", meter.port(), ex);
            }
        }
        meter = null;
        session = null;
        connectedAt = null;
    }

    PhReading readStable(int samples) {
        List<PhReading> readings = new ArrayList<>();
        for (int i = 0; i < Math.max(1, samples); i++) {
            readings.add(readOrFail());
            if (i < samples - 1) {
                sleep(SAMPLE_GAP_MS);
            }
        }
        readings.sort((a, b) -> Double.compare(a.ph(), b.ph()));
        PhReading median = readings.get(readings.size() / 2);
        return new PhReading(median.ph(), median.temperatureC(), Instant.now());
    }

    String calibrateMeter(CalibrationPoint point, double buffer) {
        return meter.calibrate(point, buffer);
    }

    void scheduleLive() {
        consecutiveFailures = 0;
        liveTask = scheduler.scheduleAtFixedRate(this::produceLiveReading, 0, sampleIntervalMs, TimeUnit.MILLISECONDS);
    }

    void cancelLive() {
        if (liveTask != null) {
            liveTask.cancel(false);
            liveTask = null;
        }
    }

    void transitionTo(ConnectionState next) {
        this.state = next;
        DeviceSnapshot snapshot = snapshot();
        listeners.forEach(listener -> safely(() -> listener.onStateChanged(snapshot)));
    }

    private void produceLiveReading() {
        if (!lock.tryLock()) {
            return;
        }
        try {
            if (state.id() != DeviceState.LIVE || meter == null) {
                return;
            }
            PhReading reading = meter.read();
            consecutiveFailures = 0;
            ConnectionSession current = session;
            boolean simulated = meter.isSimulated();
            listeners.forEach(listener -> safely(() -> listener.onReading(current, reading, simulated)));
        } catch (DeviceException ex) {
            consecutiveFailures++;
            LOG.warn("Live reading failed ({}/{}): {}", consecutiveFailures, MAX_CONSECUTIVE_FAILURES, ex.getMessage());
            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                lastError = ex.getMessage();
                cancelLive();
                transitionTo(new ErrorState());
            }
        } finally {
            lock.unlock();
        }
    }

    private PhReading readOrFail() {
        try {
            return meter.read();
        } catch (DeviceException ex) {
            lastError = ex.getMessage();
            cancelLive();
            transitionTo(new ErrorState());
            throw ex;
        }
    }

    private void withLock(Runnable action) {
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }

    private static void safely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            LOG.warn("Device listener failed", ex);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new DeviceException("Lectura interrumpida.", ex);
        }
    }

    @PreDestroy
    public void shutdown() {
        cancelLive();
        scheduler.shutdownNow();
        lock.lock();
        try {
            closeMeter();
        } finally {
            lock.unlock();
        }
    }
}
