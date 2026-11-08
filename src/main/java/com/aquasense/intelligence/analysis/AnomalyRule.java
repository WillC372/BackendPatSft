package com.aquasense.intelligence.analysis;

import com.aquasense.measurement.Measurement;
import com.aquasense.profile.WaterProfile;
import java.util.List;
import java.util.Optional;

/**
 * Strategy: cada regla de detección es un algoritmo intercambiable.
 * El detector las aplica todas sin conocer su implementacion.
 */
public interface AnomalyRule {

    Optional<Anomaly> evaluate(Measurement current, List<Measurement> previous, WaterProfile profile);
}
