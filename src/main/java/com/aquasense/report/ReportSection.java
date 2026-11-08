package com.aquasense.report;

import java.util.List;

public record ReportSection(String key, String title, List<String> lines) {
}
