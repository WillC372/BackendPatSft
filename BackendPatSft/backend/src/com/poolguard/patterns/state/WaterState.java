package com.poolguard.patterns.state;

import com.poolguard.model.SensorReading;

/**
 * STATE PATTERN
 *
 * Interface of the water quality states. Each state encapsulates the
 * behavior (alerts, UI reactions) of the pool while it remains in that
 * condition, and decides when a transition to another state occurs.
 *
 * States: OPTIMAL -> CAUTION -> CRITICAL (and back).
 */
public interface WaterState {

    /** English identifier: OPTIMAL | CAUTION | CRITICAL */
    String getName();

    /** Spanish display name for the UI: Óptima | Precaución | Crítica */
    String getDisplayNameEs();

    /** Called when the machine enters this state. */
    void onEnter(WaterStateMachine machine);

    /** Evaluates a new reading and triggers transitions when needed. */
    void evaluate(WaterStateMachine machine, SensorReading reading);
}
