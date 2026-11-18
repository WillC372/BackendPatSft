package com.aquaassistant.intelligence.dto;

import com.aquaassistant.intelligence.analysis.Forecast;

public record ForecastResponse(String profileName, double idealMin, double idealMax, Forecast forecast,
                               String outlook) {
}
