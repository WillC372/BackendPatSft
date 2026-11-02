package com.aquasense.intelligence.engine;

import java.util.List;

public record ForecastResult(boolean available, String reason, int horizonHours, List<ForecastPoint> points,
                             String method, String limitations) {

    public static ForecastResult unavailable(String reason) {
        return new ForecastResult(false, reason, 0, List.of(), "Regresión lineal por mínimos cuadrados", "");
    }
}
