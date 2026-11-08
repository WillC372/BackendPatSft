package com.aquasense.report;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builder: assembles a report step by step with optional sections and checks that the required
 * data is present before building it.
 */
public class ReportBuilder {

    private String title;
    private Instant periodStart;
    private Instant periodEnd;
    private String profileName;
    private int measurementCount;
    private boolean simulatedData;
    private String disclaimer;
    private final List<ReportSection> sections = new ArrayList<>();

    public ReportBuilder title(String value) {
        this.title = value;
        return this;
    }

    public ReportBuilder period(Instant start, Instant end) {
        this.periodStart = start;
        this.periodEnd = end;
        return this;
    }

    public ReportBuilder profile(String name) {
        this.profileName = name;
        return this;
    }

    public ReportBuilder measurementCount(int count) {
        this.measurementCount = count;
        return this;
    }

    public ReportBuilder containsSimulatedData(boolean value) {
        this.simulatedData = value;
        return this;
    }

    public ReportBuilder section(String key, String sectionTitle, List<String> lines) {
        if (lines != null && !lines.isEmpty()) {
            sections.add(new ReportSection(key, sectionTitle, List.copyOf(lines)));
        }
        return this;
    }

    public ReportBuilder disclaimer(String value) {
        this.disclaimer = value;
        return this;
    }

    public Report build() {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(periodStart, "periodStart");
        Objects.requireNonNull(periodEnd, "periodEnd");
        Objects.requireNonNull(profileName, "profileName");
        if (periodStart.isAfter(periodEnd)) {
            throw new IllegalStateException("periodStart must be before periodEnd");
        }
        return new Report(title, Instant.now(), periodStart, periodEnd, profileName, measurementCount, simulatedData,
            List.copyOf(sections), disclaimer);
    }
}
