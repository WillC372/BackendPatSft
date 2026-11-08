package com.aquasense.profile;

public record Classification(double ph, PhNature nature, PhStatus status, boolean withinRange, String explanation) {
}
