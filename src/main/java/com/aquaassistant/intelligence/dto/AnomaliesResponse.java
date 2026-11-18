package com.aquaassistant.intelligence.dto;

import com.aquaassistant.intelligence.analysis.Anomaly;
import java.util.List;

public record AnomaliesResponse(int days, int analyzed, List<Anomaly> anomalies, String message) {
}
