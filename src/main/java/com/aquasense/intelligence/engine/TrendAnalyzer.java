package com.aquasense.intelligence.engine;

import com.aquasense.intelligence.strategy.AnalysisStrategy;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

public final class TrendAnalyzer {

    public static final int MIN_POINTS = 5;
    public static final Duration MIN_SPAN = Duration.ofHours(12);
    public static final double MIN_R_SQUARED = 0.3;

    private TrendAnalyzer() {
    }

    public static TrendResult analyze(List<Sample> samples, AnalysisStrategy strategy) {
        if (samples.size() < MIN_POINTS) {
            return inconclusive("Se necesitan al menos " + MIN_POINTS + " mediciones para analizar tendencias; hay "
                    + samples.size() + ".");
        }
        Duration span = Duration.between(samples.get(0).at(), samples.get(samples.size() - 1).at());
        if (span.compareTo(MIN_SPAN) < 0) {
            return inconclusive("Las mediciones abarcan menos de 12 horas: es muy poco tiempo para hablar de "
                    + "tendencia.");
        }
        LinearFit fit = LinearFit.of(samples);
        int half = samples.size() / 2;
        double first = StatisticsCalculator.of(samples.subList(0, half)).mean();
        double second = StatisticsCalculator.of(samples.subList(half, samples.size())).mean();
        boolean significant = Math.abs(fit.slopePerDay()) >= strategy.significantTrendPerDay()
                && fit.rSquared() >= MIN_R_SQUARED;
        TrendDirection direction = !significant ? TrendDirection.STABLE
                : fit.slopePerDay() > 0 ? TrendDirection.RISING : TrendDirection.FALLING;
        String summary = switch (direction) {
            case RISING -> String.format(Locale.US, "El pH tiende a subir unas %.2f unidades por día (ajuste R² = "
                    + "%.2f).", fit.slopePerDay(), fit.rSquared());
            case FALLING -> String.format(Locale.US, "El pH tiende a bajar unas %.2f unidades por día (ajuste R² = "
                    + "%.2f).", -fit.slopePerDay(), fit.rSquared());
            default -> String.format(Locale.US, "No se observa una tendencia significativa para %s (pendiente %.3f "
                    + "por día, R² = %.2f).", strategy.context(), fit.slopePerDay(), fit.rSquared());
        };
        return new TrendResult(direction, fit.slopePerDay(), fit.rSquared(), first, second, summary);
    }

    private static TrendResult inconclusive(String reason) {
        return new TrendResult(TrendDirection.INCONCLUSIVE, 0, 0, 0, 0, reason);
    }
}
