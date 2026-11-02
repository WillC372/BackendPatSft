package com.aquasense.reporting.domain;

public record ParagraphSection(String text) implements ReportSection {

    @Override
    public void accept(SectionVisitor visitor) {
        visitor.visit(this);
    }
}
