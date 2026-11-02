package com.poolguard.patterns.mediator;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * MEDIATOR / EVENT BUS PATTERN
 *
 * Centralizes and decouples the communication between the Bluetooth module,
 * the AI engine and the User Interface. Components never talk to each other
 * directly: they publish events and subscribe to the types they care about.
 */
public final class EventBus {

    private final Map<EventType, List<Consumer<Event>>> subscribers = new ConcurrentHashMap<>();

    /** Subscribes a handler to an event type. */
    public void subscribe(EventType type, Consumer<Event> handler) {
        subscribers.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    /** Publishes an event to every subscriber of its type. */
    public void publish(Event event) {
        List<Consumer<Event>> handlers = subscribers.get(event.getType());
        if (handlers == null) {
            return;
        }
        for (Consumer<Event> handler : handlers) {
            try {
                handler.accept(event);
            } catch (Exception e) {
                System.err.println("EventBus handler error: " + e.getMessage());
            }
        }
    }
}
