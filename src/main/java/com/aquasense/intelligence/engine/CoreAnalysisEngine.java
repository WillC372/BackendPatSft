package com.aquasense.intelligence.engine;

import com.aquasense.intelligence.detect.AnomalyDetectionChain;
import com.aquasense.intelligence.strategy.AnalysisStrategies;
import com.aquasense.intelligence.strategy.AnalysisStrategy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CoreAnalysisEngine implements AnalysisEngine {

    public static final int MIN_POINTS_FOR_ANALYSIS = 3;

    private final AnalysisStrategies strategies;
    private final AnomalyDetectionChain detectors;

    public CoreAnalysisEngine(AnalysisStrategies strategies, AnomalyDetectionChain detectors) {
        this.strategies = strategies;
        this.detectors = detectors;
    }

    @Override
    public AnalysisReport analyze(AnalysisInput raw) {
        List<Sample> sorted = raw.samples().stream().sorted(Comparator.comparing(Sample::at)).toList();
        AnalysisInput input = new AnalysisInput(raw.profileName(), raw.waterType(), raw.optimalMin(),
                raw.optimalMax(), raw.cautionMin(), raw.cautionMax(), sorted);
        int simulated = (int) sorted.stream().filter(Sample::simulated).count();
        List<String> limitations = new ArrayList<>();
        if (simulated > 0) {
            limitations.add(simulated + " de " + sorted.size() + " mediciones son SIMULACIÓN; no representan agua "
                    + "real.");
        }
        if (sorted.size() < MIN_POINTS_FOR_ANALYSIS) {
            limitations.add("No hay datos suficientes para generar conclusiones confiables (se requieren al menos "
                    + MIN_POINTS_FOR_ANALYSIS + " mediciones).");
            return new AnalysisReport(input.profileName(), sorted.size(), simulated, false,
                    StatisticsCalculator.of(sorted), List.of(), TrendAnalyzer.analyze(sorted, strategies.forType(
                            input.waterType())), ForecastResult.unavailable("No hay datos suficientes."),
                    RecommendationRules.build(input, StatisticsCalculator.of(sorted), List.of(),
                            TrendAnalyzer.analyze(sorted, strategies.forType(input.waterType())), false),
                    limitations, AnalysisReport.DISCLAIMER);
        }
        AnalysisStrategy strategy = strategies.forType(input.waterType());
        Statistics stats = StatisticsCalculator.of(sorted);
        List<AnomalyFinding> findings = detectors.detect(input, strategy);
        TrendResult trend = TrendAnalyzer.analyze(sorted, strategy);
        ForecastResult forecast = Forecaster.forecast(sorted);
        limitations.add("Los análisis se basan únicamente en el pH; no evalúan contaminantes ni otros parámetros.");
        if (!forecast.available()) {
            limitations.add(forecast.reason());
        }
        List<String> recommendations = RecommendationRules.build(input, stats, findings, trend, true);
        return new AnalysisReport(input.profileName(), sorted.size(), simulated, true, stats, findings, trend,
                forecast, recommendations, limitations, AnalysisReport.DISCLAIMER);
    }
}
