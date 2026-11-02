package com.aquasense.reporting.render;

import com.aquasense.reporting.domain.Report;
import com.aquasense.reporting.domain.SectionVisitor;

public abstract class ReportRenderer implements SectionVisitor {

    protected final StringBuilder out = new StringBuilder();
    protected int depth;

    public final String render(Report report) {
        out.setLength(0);
        depth = 0;
        writeHeader(report);
        for (var child : report.root().children()) {
            child.accept(this);
        }
        writeFooter(report);
        return out.toString();
    }

    protected abstract void writeHeader(Report report);

    protected abstract void writeFooter(Report report);
}
