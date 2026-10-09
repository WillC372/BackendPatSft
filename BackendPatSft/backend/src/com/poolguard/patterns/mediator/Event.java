package com.poolguard.patterns.mediator;

import java.util.HashMap;
import java.util.Map;

/**
 * A message published on the Event Bus. Carries a type, a key/value payload
 * and a creation timestamp.
 */
public final class Event {

    private final EventType type;
    private final Map<String, Object> payload = new HashMap<>();
    private final long timestamp = System.currentTimeMillis();

    public Event(EventType type) {
        this.type = type;
    }

    /** Fluent payload builder. */
    public Event with(String key, Object value) {
        payload.put(key, value);
        return this;
    }

    public EventType getType() {
        return type;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) payload.get(key);
    }

    public long getTimestamp() {
        return timestamp;
    }
}
