package com.aquasense.reporting.render;

import com.aquasense.reporting.domain.BulletListSection;
import com.aquasense.reporting.domain.ChartSection;
import com.aquasense.reporting.domain.GroupSection;
import com.aquasense.reporting.domain.ParagraphSection;
import com.aquasense.reporting.domain.Report;
import com.aquasense.reporting.domain.TableSection;
import java.util.List;
import java.util.Locale;

public class HtmlRenderer extends ReportRenderer {

    private static final int W = 640;
    private static final int H = 260;
    private static final int PAD = 40;

    @Override
    protected void writeHeader(Report report) {
        out.append("<!doctype html>\n<html lang=\"es\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                .append("<title>").append(esc(report.title())).append("</title><style>")
                .append("body{font-family:Segoe UI,Arial,sans-serif;max-width:860px;margin:2rem auto;padding:0 1rem;")
                .append("color:#0b2a3d}h1{color:#086aa0}h2{border-bottom:2px solid #14c8c0;padding-bottom:.2rem}")
                .append("table{border-collapse:collapse;width:100%;margin:.5rem 0}th,td{border:1px solid #d3e3ec;")
                .append("padding:.35rem .6rem;text-align:left}th{background:#eaf4f8}footer{color:#5b7585;")
                .append("font-size:.85rem;margin-top:2rem}svg{max-width:100%;height:auto}</style></head><body>\n")
                .append("<h1>").append(esc(report.title())).append("</h1>\n");
    }

    @Override
    protected void writeFooter(Report report) {
        out.append("<footer>Informe generado por AquaSense AI. El pH es un indicador y no sustituye otros análisis."
                + "</footer></body></html>\n");
    }

    @Override
    public void visit(GroupSection section) {
        out.append("<section><h2>").append(esc(section.title())).append("</h2>\n");
        section.children().forEach(c -> c.accept(this));
        out.append("</section>\n");
    }

    @Override
    public void visit(ParagraphSection section) {
        out.append("<p>").append(esc(section.text())).append("</p>\n");
    }

    @Override
    public void visit(BulletListSection section) {
        out.append("<ul>");
        section.items().forEach(i -> out.append("<li>").append(esc(i)).append("</li>"));
        out.append("</ul>\n");
    }

    @Override
    public void visit(TableSection section) {
        out.append("<table><thead><tr>");
        section.headers().forEach(h -> out.append("<th>").append(esc(h)).append("</th>"));
        out.append("</tr></thead><tbody>");
        for (List<String> row : section.rows()) {
            out.append("<tr>");
            row.forEach(c -> out.append("<td>").append(esc(c)).append("</td>"));
            out.append("</tr>");
        }
        out.append("</tbody></table>\n");
    }

    @Override
    public void visit(ChartSection section) {
        List<ChartSection.Point> pts = section.points();
        double min = Math.min(section.optimalMin(), pts.stream().mapToDouble(ChartSection.Point::ph).min().orElse(0));
        double max = Math.max(section.optimalMax(), pts.stream().mapToDouble(ChartSection.Point::ph).max().orElse(14));
        min = Math.max(0, Math.floor(min - 0.5));
        max = Math.min(14, Math.ceil(max + 0.5));
        double span = Math.max(max - min, 1);
        long t0 = pts.get(0).at().toEpochMilli();
        long t1 = pts.get(pts.size() - 1).at().toEpochMilli();
        double tSpan = Math.max(t1 - t0, 1);
        StringBuilder poly = new StringBuilder();
        for (ChartSection.Point p : pts) {
            double x = PAD + (p.at().toEpochMilli() - t0) / tSpan * (W - 2 * PAD);
            double y = H - PAD - (p.ph() - min) / span * (H - 2 * PAD);
            poly.append(String.format(Locale.US, "%.1f,%.1f ", x, y));
        }
        double bandTop = H - PAD - (section.optimalMax() - min) / span * (H - 2 * PAD);
        double bandBottom = H - PAD - (section.optimalMin() - min) / span * (H - 2 * PAD);
        out.append(String.format(Locale.US, "<figure><svg viewBox=\"0 0 %d %d\" role=\"img\" aria-label=\"%s\">",
                W, H, esc(section.caption())));
        out.append(String.format(Locale.US, "<rect x=\"%d\" y=\"%.1f\" width=\"%d\" height=\"%.1f\" fill=\"#14c8c0\" "
                + "opacity=\"0.15\"/>", PAD, bandTop, W - 2 * PAD, bandBottom - bandTop));
        out.append(String.format(Locale.US, "<line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"#5b7585\"/>", PAD,
                H - PAD, W - PAD, H - PAD));
        out.append(String.format(Locale.US, "<line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"#5b7585\"/>", PAD,
                PAD, PAD, H - PAD));
        out.append(String.format(Locale.US, "<text x=\"6\" y=\"%d\" font-size=\"11\" fill=\"#5b7585\">%.1f</text>",
                PAD + 4, max));
        out.append(String.format(Locale.US, "<text x=\"6\" y=\"%d\" font-size=\"11\" fill=\"#5b7585\">%.1f</text>",
                H - PAD, min));
        out.append("<polyline fill=\"none\" stroke=\"#0a84c6\" stroke-width=\"2.5\" points=\"")
                .append(poly.toString().trim()).append("\"/></svg><figcaption>").append(esc(section.caption()))
                .append(". La banda turquesa marca el rango recomendado del perfil.</figcaption></figure>\n");
    }

    static String esc(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
