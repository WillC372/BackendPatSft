package com.aquasense.intelligence.ml;

import java.time.Instant;
import java.util.List;

public record MlInsightsResponse(boolean trained, String reason, boolean includeSimulated, int trainingSize,
                                 double trainingSpanDays, List<String> features, int treeCount, int sampleSize,
                                 double threshold, double contamination, Instant trainedAt, long trainingMillis,
                                 int periodDays, List<Point> points, List<Finding> anomalies, List<HourBucket> hourlyPattern,
                                 String method) {

    public record Point(Long id, Instant measuredAt, double ph, double score, boolean anomalous, String source) {
    }

    public record Finding(Long id, Instant measuredAt, double ph, double score, String source,
                          List<UserAnomalyModel.Factor> factors) {
    }

    public record HourBucket(int hour, Double averagePh, int count) {
    }
}
