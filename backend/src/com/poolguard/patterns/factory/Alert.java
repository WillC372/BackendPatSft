package com.poolguard.patterns.factory;

/**
 * FACTORY METHOD PATTERN - Product interface.
 *
 * Every kind of alert implements this contract. The concrete behavior
 * (push notification, Spanish voice synthesis, visual banner) is decided
 * by the AlertFactory at runtime.
 */
public interface Alert {

    /** Alert type identifier: PUSH | VOICE | VISUAL */
    String getType();

    /** Fires the alert with a Spanish message and a severity level. */
    void trigger(String messageEs, String severity);
}
