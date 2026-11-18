package com.aquaassistant.intelligence.agent;

import java.util.List;

/**
 * Result of checking the pH figures of the report against the data returned by the tools.
 */
public record Verification(int checked, int verified, List<String> unverified) {
}
