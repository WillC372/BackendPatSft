package com.aquasense.reporting.domain;

import com.aquasense.intelligence.engine.AnalysisReport;
import com.aquasense.intelligence.engine.AnomalyFinding;
import com.aquasense.intelligence.engine.ForecastPoint;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ReportBuilder {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("America/Bogota"));

    private final GroupSection root = new GroupSection("Informe");
    private final Instant generatedAt = Instant.now();
    private String title = "Informe de calidad del agua";

    public ReportBuilder title(String value) {
        this.title = value;
        return this;
    }

    public ReportBuilder general(String userName, String email, String profile, Instant from, Instant to,
                                 AnalysisReport report) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Usuario", userName));
        rows.add(List.of("Correo", email));
        rows.add(List.of("Perfil de agua", profile));
        rows.add(List.of("Periodo", (from == null ? "Inicio del historial" : fmt(from)) + " — "
                + (to == null ? fmt(generatedAt) : fmt(to))));
        rows.add(List.of("Mediciones analizadas", String.valueOf(report.measurementCount())));
        rows.add(List.of("De ellas, simuladas", String.valueOf(report.simulatedCount())));
        rows.add(List.of("Generado", fmt(generatedAt) + " (hora de Colombia)"));
        root.add(new GroupSection("Datos generales").add(new TableSection(List.of("Dato", "Valor"), rows)));
        root.add(new GroupSection("Aviso importante").add(new ParagraphSection(report.disclaimer())));
        return this;
    }

    public ReportBuilder statistics(AnalysisReport report) {
        GroupSection g = new GroupSection("Resumen estadístico");
        if (!report.sufficientData()) {
            g.add(new ParagraphSection("No hay datos suficientes para generar un resumen confiable."));
        } else {
            var s = report.statistics();
            g.add(new TableSection(List.of("Medida", "pH"), List.of(
                    List.of("Media", f(s.mean())), List.of("Mediana", f(s.median())),
                    List.of("Mínimo", f(s.min())), List.of("Máximo", f(s.max())),
                    List.of("Desviación estándar", f(s.standardDeviation())))));
        }
        root.add(g);
        return this;
    }

    public ReportBuilder anomalies(AnalysisReport report) {
        GroupSection g = new GroupSection("Anomalías detectadas");
        if (report.anomalies().isEmpty()) {
            g.add(new ParagraphSection(report.sufficientData() ? "No se detectaron anomalías estadísticas."
                    : "Sin datos suficientes para evaluar anomalías."));
        } else {
            g.add(new BulletListSection(report.anomalies().stream().limit(20)
                    .map((AnomalyFinding a) -> fmt(a.measuredAt()) + " — " + a.message() + ". " + a.explanation())
                    .toList()));
        }
        root.add(g);
        return this;
    }

    public ReportBuilder trendAndForecast(AnalysisReport report) {
        GroupSection g = new GroupSection("Tendencia y predicción");
        g.add(new ParagraphSection(report.trend().summary()));
        if (report.forecast().available()) {
            List<String> items = new ArrayList<>();
            for (ForecastPoint p : report.forecast().points()) {
                items.add(fmt(p.at()) + ": pH " + f(p.predicted()) + " (intervalo 95 %: " + f(p.lower()) + " a "
                        + f(p.upper()) + ")");
            }
            g.add(new BulletListSection(items));
            g.add(new ParagraphSection(report.forecast().limitations()));
        } else {
            g.add(new ParagraphSection("No se genera predicción. " + report.forecast().reason()));
        }
        root.add(g);
        return this;
    }

    public ReportBuilder chart(List<ChartSection.Point> points, double optimalMin, double optimalMax) {
        if (points.size() >= 3) {
            root.add(new GroupSection("Gráfica de evolución del pH").add(
                    new ChartSection("Evolución del pH en el periodo", points, optimalMin, optimalMax)));
        }
        return this;
    }

    public ReportBuilder measurements(List<List<String>> rows, int totalCount) {
        GroupSection g = new GroupSection("Mediciones registradas");
        if (rows.isEmpty()) {
            g.add(new ParagraphSection("No hay mediciones en el periodo."));
        } else {
            g.add(new TableSection(List.of("Fecha y hora", "pH", "Origen", "Zona", "Validación"), rows));
            if (totalCount > rows.size()) {
                g.add(new ParagraphSection("Se muestran las " + rows.size() + " mediciones más recientes de "
                        + totalCount + "."));
            }
        }
        root.add(g);
        return this;
    }

    public ReportBuilder conclusions(AnalysisReport report) {
        root.add(new GroupSection("Conclusiones y recomendaciones").add(new BulletListSection(
                report.recommendations())));
        List<String> limitations = new ArrayList<>(report.limitations());
        root.add(new GroupSection("Limitaciones").add(new BulletListSection(limitations)));
        return this;
    }

    public Report build() {
        return new Report(title, generatedAt, root);
    }

    private static String fmt(Instant at) {
        return FORMAT.format(at);
    }

    private static String f(double v) {
        return String.format(Locale.US, "%.2f", v);
    }
}
