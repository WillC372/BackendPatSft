package com.aquasense.reporting.render;

import com.aquasense.reporting.domain.BulletListSection;
import com.aquasense.reporting.domain.ChartSection;
import com.aquasense.reporting.domain.GroupSection;
import com.aquasense.reporting.domain.ParagraphSection;
import com.aquasense.reporting.domain.Report;
import com.aquasense.reporting.domain.TableSection;
import java.util.List;

public class MarkdownRenderer extends ReportRenderer {

    @Override
    protected void writeHeader(Report report) {
        out.append("# ").append(clean(report.title())).append("\n\n");
    }

    @Override
    protected void writeFooter(Report report) {
        out.append("---\n_Informe generado por AquaSense AI. El pH es un indicador y no sustituye otros análisis._\n");
    }

    @Override
    public void visit(GroupSection section) {
        out.append("## ").append(clean(section.title())).append("\n\n");
        section.children().forEach(c -> c.accept(this));
    }

    @Override
    public void visit(ParagraphSection section) {
        out.append(clean(section.text())).append("\n\n");
    }

    @Override
    public void visit(BulletListSection section) {
        section.items().forEach(i -> out.append("- ").append(clean(i)).append('\n'));
        out.append('\n');
    }

    @Override
    public void visit(TableSection section) {
        out.append("| ").append(String.join(" | ", section.headers().stream().map(this::cell).toList()))
                .append(" |\n");
        out.append("|").append("---|".repeat(section.headers().size())).append('\n');
        for (List<String> row : section.rows()) {
            out.append("| ").append(String.join(" | ", row.stream().map(this::cell).toList())).append(" |\n");
        }
        out.append('\n');
    }

    @Override
    public void visit(ChartSection section) {
        out.append("_La gráfica está disponible en el formato HTML del plan Premium._\n\n");
    }

    private String cell(String value) {
        return clean(value).replace("|", "\\|");
    }

    private String clean(String value) {
        return value == null ? "" : value.replace("\r", " ").replace("\n", " ");
    }
}
