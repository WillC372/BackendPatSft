package com.aquasense.reporting.domain;

public interface ReportSection {

    void accept(SectionVisitor visitor);
}
