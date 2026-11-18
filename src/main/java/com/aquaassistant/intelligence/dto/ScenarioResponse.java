package com.aquaassistant.intelligence.dto;

import com.aquaassistant.intelligence.advice.Advice;
import com.aquaassistant.profile.Classification;

public record ScenarioResponse(String profileName, Classification current, Classification hypothetical,
                               String comparison, Advice advice, String note) {
}
