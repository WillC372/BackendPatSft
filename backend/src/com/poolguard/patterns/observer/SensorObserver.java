package com.poolguard.patterns.observer;

/**
 * OBSERVER PATTERN
 *
 * Implemented by any component that must react to new sensor readings
 * (monitoring service, UI bridge, analytics engine...).
 */
public interface SensorObserver {

    void onSensorData(SensorData data);
}
