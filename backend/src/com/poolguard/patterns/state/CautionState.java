package com.poolguard.patterns.state;

import com.poolguard.model.SensorReading;
import com.poolguard.model.Thresholds;

/**
 * CAUTION state: a parameter left the optimal band but is not yet dangerous.
 * The app reacts with visual warnings; a further drift leads to CRITICAL.
 */
public class CautionState implements WaterState {

    @Override
    public String getName() {
        return "CAUTION";
    }

    @Override
    public String getDisplayNameEs() {
        return "Precaución";
    }

    @Override
    public void onEnter(WaterStateMachine machine) {
        System.out.println("[State] Water quality is now in CAUTION.");
    }

    @Override
    public void evaluate(WaterStateMachine machine, SensorReading reading) {
        Thresholds t = machine.getThresholds();
        if (StateRules.isCritical(reading, t)) {
            machine.setState(new CriticalState());
        } else if (StateRules.isOptimal(reading, t)) {
            machine.setState(new OptimalState());
        }
        // otherwise: remains CAUTION
    }
}
