package com.aquasense.intelligence.dto;

import com.aquasense.intelligence.analysis.TrendResult;

public record TrendsResponse(int days, int maxDaysForPlan, String profileName, double idealMin, double idealMax,
                             TrendResult trend) {
}
