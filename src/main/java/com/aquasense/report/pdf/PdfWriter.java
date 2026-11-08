package com.aquasense.report.pdf;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * Builder de documentos PDF con la identidad de AquaSense: maneja la posición vertical, el salto de página automático,
 * el ajuste de líneas y los bloques (títulos, párrafos, cifras, citas, imágenes). Usa DejaVu Sans incrustada, así las
 * tildes, la ñ y símbolos como ° y ± se ven en cualquier lector.
 */
public final class PdfWriter implements AutoCloseable {

    static final Color INK = new Color(0x0b, 0x23, 0x40);
    static final Color MUTED = new Color(0x4f, 0x6a, 0x88);
    static final Color ACCENT = new Color(0x0f, 0x72, 0xe0);
    static final Color CYAN = new Color(0x0a, 0xa9, 0xcf);
    static final Color LINE = new Color(0xd2, 0xe1, 0xee);
    static final Color SURFACE = new Color(0xee, 0xf5, 0xfb);
    static final Color OK = new Color(0x13, 0x8a, 0x50);
    static final Color WARN = new Color(0xa9, 0x64, 0x00);
    static final Color CRIT = new Color(0xc5, 0x2f, 0x43);

    private static final float MARGIN = 48;
    private static final float BOTTOM = 56;

    private final PDDocument document = new PDDocument();
    private final PDType0Font regular;
    private final PDType0Font bold;
    private final Map<PDType0Font, Map<Integer, Boolean>> glyphs = new HashMap<>();
    private final String footer;
    private PDPage page;
    private PDPageContentStream content;
    private float y;

    public PdfWriter(String title, String footer) throws IOException {
        this.regular = load("fonts/DejaVuSans.ttf");
        this.bold = load("fonts/DejaVuSans-Bold.ttf");
        this.footer = footer;
        PDDocumentInformation info = document.getDocumentInformation();
        info.setTitle(title);
        info.setAuthor("AquaSense AI");
        info.setCreator("AquaSense AI");
        newPage();
    }

    private PDType0Font load(String resource) throws IOException {
        try (InputStream in = PdfWriter.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Missing font " + resource);
            }
            return PDType0Font.load(document, in, true);
        }
    }

    private float width() {
        return PDRectangle.A4.getWidth() - 2 * MARGIN;
    }

    private void newPage() throws IOException {
        if (content != null) {
            content.close();
        }
        page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        content = new PDPageContentStream(document, page);
        y = PDRectangle.A4.getHeight() - MARGIN;
    }

    private void ensure(float height) throws IOException {
        if (y - height < BOTTOM) {
            newPage();
        }
    }

    /** Banda de encabezado de la primera página: marca, título y subtítulo. */
    public PdfWriter banner(String eyebrow, String title, String subtitle) throws IOException {
        float top = PDRectangle.A4.getHeight();
        float height = 118;
        content.setNonStrokingColor(INK);
        content.addRect(0, top - height, PDRectangle.A4.getWidth(), height);
        content.fill();
        content.setNonStrokingColor(CYAN);
        content.addRect(0, top - height, PDRectangle.A4.getWidth(), 4);
        content.fill();
        text(bold, 13, Color.WHITE, MARGIN, top - 34, "AquaSense");
        text(bold, 13, CYAN, MARGIN + bold.getStringWidth("AquaSense ") / 1000 * 13, top - 34, "AI");
        text(regular, 8.5f, new Color(0xb9, 0xd3, 0xf0), MARGIN, top - 50, eyebrow.toUpperCase());
        List<String> lines = wrap(title, bold, 19, width());
        float ty = top - 74;
        for (String line : lines.subList(0, Math.min(2, lines.size()))) {
            text(bold, 19, Color.WHITE, MARGIN, ty, line);
            ty -= 23;
        }
        y = top - height - 22;
        if (subtitle != null) {
            paragraph(subtitle, 9.5f, MUTED);
        }
        return this;
    }

    public PdfWriter heading(String text) throws IOException {
        ensure(48);
        y -= 12;
        content.setNonStrokingColor(ACCENT);
        content.addRect(MARGIN, y - 2, 3, 15);
        content.fill();
        text(bold, 13, INK, MARGIN + 10, y, text);
        y -= 22;
        return this;
    }

    public PdfWriter paragraph(String text, float size, Color color) throws IOException {
        for (String block : text.split("\n")) {
            for (String line : wrap(block, regular, size, width())) {
                ensure(size * 1.5f);
                text(regular, size, color, MARGIN, y, line);
                y -= size * 1.45f;
            }
        }
        y -= 6;
        return this;
    }

    public PdfWriter paragraph(String text) throws IOException {
        return paragraph(text, 10.5f, INK);
    }

    /** Lista con marcador; {@code markers} permite numerar (prioridad) o usar viñetas. */
    public PdfWriter list(List<String> items, List<String> markers, List<Color> colors) throws IOException {
        for (int i = 0; i < items.size(); i++) {
            String marker = markers == null ? "•" : markers.get(i);
            Color color = colors == null ? ACCENT : colors.get(i);
            List<String> lines = wrap(items.get(i), regular, 10.5f, width() - 22);
            ensure(lines.size() * 15f + 4);
            if ("•".equals(marker)) {
                text(bold, 10.5f, color, MARGIN + 4, y, marker);
            } else {
                content.setNonStrokingColor(color);
                content.addRect(MARGIN, y - 3, 16, 13);
                content.fill();
                text(bold, 8.5f, Color.WHITE, MARGIN + 3, y, marker.substring(0, Math.min(2, marker.length())));
            }
            for (String line : lines) {
                text(regular, 10.5f, INK, MARGIN + 22, y, line);
                y -= 15;
            }
            y -= 3;
        }
        y -= 4;
        return this;
    }

    /** Fila de cifras clave en recuadros. */
    public PdfWriter metrics(List<String[]> items) throws IOException {
        if (items.isEmpty()) {
            return this;
        }
        int perRow = Math.min(4, items.size());
        float gap = 8;
        float boxWidth = (width() - gap * (perRow - 1)) / perRow;
        for (int start = 0; start < items.size(); start += perRow) {
            int end = Math.min(items.size(), start + perRow);
            // Una etiqueta larga pasa a dos líneas en lugar de cortarse; toda la fila crece igual.
            List<List<String>> labels = new ArrayList<>();
            int labelLines = 1;
            for (int i = start; i < end; i++) {
                List<String> lines = wrap(items.get(i)[0].toUpperCase(), regular, 7.5f, boxWidth - 18);
                if (lines.size() > 2) {
                    lines = List.of(lines.get(0), fit(String.join(" ", lines.subList(1, lines.size())), regular, 7.5f, boxWidth - 18));
                }
                labels.add(lines);
                labelLines = Math.max(labelLines, lines.size());
            }
            float extra = (labelLines - 1) * 9.5f;
            ensure(58 + extra);
            for (int i = start; i < end; i++) {
                float x = MARGIN + (i - start) * (boxWidth + gap);
                content.setNonStrokingColor(SURFACE);
                content.addRect(x, y - 42 - extra, boxWidth, 52 + extra);
                content.fill();
                float ly = y - 3;
                for (String line : labels.get(i - start)) {
                    text(regular, 7.5f, MUTED, x + 9, ly, line);
                    ly -= 9.5f;
                }
                text(bold, 14, INK, x + 9, y - 26 - extra, fit(items.get(i)[1], bold, 14, boxWidth - 18));
            }
            y -= 62 + extra;
        }
        return this;
    }

    /** Recuadro con borde de color a la izquierda: avisos, verificación, limitaciones. */
    public PdfWriter callout(String title, List<String> lines, Color color) throws IOException {
        List<String> wrapped = new ArrayList<>();
        for (String line : lines) {
            wrapped.addAll(wrap(line, regular, 9.5f, width() - 24));
        }
        float height = 22 + wrapped.size() * 13.5f;
        ensure(height + 10);
        content.setNonStrokingColor(SURFACE);
        content.addRect(MARGIN, y - height + 10, width(), height);
        content.fill();
        content.setNonStrokingColor(color);
        content.addRect(MARGIN, y - height + 10, 3, height);
        content.fill();
        text(bold, 10, color, MARGIN + 12, y - 4, title);
        float ly = y - 20;
        for (String line : wrapped) {
            text(regular, 9.5f, INK, MARGIN + 12, ly, line);
            ly -= 13.5f;
        }
        y -= height + 8;
        return this;
    }

    /** Cita normativa: organización y sección, texto literal en su idioma y resumen en español. */
    public PdfWriter citation(int number, String source, String quote, String summary, String url) throws IOException {
        List<String> quoteLines = wrap("“" + quote + "”", regular, 9, width() - 30);
        List<String> summaryLines = wrap("Resumen en español (no es traducción oficial): " + summary, regular, 9, width() - 30);
        float height = 34 + (quoteLines.size() + summaryLines.size()) * 12.5f;
        ensure(Math.min(height, 260));
        text(bold, 9.5f, ACCENT, MARGIN, y, number + ".");
        text(bold, 9.5f, INK, MARGIN + 16, y, fit(source, bold, 9.5f, width() - 16));
        y -= 14;
        for (String line : quoteLines) {
            ensure(13);
            text(regular, 9, MUTED, MARGIN + 16, y, line);
            y -= 12.5f;
        }
        for (String line : summaryLines) {
            ensure(13);
            text(regular, 9, INK, MARGIN + 16, y, line);
            y -= 12.5f;
        }
        ensure(13);
        text(regular, 8, ACCENT, MARGIN + 16, y, fit(url, regular, 8, width() - 16));
        y -= 18;
        return this;
    }

    public PdfWriter image(BufferedImage image, String caption) throws IOException {
        float w = width();
        float h = w * image.getHeight() / image.getWidth();
        ensure(h + 24);
        PDImageXObject xo = LosslessFactory.createFromImage(document, image);
        content.drawImage(xo, MARGIN, y - h, w, h);
        y -= h + 6;
        if (caption != null) {
            paragraph(caption, 8.5f, MUTED);
        }
        return this;
    }

    public PdfWriter space(float points) {
        y -= points;
        return this;
    }

    public byte[] finish() throws IOException {
        content.close();
        int total = document.getNumberOfPages();
        for (int i = 0; i < total; i++) {
            PDPage p = document.getPage(i);
            try (PDPageContentStream footerStream = new PDPageContentStream(document, p, PDPageContentStream.AppendMode.APPEND, true)) {
                footerStream.setStrokingColor(LINE);
                footerStream.moveTo(MARGIN, 40);
                footerStream.lineTo(PDRectangle.A4.getWidth() - MARGIN, 40);
                footerStream.stroke();
                writeOn(footerStream, regular, 7.5f, MUTED, MARGIN, 28, fit(footer, regular, 7.5f, width() - 70));
                String number = "Página " + (i + 1) + " de " + total;
                writeOn(footerStream, regular, 7.5f, MUTED,
                    PDRectangle.A4.getWidth() - MARGIN - regular.getStringWidth(clean(number, regular)) / 1000 * 7.5f, 28, number);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.save(out);
        return out.toByteArray();
    }

    @Override
    public void close() throws IOException {
        document.close();
    }

    private void text(PDType0Font font, float size, Color color, float x, float baseline, String text) throws IOException {
        writeOn(content, font, size, color, x, baseline, text);
    }

    private void writeOn(PDPageContentStream stream, PDType0Font font, float size, Color color, float x, float baseline,
                         String text) throws IOException {
        stream.beginText();
        stream.setFont(font, size);
        stream.setNonStrokingColor(color);
        stream.newLineAtOffset(x, baseline);
        stream.showText(clean(text, font));
        stream.endText();
    }

    /** Reemplaza los caracteres que la fuente no tiene (por ejemplo emojis) para que el PDF nunca falle. */
    String clean(String text, PDType0Font font) {
        Map<Integer, Boolean> known = glyphs.computeIfAbsent(font, f -> new HashMap<>());
        StringBuilder out = new StringBuilder();
        text.replace('\t', ' ').replace('\r', ' ').codePoints().forEach(cp -> {
            boolean ok = known.computeIfAbsent(cp, c -> {
                try {
                    font.encode(new String(Character.toChars(c)));
                    return true;
                } catch (IOException | IllegalArgumentException ex) {
                    return false;
                }
            });
            out.append(ok ? new String(Character.toChars(cp)) : "?");
        });
        return out.toString();
    }

    private float measure(String text, PDType0Font font, float size) {
        try {
            return font.getStringWidth(clean(text, font)) / 1000 * size;
        } catch (IOException ex) {
            return text.length() * size * 0.55f;
        }
    }

    List<String> wrap(String text, PDType0Font font, float size, float maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split(" +")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (measure(candidate, font, size) <= maxWidth || line.isEmpty()) {
                if (line.isEmpty() && measure(word, font, size) > maxWidth) {
                    String rest = word;
                    while (measure(rest, font, size) > maxWidth && rest.length() > 1) {
                        int cut = rest.length() - 1;
                        while (cut > 1 && measure(rest.substring(0, cut), font, size) > maxWidth) {
                            cut--;
                        }
                        lines.add(rest.substring(0, cut));
                        rest = rest.substring(cut);
                    }
                    line = new StringBuilder(rest);
                } else {
                    line = new StringBuilder(candidate);
                }
            } else {
                lines.add(line.toString());
                line = new StringBuilder(word);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }

    private String fit(String text, PDType0Font font, float size, float maxWidth) {
        if (measure(text, font, size) <= maxWidth) {
            return text;
        }
        String cut = text;
        while (cut.length() > 1 && measure(cut + "…", font, size) > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "…";
    }
}
