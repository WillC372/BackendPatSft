package com.aquasense.device;

import com.aquasense.device.connection.ConnectionSession;
import com.aquasense.device.connection.DeviceConnection;
import com.aquasense.device.connection.DeviceListener;
import com.aquasense.device.connection.DeviceSnapshot;
import com.aquasense.device.hardware.PhReading;
import com.aquasense.profile.Classification;
import com.aquasense.profile.PhClassifier;
import com.aquasense.profile.ProfileService;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Observer que convierte las lecturas en vivo en eventos Server-Sent Events para el frontend.
 * Solo el usuario que conecto el medidor recibe sus lecturas; todos reciben los cambios de estado.
 */
@Component
public class LiveFeed implements DeviceListener {

    private static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L;

    static final int MAX_STREAMS_PER_USER = 5;
    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final PhClassifier classifier;
    private final ProfileService profileService;

    public LiveFeed(DeviceConnection connection, PhClassifier classifier, ProfileService profileService) {
        this.classifier = classifier;
        this.profileService = profileService;
        connection.addListener(this);
    }

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        List<SseEmitter> mine = emitters.computeIfAbsent(userId, id -> new CopyOnWriteArrayList<>());
        // Cada pestaña abre una conexión; se limita por usuario para que nadie agote los hilos del servidor.
        while (mine.size() >= MAX_STREAMS_PER_USER) {
            SseEmitter oldest = mine.remove(0);
            oldest.complete();
        }
        mine.add(emitter);
        Runnable remove = () -> emitters.getOrDefault(userId, List.of()).remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(error -> remove.run());
        send(userId, emitter, "hello", Map.of("at", Instant.now().toString()));
        return emitter;
    }

    @Override
    public void onReading(ConnectionSession session, PhReading reading, boolean simulated) {
        if (session == null || !emitters.containsKey(session.ownerId())) {
            return;
        }
        Classification c = classifier.classify(reading.ph(), profileService.activeProfile(session.ownerId()));
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("ph", reading.ph());
        payload.put("temperatureC", reading.temperatureC());
        payload.put("at", reading.at().toString());
        payload.put("simulated", simulated);
        payload.put("nature", c.nature().name());
        payload.put("natureLabel", c.nature().label());
        payload.put("status", c.status().name());
        payload.put("statusLabel", c.status().label());
        payload.put("deviceName", session.deviceName());
        broadcast(session.ownerId(), "reading", payload);
    }

    @Override
    public void onStateChanged(DeviceSnapshot snapshot) {
        Map<String, Object> payload = Map.of("state", snapshot.state().name(), "stateLabel", snapshot.state().label());
        emitters.keySet().forEach(userId -> broadcast(userId, "status", payload));
    }

    @Scheduled(fixedRate = 20000)
    public void heartbeat() {
        emitters.keySet().forEach(userId -> broadcast(userId, "ping", Map.of("at", Instant.now().toString())));
    }

    private void broadcast(Long userId, String event, Object payload) {
        for (SseEmitter emitter : emitters.getOrDefault(userId, List.of())) {
            send(userId, emitter, event, payload);
        }
    }

    private void send(Long userId, SseEmitter emitter, String event, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(event).data(payload));
        } catch (IOException | IllegalStateException ex) {
            emitters.getOrDefault(userId, List.of()).remove(emitter);
        }
    }
}
