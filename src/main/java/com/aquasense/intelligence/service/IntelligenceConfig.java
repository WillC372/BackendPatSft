package com.aquasense.intelligence.service;

import com.aquasense.intelligence.engine.AnalysisEngine;
import com.aquasense.intelligence.repository.AlertRepository;
import com.aquasense.intelligence.repository.AnalysisRepository;
import com.aquasense.measurement.service.MeasurementService;
import com.aquasense.waterprofile.service.WaterProfileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IntelligenceConfig {

    @Bean
    public AnalysisUseCase analysisUseCase(MeasurementService measurements, WaterProfileService profiles,
                                           AnalysisEngine engine, AnalysisRepository analyses, AlertRepository alerts,
                                           ObjectMapper mapper) {
        AnalysisUseCase computing = new ComputingAnalysis(measurements, profiles, engine);
        AnalysisUseCase persisting = new PersistingAnalysis(computing, analyses, mapper);
        return new AlertingAnalysis(persisting, alerts);
    }
}
