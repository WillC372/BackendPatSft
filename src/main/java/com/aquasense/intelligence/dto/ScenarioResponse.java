package com.aquasense.intelligence.dto;

import com.aquasense.intelligence.advice.Advice;
import com.aquasense.profile.Classification;

public record ScenarioResponse(String profileName, Classification current, Classification hypothetical,
                               String comparison, Advice advice, String note) {
}
