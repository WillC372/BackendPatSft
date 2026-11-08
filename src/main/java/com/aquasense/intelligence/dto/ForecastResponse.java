package com.aquasense.intelligence.dto;

import com.aquasense.intelligence.analysis.Forecast;

public record ForecastResponse(String profileName, double idealMin, double idealMax, Forecast forecast,
                               String outlook) {
}
