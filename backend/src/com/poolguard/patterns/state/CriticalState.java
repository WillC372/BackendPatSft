package com.poolguard.patterns.state;

import com.poolguard.model.SensorReading;
import com.poolguard.model.Thresholds;

/**
 * CRITICAL state: the water is contaminated / unsafe (e.g. sudden organic
 * contamination dropping pH). Urgent audio + visual + push alerts fire.
 */
public class CriticalState implements WaterState {

    @Override
    public String getName() {
        return "CRITICAL";
    }

    @Override
    public String getDisplayNameEs() {
        return "Crítica";
    }

    @Override
    public void onEnter(WaterStateMachine machine) {
        System.out.println("[State] Water quality is now CRITICAL! Immediate treatment required.");
    }

    @Override
    public void evaluate(WaterStateMachine machine, SensorReading reading) {
        Thresholds t = machine.getThresholds();
        if (StateRules.isCaution(reading, t)) {
            machine.setState(new CautionState());
        } else if (StateRules.isOptimal(reading, t)) {
            machine.setState(new OptimalState());
        }
        // otherwise: remains CRITICAL
    }
}
