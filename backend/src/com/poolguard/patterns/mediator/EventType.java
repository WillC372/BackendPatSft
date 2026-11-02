package com.poolguard.patterns.mediator;

/**
 * Types of events flowing through the Event Bus.
 */
public enum EventType {
    SENSOR_DATA,
    STATE_CHANGED,
    ALERT_TRIGGERED,
    VOICE_ALERT,
    THRESHOLD_UPDATED,
    MAINTENANCE_REGISTERED,
    PREDICTION_UPDATED
}
