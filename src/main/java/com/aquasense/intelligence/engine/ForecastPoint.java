package com.aquasense.intelligence.engine;

import java.time.Instant;

public record ForecastPoint(Instant at, double predicted, double lower, double upper) {
}
