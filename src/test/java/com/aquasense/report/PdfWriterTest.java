package com.aquasense.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquasense.report.pdf.PdfWriter;
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
    void traceLineDropsTheRepeatedProviderName() {
        assertThat(com.aquasense.report.pdf.ReportPdfService.traceLine("Google Gemini no pudo terminar", "Google Gemini: límite de uso"))
            .isEqualTo("Google Gemini no pudo terminar: límite de uso");
        assertThat(com.aquasense.report.pdf.ReportPdfService.traceLine("Revisó las alertas", "3 alertas")).isEqualTo("Revisó las alertas: 3 alertas");
    }
}
