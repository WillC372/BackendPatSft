package com.aquasense.intelligence.dto;

import com.aquasense.intelligence.advice.Advice;
import com.aquasense.knowledge.Citation;
import com.aquasense.measurement.dto.MeasurementResponse;
import java.util.List;

/**
 * Recomendación por reglas del perfil, acompañada de los fragmentos normativos que la respaldan
 * ({@code sources}) y de una nota de cobertura cuando la base no tiene una norma para ese uso.
 */
public record RecommendationResponse(MeasurementResponse basedOn, String profileName, Advice advice, String disclaimer,
                                     List<Citation> sources, String coverageNote) {
}
