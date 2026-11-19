package com.aquaassistant.alert.analysis;

import com.aquaassistant.measurement.Measurement;
import com.aquaassistant.profile.WaterProfile;
import java.util.List;
import java.util.Optional;

/**
 * Strategy: every detection rule is an interchangeable algorithm.
 * The detector applies all of them without knowing their implementation.
 */
public interface AnomalyRule {

    Optional<Anomaly> evaluate(Measurement current, List<Measurement> previous, WaterProfile profile);
}
