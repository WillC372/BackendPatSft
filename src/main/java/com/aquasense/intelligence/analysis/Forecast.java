package com.aquasense.intelligence.analysis;

import java.time.Instant;
import java.util.List;

public record Forecast(boolean available, String reason, String method, int basedOn, double spanDays,
                       int horizonDays, double slopePerDay, double r2, List<Point> points, List<String> limitations) {

    public record Point(Instant at, double predicted, double lower, double upper) {
    }
}
