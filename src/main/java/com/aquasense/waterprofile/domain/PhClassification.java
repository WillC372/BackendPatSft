package com.aquasense.waterprofile.domain;

import java.math.BigDecimal;

public record PhClassification(BigDecimal ph, AcidityClass acidity, RangeStatus status, boolean withinRecommendedRange) {
}
