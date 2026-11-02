package com.aquasense.intelligence.engine;

import java.util.Arrays;
import java.util.List;

public final class StatisticsCalculator {

    private StatisticsCalculator() {
    }

    public static Statistics of(List<Sample> samples) {
        double[] v = samples.stream().mapToDouble(Sample::ph).toArray();
        int n = v.length;
        if (n == 0) {
            return new Statistics(0, 0, 0, 0, 0, 0);
        }
        double mean = Arrays.stream(v).average().orElse(0);
        double[] sorted = v.clone();
        Arrays.sort(sorted);
        double median = n % 2 == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;
        double variance = n > 1 ? Arrays.stream(v).map(x -> (x - mean) * (x - mean)).sum() / (n - 1) : 0;
        return new Statistics(n, mean, median, sorted[0], sorted[n - 1], Math.sqrt(variance));
    }
}
