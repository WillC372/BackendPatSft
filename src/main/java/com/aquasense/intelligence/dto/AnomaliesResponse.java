package com.aquasense.intelligence.dto;

import com.aquasense.intelligence.analysis.Anomaly;
import java.util.List;

public record AnomaliesResponse(int days, int analyzed, List<Anomaly> anomalies, String message) {
}
