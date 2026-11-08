package com.aquasense.intelligence.dto;

import com.aquasense.intelligence.advice.Advice;
import com.aquasense.knowledge.Citation;
import com.aquasense.measurement.dto.MeasurementResponse;
import java.util.List;

/**
 * Rule-based recommendation of the profile, together with the guideline fragments that support it
 * ({@code sources}) and a coverage note when the knowledge base has no guideline for that use.
 */
public record RecommendationResponse(MeasurementResponse basedOn, String profileName, Advice advice, String disclaimer,
                                     List<Citation> sources, String coverageNote) {
}
