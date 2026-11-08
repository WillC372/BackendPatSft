package com.aquasense.intelligence.ml;

import com.aquasense.intelligence.analysis.Statistics;
import java.util.Arrays;

/**
 * Robust scaling (median and interquartile range): every variable ends up in comparable units
 * and extreme values do not distort the scale. It also allows explaining how far a value is from the typical one.
 */
public final class RobustScaler {

    private final double[] center;
    private final double[] scale;

    private RobustScaler(double[] center, double[] scale) {
        this.center = center;
        this.scale = scale;
    }

    public static RobustScaler fit(double[][] data) {
        int features = data[0].length;
        double[] center = new double[features];
        double[] scale = new double[features];
        for (int f = 0; f < features; f++) {
            final int column = f;
            double[] values = Arrays.stream(data).mapToDouble(row -> row[column]).sorted().toArray();
            center[f] = Statistics.median(values);
            double iqr = quantile(values, 0.75) - quantile(values, 0.25);
            if (iqr <= 1e-9) {
                iqr = 1.4826 * Statistics.medianAbsoluteDeviation(values);
            }
            scale[f] = iqr <= 1e-9 ? 1 : iqr;
        }
        return new RobustScaler(center, scale);
    }

    public double[] transform(double[] row) {
        double[] out = new double[row.length];
        for (int f = 0; f < row.length; f++) {
            out[f] = (row[f] - center[f]) / scale[f];
        }
        return out;
    }

    public double center(int feature) {
        return center[feature];
    }

    static double quantile(double[] sorted, double q) {
        if (sorted.length == 1) {
            return sorted[0];
        }
        double position = q * (sorted.length - 1);
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        return sorted[lower] + (sorted[upper] - sorted[lower]) * (position - lower);
    }
}
