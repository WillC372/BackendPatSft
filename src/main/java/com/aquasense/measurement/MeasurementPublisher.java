package com.aquasense.measurement;

import com.aquasense.profile.WaterProfile;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Sujeto del patron Observer. Spring inyecta todos los beans que implementan
 * {@link MeasurementListener}; agregar un nuevo observador no requiere modificar esta clase.
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
