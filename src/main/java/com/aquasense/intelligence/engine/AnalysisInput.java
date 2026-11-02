package com.aquasense.intelligence.engine;

import com.aquasense.waterprofile.domain.WaterType;
import java.util.List;

public record AnalysisInput(String profileName, WaterType waterType, double optimalMin, double optimalMax,
                            double cautionMin, double cautionMax, List<Sample> samples) {
}
