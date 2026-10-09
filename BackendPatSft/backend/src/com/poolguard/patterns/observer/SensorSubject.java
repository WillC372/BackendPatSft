package com.poolguard.patterns.observer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * OBSERVER PATTERN - Subject.
 *
 * Decouples the data source (Bluetooth meter / simulator) from its consumers.
 * Observers register themselves and are notified on every new reading.
 */
public class SensorSubject {

    private final List<SensorObserver> observers = new CopyOnWriteArrayList<>();

    public void attach(SensorObserver observer) {
        observers.add(observer);
    }

    public void detach(SensorObserver observer) {
        observers.remove(observer);
    }

    /** Broadcasts a new sensor packet to every registered observer. */
    public void notifyObservers(SensorData data) {
        for (SensorObserver observer : observers) {
            try {
                observer.onSensorData(data);
            } catch (Exception e) {
                System.err.println("Observer error: " + e.getMessage());
            }
        }
    }
}
