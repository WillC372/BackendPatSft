package com.aquasense.intelligence.advice;

import java.util.List;

public record Advice(String summary, List<String> possibleCauses, List<String> actions, boolean needsExpert,
                     String expertNote) {
}
