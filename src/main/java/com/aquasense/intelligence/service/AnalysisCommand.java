package com.aquasense.intelligence.service;

import java.time.Instant;
import java.util.UUID;

public record AnalysisCommand(UUID ownerId, UUID profileId, Instant from, Instant to, boolean includeSimulated) {
}
