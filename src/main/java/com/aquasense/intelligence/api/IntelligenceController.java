package com.aquasense.intelligence.api;

import com.aquasense.identity.security.AuthenticatedUser;
import com.aquasense.intelligence.api.IntelligenceDtos.AlertResponse;
import com.aquasense.intelligence.api.IntelligenceDtos.AnalysisRequest;
import com.aquasense.intelligence.api.IntelligenceDtos.AnalysisSummary;
import com.aquasense.intelligence.api.IntelligenceDtos.ScenarioRequest;
import com.aquasense.intelligence.engine.AnalysisReport;
import com.aquasense.intelligence.service.AnalysisCommand;
import com.aquasense.intelligence.service.AnalysisResult;
import com.aquasense.intelligence.service.IntelligenceService;
import com.aquasense.intelligence.service.ScenarioService;
import com.aquasense.intelligence.service.ScenarioService.Scenario;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analysis")
@Tag(name = "AquaSense Intelligence")
@SecurityRequirement(name = "bearerAuth")
public class IntelligenceController {

    private final IntelligenceService service;
    private final ScenarioService scenarios;

    public IntelligenceController(IntelligenceService service, ScenarioService scenarios) {
        this.service = service;
        this.scenarios = scenarios;
    }

    @PostMapping
    public AnalysisResult analyze(@AuthenticationPrincipal AuthenticatedUser user,
                                  @Valid @RequestBody AnalysisRequest r) {
        return service.analyze(new AnalysisCommand(user.id(), r.profileId(), r.from(), r.to(),
                r.includeSimulated() == null || r.includeSimulated()));
    }

    @GetMapping("/history")
    public List<AnalysisSummary> history(@AuthenticationPrincipal AuthenticatedUser user,
                                         @RequestParam(defaultValue = "20") int size) {
        return service.history(user.id(), size).stream().map(AnalysisSummary::from).toList();
    }

    @GetMapping("/alerts")
    public List<AlertResponse> alerts(@AuthenticationPrincipal AuthenticatedUser user,
                                      @RequestParam(defaultValue = "true") boolean onlyOpen) {
        return service.alerts(user.id(), onlyOpen).stream().map(AlertResponse::from).toList();
    }

    @PostMapping("/alerts/{id}/acknowledge")
    public AlertResponse acknowledge(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return AlertResponse.from(service.acknowledge(id, user.id()));
    }

    @PostMapping("/scenario")
    public Scenario scenario(@AuthenticationPrincipal AuthenticatedUser user,
                             @Valid @RequestBody ScenarioRequest r) {
        return scenarios.simulate(user.id(), r.profileId(), r.ph(), r.delta());
    }

    @GetMapping("/{id}")
    public AnalysisReport stored(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return service.stored(id, user.id());
    }
}
