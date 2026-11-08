package com.aquasense.device.hardware;

import java.time.Instant;

public record PhReading(double ph, Double temperatureC, Instant at) {
}
