package com.poolguard.patterns.state;

import com.poolguard.model.SensorReading;
import com.poolguard.model.Thresholds;

/**
 * Shared classification rules used by the concrete states to decide
 * transitions, based on the configurable thresholds.
 */
final class StateRules {

    private StateRules() {
    }

    static boolean isCritical(SensorReading r, Thresholds t) {
        return r.getPh() < t.getPhCriticalMin() || r.getPh() > t.getPhCriticalMax()
                || r.getChlorinePpm() < t.getChlorineCriticalMin() || r.getChlorinePpm() > t.getChlorineCriticalMax();
    }

    static boolean isCaution(SensorReading r, Thresholds t) {
        return r.getPh() < t.getPhCautionMin() || r.getPh() > t.getPhCautionMax()
                || r.getChlorinePpm() < t.getChlorineCautionMin() || r.getChlorinePpm() > t.getChlorineCautionMax();
    }

    static boolean isOptimal(SensorReading r, Thresholds t) {
        return r.getPh() >= t.getPhOptimalMin() && r.getPh() <= t.getPhOptimalMax()
                && r.getChlorinePpm() >= t.getChlorineOptimalMin() && r.getChlorinePpm() <= t.getChlorineOptimalMax();
    }
}
