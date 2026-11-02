package com.aquasense.reporting.domain;

import java.time.Instant;

public record Report(String title, Instant generatedAt, GroupSection root) {
}
