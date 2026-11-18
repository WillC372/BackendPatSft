package com.aquaassistant.intelligence.dto;

import com.aquaassistant.intelligence.analysis.TrendResult;

public record TrendsResponse(int days, int maxDaysForPlan, String profileName, double idealMin, double idealMax,
                             TrendResult trend) {
}
