package com.aquasense.intelligence.deep;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Sliding windows for the network: 24 input hours and 12 hours to predict. The network predicts the pH change
 * from the last hour (scaled), so "no change" is the same as the persistence baseline.
 * It also computes the three simple baselines the network is compared with.
 */
public final class ForecastDataset {

    public static final int WINDOW = 24;
    public static final int HORIZON = 12;
    public static final String[] FEATURES = {"pH escalado", "Cambio frente a la hora anterior", "Hora del día (seno)",
        "Hora del día (coseno)", "Dato medido (1) o interpolado (0)"};

    private ForecastDataset() {
    }

    /** One window: the input points and, if they exist, the next 12 real values. */
    public record Window(List<HourlySeries.Point> input, double[] future) {

        public double last() {
            return input.get(input.size() - 1).ph();
        }
    }

    public record Scaling(double mean, double scale) {

        static Scaling fit(List<Window> windows) {
            double sum = 0;
            double sq = 0;
            int n = 0;
            for (Window w : windows) {
                for (HourlySeries.Point p : w.input()) {
                    sum += p.ph();
                    sq += p.ph() * p.ph();
                    n++;
                }
            }
            double mean = n == 0 ? 7 : sum / n;
            double variance = n == 0 ? 0 : Math.max(0, sq / n - mean * mean);
            return new Scaling(mean, Math.max(0.05, Math.sqrt(variance)));
        }
    }

    public static List<Window> windows(List<List<HourlySeries.Point>> segments) {
        List<Window> windows = new ArrayList<>();
        for (List<HourlySeries.Point> s : segments) {
            for (int t = WINDOW - 1; t + HORIZON < s.size(); t++) {
                double[] future = new double[HORIZON];
                for (int k = 1; k <= HORIZON; k++) {
                    future[k - 1] = s.get(t + k).ph();
                }
                windows.add(new Window(List.copyOf(s.subList(t - WINDOW + 1, t + 1)), future));
            }
        }
        return windows;
    }

    public static double[][] features(List<HourlySeries.Point> input, Scaling scaling) {
        double[][] x = new double[input.size()][FEATURES.length];
        for (int t = 0; t < input.size(); t++) {
            HourlySeries.Point p = input.get(t);
            double hour = p.hour().atZone(ZoneOffset.UTC).getHour();
            x[t][0] = (p.ph() - scaling.mean()) / scaling.scale();
            x[t][1] = t == 0 ? 0 : (p.ph() - input.get(t - 1).ph()) / scaling.scale();
            x[t][2] = Math.sin(2 * Math.PI * hour / 24);
            x[t][3] = Math.cos(2 * Math.PI * hour / 24);
            x[t][4] = p.observed() ? 1 : 0;
        }
        return x;
    }

    public static double[] target(Window w, Scaling scaling) {
        double[] y = new double[HORIZON];
        for (int k = 0; k < HORIZON; k++) {
            y[k] = (w.future()[k] - w.last()) / scaling.scale();
        }
        return y;
    }

    /** Turns the network output (scaled changes) into pH values. */
    public static double[] toPh(double[] output, double last, Scaling scaling) {
        double[] ph = new double[output.length];
        for (int k = 0; k < output.length; k++) {
            ph[k] = last + output[k] * scaling.scale();
        }
        return ph;
    }

    /** Persistence: the pH stays as in the last hour. */
    public static double[] persistence(Window w) {
        double[] p = new double[HORIZON];
        java.util.Arrays.fill(p, w.last());
        return p;
    }

    /** Daily seasonal: the pH of every future hour repeats the one of the same hour on the previous day. */
    public static double[] seasonal(Window w) {
        double[] p = new double[HORIZON];
        for (int k = 1; k <= HORIZON; k++) {
            p[k - 1] = w.input().get(k - 1).ph();
        }
        return p;
    }

    /** Linear trend: least-squares line over the 24 input hours, extrapolated. */
    public static double[] linear(Window w) {
        int n = w.input().size();
        double mx = (n - 1) / 2.0;
        double my = w.input().stream().mapToDouble(HourlySeries.Point::ph).average().orElse(w.last());
        double num = 0;
        double den = 0;
        for (int t = 0; t < n; t++) {
            num += (t - mx) * (w.input().get(t).ph() - my);
            den += (t - mx) * (t - mx);
        }
        double slope = den == 0 ? 0 : num / den;
        double[] p = new double[HORIZON];
        for (int k = 1; k <= HORIZON; k++) {
            p[k - 1] = my + slope * (n - 1 + k - mx);
        }
        return p;
    }
}
