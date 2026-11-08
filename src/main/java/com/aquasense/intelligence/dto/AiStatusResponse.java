package com.aquasense.intelligence.dto;

import java.util.List;

public record AiStatusResponse(boolean configured, String model, String plan, long usedToday, int dailyQuota,
                               int maxAnalysisDays, List<ProviderStatus> providers) {

    public record ProviderStatus(int order, String provider, String model, boolean configured) {
    }
}
