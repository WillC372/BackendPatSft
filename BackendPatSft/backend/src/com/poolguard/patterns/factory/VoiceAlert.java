package com.poolguard.patterns.factory;

import com.poolguard.patterns.mediator.Event;
import com.poolguard.patterns.mediator.EventBus;
import com.poolguard.patterns.mediator.EventType;

/**
 * Voice alert in Spanish: emits a VOICE_ALERT event on the bus so the UI
 * can synthesize the message with the browser SpeechSynthesis API (es-ES).
 */
public class VoiceAlert implements Alert {

    private final EventBus eventBus;

    public VoiceAlert(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public String getType() {
        return "VOICE";
    }

    @Override
    public void trigger(String messageEs, String severity) {
        System.out.println("[VOICE][" + severity + "] " + messageEs);
        eventBus.publish(new Event(EventType.VOICE_ALERT)
                .with("message", messageEs)
                .with("severity", severity));
    }
}
