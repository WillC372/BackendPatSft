package com.poolguard.patterns.factory;

import com.poolguard.patterns.mediator.EventBus;

/**
 * FACTORY METHOD PATTERN
 *
 * Creates alert instances dynamically (PUSH / VOICE / VISUAL) without
 * exposing the concrete classes to the client code.
 */
public class AlertFactory {

    public enum AlertType {
        PUSH, VOICE, VISUAL
    }

    private final EventBus eventBus;

    public AlertFactory(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    /** Creates the alert instance for the requested type. */
    public Alert createAlert(AlertType type) {
        switch (type) {
            case PUSH:
                return new PushAlert();
            case VOICE:
                return new VoiceAlert(eventBus);
            case VISUAL:
                return new VisualAlert();
            default:
                throw new IllegalArgumentException("Unknown alert type: " + type);
        }
    }
}
