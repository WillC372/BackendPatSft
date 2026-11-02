package com.aquasense.reporting.domain;

import java.util.List;

public record TableSection(List<String> headers, List<List<String>> rows) implements ReportSection {

    @Override
    public void accept(SectionVisitor visitor) {
        visitor.visit(this);
    }
}
