package com.aquasense.intelligence.service;

import com.aquasense.billing.service.LimitGuard;
import com.aquasense.billing.service.SubscriptionService;
import com.aquasense.intelligence.engine.AnalysisEngine;
import com.aquasense.intelligence.repository.AlertRepository;
import com.aquasense.intelligence.repository.AnalysisRepository;
import com.aquasense.measurement.service.MeasurementService;
import com.aquasense.waterprofile.service.WaterProfileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class IntelligenceConfig {

    @Bean("restrictedAnalysis")
    public AnalysisUseCase restrictedAnalysis(MeasurementService measurements, WaterProfileService profiles,
                                              AnalysisEngine engine, SubscriptionService subscriptions) {
        return new PlanRestrictedAnalysis(new ComputingAnalysis(measurements, profiles, engine), subscriptions);
    }

    @Bean
    @Primary
    public AnalysisUseCase analysisUseCase(MeasurementService measurements, WaterProfileService profiles,
                                           AnalysisEngine engine, AnalysisRepository analyses, AlertRepository alerts,
                                           ObjectMapper mapper, SubscriptionService subscriptions,
                                           LimitGuard guard) {
        AnalysisUseCase computing = new ComputingAnalysis(measurements, profiles, engine);
        AnalysisUseCase restricted = new PlanRestrictedAnalysis(computing, subscriptions);
        AnalysisUseCase persisting = new PersistingAnalysis(restricted, analyses, mapper);
        AnalysisUseCase alerting = new AlertingAnalysis(persisting, alerts);
        return new QuotaProxyAnalysis(alerting, guard);
    }
}
