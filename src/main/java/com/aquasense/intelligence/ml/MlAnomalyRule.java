package com.aquasense.intelligence.ml;

import com.aquasense.intelligence.analysis.Anomaly;
import com.aquasense.intelligence.analysis.AnomalyRule;
import com.aquasense.measurement.Measurement;
import com.aquasense.measurement.MeasurementSource;
import com.aquasense.profile.WaterProfile;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Regla basada en aprendizaje automático (Strategy): se suma a las reglas fijas del detector de anomalías.
 * Usa el modelo Isolation Forest del usuario y, si la medición es inusual para su historial, explica por qué.
 * Así las alertas, el diagnóstico y el agente de informes también la aprovechan.
 */
@Component
@Order(4)
public class MlAnomalyRule implements AnomalyRule {

    public static final String TYPE = "ML_BEHAVIOR";

    private final MlModelService modelService;

    public MlAnomalyRule(MlModelService modelService) {
        this.modelService = modelService;
    }

    @Override
    public Optional<Anomaly> evaluate(Measurement current, List<Measurement> previous, WaterProfile profile) {
        if (current.getSource() == MeasurementSource.SIMULATED || current.getUserId() == null) {
            return Optional.empty();
        }
        Optional<MlModelService.Evaluation> evaluation = modelService.evaluate(current);
        if (evaluation.isEmpty() || !evaluation.get().anomalous()) {
            return Optional.empty();
        }
        UserAnomalyModel model = evaluation.get().model();
        double[] vector = evaluation.get().vector();
        double score = evaluation.get().score();
        String reasons = model.explain(vector, current).stream().limit(2)
            .map(f -> f.label() + ": " + f.value() + " (" + f.typical() + ")")
            .collect(Collectors.joining("; "));
        String message = String.format(Locale.US, "pH %.2f inusual para tu historial (puntaje %.2f, umbral %.2f): %s.",
            current.getPh(), score, model.threshold(), reasons);
        return Optional.of(new Anomaly(TYPE, Anomaly.Severity.CAUTION, "Comportamiento inusual (IA local)", message,
            current.getId(), current.getMeasuredAt(), current.getPh()));
    }
}
