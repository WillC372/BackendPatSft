package com.aquasense.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquasense.reporting.domain.BulletListSection;
import com.aquasense.reporting.domain.ChartSection;
import com.aquasense.reporting.domain.GroupSection;
import com.aquasense.reporting.domain.ParagraphSection;
import com.aquasense.reporting.domain.Report;
import com.aquasense.reporting.domain.ReportFormat;
import com.aquasense.reporting.domain.TableSection;
import com.aquasense.reporting.render.HtmlRenderer;
import com.aquasense.reporting.render.MarkdownRenderer;
import com.aquasense.reporting.render.ReportRendererFactory;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportRenderingTest {

    private Report sample(String hostile) {
        GroupSection root = new GroupSection("Informe");
        root.add(new GroupSection("Datos").add(new TableSection(List.of("Dato", "Valor"),
                List.of(List.of("Usuario", hostile), List.of("A|B", "x")))));
        root.add(new GroupSection("Notas").add(new ParagraphSection("Texto " + hostile))
                .add(new BulletListSection(List.of("uno", hostile))));
        Instant t = Instant.parse("2026-11-01T00:00:00Z");
        root.add(new GroupSection("Gráfica").add(new ChartSection("Evolución", List.of(
                new ChartSection.Point(t, 7.0), new ChartSection.Point(t.plusSeconds(3600), 7.2),
                new ChartSection.Point(t.plusSeconds(7200), 6.9)), 6.5, 7.5)));
        return new Report("Título <b>", Instant.now(), root);
    }

    @Test
    void markdownRendersStructureAndEscapesTablePipes() {
        String md = new MarkdownRenderer().render(sample("Ana"));
        assertThat(md).startsWith("# Título <b>").contains("## Datos").contains("| Dato | Valor |")
                .contains("A\\|B").contains("- uno").contains("disponible en el formato HTML");
    }

    @Test
    void htmlEscapesUserControlledContent() {
        String html = new HtmlRenderer().render(sample("<script>alert(1)</script>"));
        assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;alert(1)&lt;/script&gt;")
                .contains("<title>Título &lt;b&gt;</title>");
    }

    @Test
    void htmlChartIsInlineSvgWithoutScripts() {
        String html = new HtmlRenderer().render(sample("Ana"));
        assertThat(html).contains("<svg").contains("<polyline").contains("rango recomendado");
        assertThat(html).doesNotContain("<script");
    }

    @Test
    void factoryReturnsRendererPerFormat() {
        ReportRendererFactory factory = new ReportRendererFactory();
        assertThat(factory.forFormat(ReportFormat.MARKDOWN)).isInstanceOf(MarkdownRenderer.class);
        assertThat(factory.forFormat(ReportFormat.HTML)).isInstanceOf(HtmlRenderer.class);
    }
}
