package com.aquasense.intelligence.deep;

import java.time.Instant;
import java.util.List;

/**
 * Resultado de la capa de aprendizaje profundo: datos usados, arquitectura, curva de entrenamiento, comparación con
 * referencias simples en datos no vistos, predicción de las próximas 12 horas con intervalo y alerta anticipada.
 */
public record DeepForecastResponse(boolean trained, String reason, boolean includesSimulated, DataSummary data,
                                   Architecture architecture, Training training, Evaluation evaluation, Forecast forecast,
                                   Alert alert, List<HistoryPoint> history, String explanation, String disclaimer) {

    public record DataSummary(int measurements, int hours, int observedHours, int interpolatedHours, int segments,
                              double spanDays, int windows, int requiredWindows) {
    }

    public record Architecture(String type, int inputs, int hidden, int outputs, int windowHours, int horizonHours,
                               int parameters, List<String> features) {
    }

    public record Training(int epochs, int bestEpoch, List<Double> trainLoss, List<Double> validationLoss, long trainingMs,
                           int trainWindows, int validationWindows, int testWindows) {
    }

    public record ModelScore(String id, String label, double mae) {
    }

    public record Evaluation(List<ModelScore> models, String chosen, String chosenLabel, double skillPercent,
                             boolean networkChosen, List<Double> networkMaeByHour, List<Double> bestBaselineMaeByHour,
                             String bestBaselineLabel) {
    }

    public record ForecastPoint(Instant at, int hoursAhead, double network, double baseline, double chosen, double lower,
                                double upper) {
    }

    public record Forecast(Instant from, double lastPh, boolean stale, List<ForecastPoint> points, String intervalLabel) {
    }

    public record Alert(String level, String message, Integer hoursAhead, Double ph) {
    }

    public record HistoryPoint(Instant at, double ph, boolean observed) {
    }
}
