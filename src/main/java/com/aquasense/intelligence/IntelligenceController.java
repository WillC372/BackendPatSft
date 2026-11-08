package com.aquasense.intelligence;

import com.aquasense.intelligence.dto.AiStatusResponse;
import com.aquasense.intelligence.dto.AnomaliesResponse;
import com.aquasense.intelligence.dto.ConclusionResponse;
import com.aquasense.intelligence.dto.ForecastResponse;
import com.aquasense.intelligence.dto.OverviewResponse;
import com.aquasense.intelligence.dto.RecommendationResponse;
import com.aquasense.intelligence.dto.ScenarioRequest;
import com.aquasense.intelligence.dto.ScenarioResponse;
import com.aquasense.intelligence.dto.TrendsResponse;
import com.aquasense.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/intelligence")
public class IntelligenceController {

    private final IntelligenceFacade facade;
    private final com.aquasense.intelligence.scenario.ScenarioExplainer scenarioExplainer;

    public IntelligenceController(IntelligenceFacade facade,
                                  com.aquasense.intelligence.scenario.ScenarioExplainer scenarioExplainer) {
        this.facade = facade;
        this.scenarioExplainer = scenarioExplainer;
    }

    @GetMapping("/overview")
    public OverviewResponse overview() {
        return facade.overview(CurrentUser.id());
    }

    @GetMapping("/anomalies")
    public AnomaliesResponse anomalies(@RequestParam(defaultValue = "7") int days) {
        return facade.anomalies(CurrentUser.id(), days);
    }

    @GetMapping("/trends")
    public TrendsResponse trends(@RequestParam(defaultValue = "7") int days) {
        return facade.trends(CurrentUser.id(), days);
    }

    @GetMapping("/forecast")
    public ForecastResponse forecast(@RequestParam(defaultValue = "7") int horizon) {
        return facade.forecast(CurrentUser.id(), horizon);
    }

    @GetMapping("/recommendations")
    public RecommendationResponse recommendations() {
        return facade.recommendations(CurrentUser.id());
    }

    @PostMapping("/scenario")
    public ScenarioResponse scenario(@Valid @RequestBody ScenarioRequest request) {
        return facade.scenario(CurrentUser.id(), request.hypotheticalPh());
    }

    @PostMapping("/scenario/explain")
    public com.aquasense.intelligence.scenario.ScenarioExplanation explainScenario(@Valid @RequestBody ScenarioRequest request) {
        return scenarioExplainer.explain(CurrentUser.id(), request.hypotheticalPh());
    }

    @PostMapping("/conclusion")
    public ConclusionResponse conclusion(@RequestParam(defaultValue = "7") int days) {
        return facade.conclusion(CurrentUser.id(), days);
    }


    @PostMapping("/diagnosis")
    public com.aquasense.intelligence.diagnosis.DiagnosisResponse diagnose(@RequestParam(defaultValue = "7") int days) {
        return facade.diagnose(CurrentUser.id(), days);
    }

    @GetMapping("/diagnosis/latest")
    public org.springframework.http.ResponseEntity<com.aquasense.intelligence.diagnosis.DiagnosisResponse> latestDiagnosis() {
        return facade.latestDiagnosis(CurrentUser.id()).map(org.springframework.http.ResponseEntity::ok)
            .orElse(org.springframework.http.ResponseEntity.noContent().build());
    }

    @GetMapping("/diagnosis/history")
    public java.util.List<com.aquasense.intelligence.diagnosis.DiagnosisResponse> diagnosisHistory(
            @RequestParam(defaultValue = "10") int limit) {
        return facade.diagnosisHistory(CurrentUser.id(), limit);
    }

    @GetMapping("/ml/insights")
    public com.aquasense.intelligence.ml.MlInsightsResponse mlInsights(@RequestParam(defaultValue = "30") int days,
                                                                   @RequestParam(defaultValue = "false") boolean includeSimulated) {
        return facade.mlInsights(CurrentUser.id(), days, includeSimulated);
    }

    @GetMapping("/ai-status")
    public AiStatusResponse aiStatus() {
        return facade.aiStatus(CurrentUser.id());
    }
}
