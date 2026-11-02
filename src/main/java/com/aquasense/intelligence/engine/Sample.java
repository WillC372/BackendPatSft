package com.aquasense.intelligence.engine;

import java.time.Instant;

public record Sample(Instant at, double ph, boolean simulated) {
}
