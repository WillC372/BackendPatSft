package com.aquasense.vision;

import java.util.List;

public record PhotoReadingResponse(String pendingId, boolean readable, String kind, String kindLabel, Double ph,
                                   Double phLow, Double phHigh, double confidence, boolean referenceChartVisible,
                                   Double temperatureC, List<Issue> issues, String observations,
                                   List<Opinion> opinions, String agreement, String agreementLabel,
                                   List<String> adjustments, String note, String disclaimer) {

    public record Issue(String code, String label) {
    }

    public record Opinion(String provider, String model, boolean readable, Double ph, String kind) {
    }
}
