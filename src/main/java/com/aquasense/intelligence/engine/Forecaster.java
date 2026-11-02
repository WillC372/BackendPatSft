package com.aquasense.intelligence.engine;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class Forecaster {

    public static final int MIN_POINTS = 8;
    public static final Duration MIN_SPAN = Duration.ofHours(48);
    public static final int MAX_HORIZON_HOURS = 72;
    public static final int MIN_HORIZON_HOURS = 6;
    private static final double[] T_95 = {0, 0, 0, 0, 0, 0, 2.447, 2.365, 2.306, 2.262, 2.228, 2.201, 2.179, 2.160,
            2.145, 2.131, 2.120, 2.110, 2.101, 2.093, 2.086, 2.080, 2.074, 2.069, 2.064, 2.060, 2.056, 2.052, 2.048,
            2.045, 2.042};

    private Forecaster() {
    }

    public static ForecastResult forecast(List<Sample> samples) {
        if (samples.size() < MIN_POINTS) {
            return ForecastResult.unavailable("No hay datos suficientes para predecir: se requieren al menos "
                    + MIN_POINTS + " mediciones y hay " + samples.size() + ".");
        }
        Instant start = samples.get(0).at();
        Instant end = samples.get(samples.size() - 1).at();
        Duration span = Duration.between(start, end);
        if (span.compareTo(MIN_SPAN) < 0) {
            return ForecastResult.unavailable("Las mediciones abarcan menos de 48 horas; no es posible estimar una "
                    + "evolución confiable.");
        }
        LinearFit fit = LinearFit.of(samples);
        if (fit.rSquared() < TrendAnalyzer.MIN_R_SQUARED) {
            return ForecastResult.unavailable("El comportamiento histórico no sigue un patrón lineal claro (R² = "
                    + String.format(java.util.Locale.US, "%.2f", fit.rSquared()) + "); una predicción no tendría "
                    + "fundamento.");
        }
        int horizon = (int) Math.min(MAX_HORIZON_HOURS, span.toHours() / 2);
        if (horizon < MIN_HORIZON_HOURS) {
            return ForecastResult.unavailable("El horizonte posible es demasiado corto para ser útil.");
        }
        double t = tCritical(samples.size() - 2);
        List<ForecastPoint> points = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Instant at = end.plus(Duration.ofHours(Math.round(horizon * i / 3.0)));
            double predicted = fit.predict(at);
            double half = fit.predictionHalfWidth(at, t);
            points.add(new ForecastPoint(at, clamp(predicted), clamp(predicted - half), clamp(predicted + half)));
        }
        return new ForecastResult(true, "Proyección disponible.", horizon, points,
                "Regresión lineal por mínimos cuadrados con intervalo de predicción del 95 %",
                "Supone que el comportamiento reciente continúa. No considera intervenciones, clima, uso ni "
                        + "recalibraciones del sensor. Es una estimación, no una certeza.");
    }

    private static double tCritical(int degreesOfFreedom) {
        if (degreesOfFreedom >= T_95.length) {
            return 1.96;
        }
        return T_95[degreesOfFreedom];
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(14, value));
    }
}
