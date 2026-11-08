package com.aquasense.measurement;

import com.aquasense.profile.WaterProfile;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Subject of the Observer pattern. Spring injects every bean that implements
 * {@link MeasurementListener}; adding a new observer does not require changing this class.
 */
@Component
public class MeasurementPublisher {

    private final List<MeasurementListener> listeners;

    public MeasurementPublisher(List<MeasurementListener> listeners) {
        this.listeners = List.copyOf(listeners);
    }

    public void publish(Measurement measurement, WaterProfile profile) {
        listeners.forEach(listener -> listener.onMeasurementRecorded(measurement, profile));
    }

    public int listenerCount() {
        return listeners.size();
    }
}
