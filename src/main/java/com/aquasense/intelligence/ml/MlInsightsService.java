package com.aquasense.intelligence.ml;

import com.aquasense.measurement.Measurement;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MlInsightsService {

    private static final String METHOD = "Isolation Forest (100 árboles) sobre pH, cambio, velocidad de cambio, "
        + "distancia a la mediana reciente, hora del día y temperatura (si está disponible). Entrenado con tus últimos 90 días.";

    private final MlModelService modelService;

    public MlInsightsService(MlModelService modelService) {
        this.modelService = modelService;
    }

    public MlInsightsResponse insights(Long userId, int days, boolean includeSimulated) {
        MlModelService.Training training = modelService.model(userId, includeSimulated);
        List<Measurement> data = training.data();
        List<MlInsightsResponse.HourBucket> hourly = hourlyPattern(data);
        if (!training.trained()) {
            return new MlInsightsResponse(false, training.reason(), includeSimulated, data.size(), 0,
                List.of(), 0, 0, 0, MlModelService.CONTAMINATION, null, 0, days, List.of(), List.of(), hourly, METHOD);
        }
        UserAnomalyModel model = training.model();
        Instant from = Instant.now().minus(Duration.ofDays(days));
        List<MlInsightsResponse.Point> points = new ArrayList<>();
        List<MlInsightsResponse.Finding> findings = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            Measurement m = data.get(i);
            if (m.getMeasuredAt().isBefore(from)) {
                continue;
            }
            double[] vector = model.vector(data, i);
            double score = Math.round(model.score(vector) * 1000) / 1000.0;
            boolean anomalous = model.isAnomalous(score);
            points.add(new MlInsightsResponse.Point(m.getId(), m.getMeasuredAt(), m.getPh(), score, anomalous, m.getSource().name()));
            if (anomalous) {
                findings.add(new MlInsightsResponse.Finding(m.getId(), m.getMeasuredAt(), m.getPh(), score, m.getSource().name(),
                    model.explain(vector, m)));
            }
        }
        findings.sort(Comparator.comparingDouble(MlInsightsResponse.Finding::score).reversed());
        return new MlInsightsResponse(true, null, includeSimulated, model.trainingSize(),
            Math.round(training.spanDays() * 10) / 10.0, model.featureLabels(), model.treeCount(), model.sampleSize(),
            Math.round(model.threshold() * 1000) / 1000.0, MlModelService.CONTAMINATION, model.trainedAt(),
            model.trainingMillis(), days, points, findings.subList(0, Math.min(15, findings.size())), hourly, METHOD);
    }

    private static List<MlInsightsResponse.HourBucket> hourlyPattern(List<Measurement> data) {
        double[] sums = new double[24];
        int[] counts = new int[24];
        for (Measurement m : data) {
            int hour = m.getMeasuredAt().atZone(ZoneOffset.UTC).getHour();
            sums[hour] += m.getPh();
            counts[hour]++;
        }
        List<MlInsightsResponse.HourBucket> buckets = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            buckets.add(new MlInsightsResponse.HourBucket(h, counts[h] == 0 ? null : Math.round(sums[h] / counts[h] * 100) / 100.0, counts[h]));
        }
        return buckets;
    }
}
