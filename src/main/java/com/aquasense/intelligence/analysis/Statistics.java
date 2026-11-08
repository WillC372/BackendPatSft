package com.aquasense.intelligence.analysis;

import java.util.Arrays;

/**
 * Pure statistical functions used by the AquaSense Intelligence algorithms.
 */
public final class Statistics {

    private Statistics() {
    }

    public static double mean(double[] values) {
        return Arrays.stream(values).average().orElse(Double.NaN);
    }

    public static double stdDev(double[] values) {
        if (values.length < 2) {
            return 0;
        }
        double mean = mean(values);
        double sum = Arrays.stream(values).map(v -> (v - mean) * (v - mean)).sum();
        return Math.sqrt(sum / (values.length - 1));
    }

    public static double median(double[] values) {
        if (values.length == 0) {
            return Double.NaN;
        }
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int middle = sorted.length / 2;
        return sorted.length % 2 == 0 ? (sorted[middle - 1] + sorted[middle]) / 2 : sorted[middle];
    }

    public static double medianAbsoluteDeviation(double[] values) {
        double median = median(values);
        return median(Arrays.stream(values).map(v -> Math.abs(v - median)).toArray());
    }

    public static Regression linearRegression(double[] x, double[] y) {
        int n = x.length;
        double meanX = mean(x);
        double meanY = mean(y);
        double sxx = 0;
        double sxy = 0;
        double syy = 0;
        for (int i = 0; i < n; i++) {
            sxx += (x[i] - meanX) * (x[i] - meanX);
            sxy += (x[i] - meanX) * (y[i] - meanY);
            syy += (y[i] - meanY) * (y[i] - meanY);
        }
        double slope = sxx == 0 ? 0 : sxy / sxx;
        double intercept = meanY - slope * meanX;
        double sse = 0;
        for (int i = 0; i < n; i++) {
            double residual = y[i] - (intercept + slope * x[i]);
            sse += residual * residual;
        }
        double residualStd = n > 2 ? Math.sqrt(sse / (n - 2)) : 0;
        double r2 = syy == 0 ? 1 : 1 - sse / syy;
        return new Regression(slope, intercept, r2, residualStd, meanX, sxx, n);
    }

    public static double studentT975(int degreesOfFreedom) {
        int[] df = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 20, 25, 30, 40, 60, 120};
        double[] t = {12.706, 4.303, 3.182, 2.776, 2.571, 2.447, 2.365, 2.306, 2.262, 2.228, 2.179, 2.131, 2.086,
            2.060, 2.042, 2.021, 2.000, 1.980};
        for (int i = 0; i < df.length; i++) {
            if (degreesOfFreedom <= df[i]) {
                return t[i];
            }
        }
        return 1.96;
    }

    public record Regression(double slope, double intercept, double r2, double residualStd, double meanX, double sxx, int n) {

        public double predict(double x) {
            return intercept + slope * x;
        }

        public double predictionHalfWidth(double x) {
            if (n < 3) {
                return Double.NaN;
            }
            double leverage = sxx == 0 ? 0 : (x - meanX) * (x - meanX) / sxx;
            return studentT975(n - 2) * residualStd * Math.sqrt(1 + 1.0 / n + leverage);
        }
    }
}
