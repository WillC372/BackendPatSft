package com.aquasense.reporting.domain;

import java.time.Instant;
import java.util.List;

public record ChartSection(String caption, List<Point> points, double optimalMin, double optimalMax)
        implements ReportSection {

    public record Point(Instant at, double ph) {
    }

    @Override
    public void accept(SectionVisitor visitor) {
        visitor.visit(this);
    }
}
