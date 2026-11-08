package com.aquasense.intelligence.analysis;

import com.aquasense.measurement.Measurement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Predicción por regresión lineal de mínimos cuadrados con intervalo de predicción del 95 %.
 * Se niega a predecir si no hay suficientes datos o si el horizonte supera lo observado.
 */
@Component
public class Forecaster {

    static final int MIN_POINTS = 10;
    static final double MIN_SPAN_DAYS = 2.0;
    static final int MAX_HORIZON_DAYS = 7;
    private static final double SECONDS_PER_DAY = 86400.0;
    private static final String METHOD = "Regresión lineal (mínimos cuadrados) con intervalo de predicción del 95 %";

    public Forecast forecast(List<Measurement> chronological, int requestedHorizon, Instant now) {
        List<String> limitations = List.of(
            "Supone que la tendencia reciente continúa igual; no anticipa eventos como lluvia, dosificación o cambios de agua.",
            "La incertidumbre crece con el horizonte y con la dispersión de tus mediciones.",
            "Es una estimación estadística, no una medición. Confirma siempre con el medidor.");
        int n = chronological.size();
        if (n < MIN_POINTS) {
            return unavailable("Se necesitan al menos " + MIN_POINTS + " mediciones; hay " + n + ".", n, 0, limitations);
        }
        Instant origin = chronological.get(0).getMeasuredAt();
        double span = Duration.between(origin, chronological.get(n - 1).getMeasuredAt()).toSeconds() / SECONDS_PER_DAY;
        if (span < MIN_SPAN_DAYS) {
            return unavailable(String.format(java.util.Locale.US,
                "Las mediciones cubren %.1f días; se necesitan al menos %.0f días de historial.", span, MIN_SPAN_DAYS),
                n, span, limitations);
        }
        int horizon = Math.max(1, Math.min(Math.min(requestedHorizon, MAX_HORIZON_DAYS), (int) Math.floor(span)));
        double[] x = chronological.stream()
            .mapToDouble(m -> Duration.between(origin, m.getMeasuredAt()).toSeconds() / SECONDS_PER_DAY).toArray();
        double[] y = chronological.stream().mapToDouble(Measurement::getPh).toArray();
        Statistics.Regression regression = Statistics.linearRegression(x, y);
        double nowX = Duration.between(origin, now).toSeconds() / SECONDS_PER_DAY;
        List<Forecast.Point> points = new ArrayList<>();
        for (int day = 1; day <= horizon; day++) {
            double target = nowX + day;
            double predicted = clamp(regression.predict(target));
            double half = regression.predictionHalfWidth(target);
            points.add(new Forecast.Point(now.plus(Duration.ofDays(day)), round(predicted),
                round(clamp(predicted - half)), round(clamp(predicted + half))));
        }
        return new Forecast(true, null, METHOD, n, round(span), horizon, Math.round(regression.slope() * 1000) / 1000.0,
            round(regression.r2()), points, limitations);
    }

    private Forecast unavailable(String reason, int n, double span, List<String> limitations) {
        return new Forecast(false, reason, METHOD, n, round(span), 0, 0, 0, List.of(), limitations);
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(14, value));
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
