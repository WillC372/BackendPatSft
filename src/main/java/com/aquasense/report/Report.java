package com.aquasense.report;

import java.time.Instant;
import java.util.List;

public record Report(String title, Instant generatedAt, Instant periodStart, Instant periodEnd, String profileName,
                     int measurementCount, boolean containsSimulatedData, List<ReportSection> sections,
                     String disclaimer) {
}
