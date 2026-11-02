package com.aquasense.intelligence.engine;

import java.time.Instant;
import java.util.List;

public record LinearFit(double slopePerDay, double intercept, double rSquared, double residualStdError, int n,
                        double meanX, double sxx, Instant origin) {

    private static final double MILLIS_PER_DAY = 86_400_000.0;

    public static LinearFit of(List<Sample> samples) {
        int n = samples.size();
        Instant origin = samples.get(0).at();
        double[] x = new double[n];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = (samples.get(i).at().toEpochMilli() - origin.toEpochMilli()) / MILLIS_PER_DAY;
            y[i] = samples.get(i).ph();
        }
        double meanX = 0;
        double meanY = 0;
        for (int i = 0; i < n; i++) {
            meanX += x[i];
            meanY += y[i];
        }
        meanX /= n;
        meanY /= n;
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
            double e = y[i] - (intercept + slope * x[i]);
            sse += e * e;
        }
        double r2 = syy == 0 ? 0 : 1 - sse / syy;
        double se = n > 2 ? Math.sqrt(sse / (n - 2)) : 0;
        return new LinearFit(slope, intercept, Math.max(0, r2), se, n, meanX, sxx, origin);
    }

    public double predict(Instant at) {
        double x = (at.toEpochMilli() - origin.toEpochMilli()) / MILLIS_PER_DAY;
        return intercept + slopePerDay * x;
    }

    public double predictionHalfWidth(Instant at, double tCritical) {
        double x = (at.toEpochMilli() - origin.toEpochMilli()) / MILLIS_PER_DAY;
        double factor = Math.sqrt(1 + 1.0 / n + (sxx == 0 ? 0 : (x - meanX) * (x - meanX) / sxx));
        return tCritical * residualStdError * factor;
    }
}
