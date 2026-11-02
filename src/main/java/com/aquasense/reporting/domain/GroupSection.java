package com.aquasense.reporting.domain;

import java.util.ArrayList;
import java.util.List;

public class GroupSection implements ReportSection {

    private final String title;
    private final List<ReportSection> children = new ArrayList<>();

    public GroupSection(String title) {
        this.title = title;
    }

    public GroupSection add(ReportSection child) {
        children.add(child);
        return this;
    }

    public String title() {
        return title;
    }

    public List<ReportSection> children() {
        return List.copyOf(children);
    }

    @Override
    public void accept(SectionVisitor visitor) {
        visitor.visit(this);
    }
}
