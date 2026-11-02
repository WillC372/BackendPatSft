package com.poolguard.patterns.state;

import com.poolguard.model.SensorReading;
import com.poolguard.model.Thresholds;

/**
 * STATE PATTERN - Context.
 *
 * Holds the current water quality state and delegates the behavior to the
 * active WaterState object. Every sensor reading is fed into update(), which
 * may automatically change the state (and therefore the app's behavior).
 */
public class WaterStateMachine {

    private WaterState state;
    private final Thresholds thresholds;

    public WaterStateMachine(Thresholds thresholds) {
        this.thresholds = thresholds;
        this.state = new OptimalState();
    }

    /** Feeds a new reading; the current state decides if a transition occurs. */
    public void update(SensorReading reading) {
        state.evaluate(this, reading);
    }

    /** Performs the state transition and runs the entry behavior. */
    public void setState(WaterState newState) {
        if (newState.getName().equals(this.state.getName())) {
            return;
        }
        this.state = newState;
        state.onEnter(this);
    }

    public WaterState getState() {
        return state;
    }

    public Thresholds getThresholds() {
        return thresholds;
    }
}
