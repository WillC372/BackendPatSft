package com.poolguard.patterns.state;

import com.poolguard.model.SensorReading;
import com.poolguard.model.Thresholds;

/**
 * OPTIMAL state: pH and chlorine inside the safe band.
 * Any deviation moves the machine to CAUTION (or CRITICAL if severe).
 */
public class OptimalState implements WaterState {

    @Override
    public String getName() {
        return "OPTIMAL";
    }

    @Override
    public String getDisplayNameEs() {
        return "Óptima";
    }

    @Override
    public void onEnter(WaterStateMachine machine) {
        System.out.println("[State] Water quality is now OPTIMAL.");
    }

    @Override
    public void evaluate(WaterStateMachine machine, SensorReading reading) {
        Thresholds t = machine.getThresholds();
        if (StateRules.isCritical(reading, t)) {
            machine.setState(new CriticalState());
        } else if (StateRules.isCaution(reading, t)) {
            machine.setState(new CautionState());
        }
        // otherwise: remains OPTIMAL
    }
}
