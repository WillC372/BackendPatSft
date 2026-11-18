package com.aquaassistant.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquaassistant.report.pdf.PdfWriter;
import java.awt.Color;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/** The PDF generator: accents, line breaks, pagination and page numbers. */
class PdfWriterTest {

    @Test
    void longContentFlowsOntoNumberedPagesWithAccents() throws Exception {
        PdfWriter pdf = new PdfWriter("Prueba", "Pie de página");
        pdf.banner("Informe de prueba", "Título con tildes: pH, acción, ñandú", "Subtítulo");
        pdf.heading("Sección");
        for (int i = 0; i < 80; i++) {
            pdf.paragraph("Párrafo " + i + ": el agua mantiene un pH estable en el rango recomendado para el perfil, sin anomalías críticas.");
        }
        pdf.callout("Aviso", List.of("Datos simulados ≠ agua real"), Color.ORANGE);
        byte[] bytes = pdf.finish();
        try (var document = Loader.loadPDF(bytes)) {
            int pages = document.getNumberOfPages();
            assertThat(pages).isGreaterThan(1);
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("ñandú").contains("acción").contains("Párrafo 79")
                .contains("Página 1 de " + pages).contains("Página " + pages + " de " + pages);
        }
    }

    @Test
    void chineseAndJapaneseAreDrawnWithTheCjkFontAndWrap() throws Exception {
        PdfWriter pdf = new PdfWriter("水质报告", "Aqua Assistant AI · 仅供参考");
        pdf.banner("水质报告 · 最近 7 天", "游泳池的 pH 值", null);
        pdf.paragraph("测量次数：4。平均值：7.65。最小值：7.10。最大值：8.20。".repeat(6));
        pdf.paragraph("水は推奨範囲内です。pH 7.4 を保ち、定期的に測定してください。");
        pdf.image(com.aquaassistant.report.pdf.ChartPainter.bars(List.of("范围内", "注意", "危急"), List.of(3, 1, 0),
            List.of(Color.GREEN, Color.ORANGE, Color.RED)), "按状态分布的测量");
        byte[] bytes = pdf.finish();
        try (var document = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(document).replaceAll("\\s+", "");
            assertThat(text).contains("游泳池的pH值").contains("水は推奨範囲内です").contains("最大值：8.20").doesNotContain("?");
        }
    }

    @Test
    void traceLineDropsTheRepeatedProviderName() {
        assertThat(com.aquaassistant.report.pdf.ReportPdfService.traceLine("Google Gemini no pudo terminar", "Google Gemini: límite de uso"))
            .isEqualTo("Google Gemini no pudo terminar: límite de uso");
        assertThat(com.aquaassistant.report.pdf.ReportPdfService.traceLine("Revisó las alertas", "3 alertas")).isEqualTo("Revisó las alertas: 3 alertas");
    }
}
