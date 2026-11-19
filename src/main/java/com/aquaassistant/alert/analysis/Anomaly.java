package com.aquaassistant.alert.analysis;

import java.time.Instant;

public record Anomaly(String type, Severity severity, String title, String message, Long measurementId,
                      Instant measuredAt, double ph) {

    public enum Severity {
        CAUTION, CRITICAL
    }
}
