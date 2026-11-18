package com.aquaassistant.intelligence.dto;

import com.aquaassistant.intelligence.advice.Advice;
import com.aquaassistant.measurement.dto.MeasurementResponse;
import java.util.List;

public record OverviewResponse(boolean hasData, MeasurementResponse latest, String profileName, String profileRange,
                               List<CheckItem> checks, Advice advice, int anomalies7d, String trendLabel,
                               String disclaimer) {

    public record CheckItem(String label, boolean ok) {
    }
}
