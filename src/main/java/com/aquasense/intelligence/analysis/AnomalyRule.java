package com.aquasense.intelligence.analysis;

import com.aquasense.measurement.Measurement;
import com.aquasense.profile.WaterProfile;
import java.util.List;
import java.util.Optional;

/**
 * Strategy: every detection rule is an interchangeable algorithm.
 * The detector applies all of them without knowing their implementation.
 */
public interface AnomalyRule {

    Optional<Anomaly> evaluate(Measurement current, List<Measurement> previous, WaterProfile profile);
}
