package com.aquasense.intelligence.strategy;

import com.aquasense.waterprofile.domain.WaterType;
import org.springframework.stereotype.Component;

@Component
public class AnalysisStrategies {

    private final AnalysisStrategy pool = new PoolStrategy();
    private final AnalysisStrategy aquarium = new AquariumStrategy();
    private final AnalysisStrategy drinking = new DrinkingStrategy();
    private final AnalysisStrategy agriculture = new AgricultureStrategy();
    private final AnalysisStrategy general = new GeneralStrategy();

    public AnalysisStrategy forType(WaterType type) {
        return switch (type) {
            case POOL -> pool;
            case AQUARIUM -> aquarium;
            case DRINKING -> drinking;
            case AGRICULTURE -> agriculture;
            case GENERAL, CUSTOM -> general;
        };
    }
}
