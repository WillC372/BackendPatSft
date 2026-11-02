package com.aquasense.reporting.domain;

import java.util.List;

public record BulletListSection(List<String> items) implements ReportSection {

    @Override
    public void accept(SectionVisitor visitor) {
        visitor.visit(this);
    }
}
