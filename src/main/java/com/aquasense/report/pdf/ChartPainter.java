package com.aquasense.report.pdf;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Draws the report charts with Java2D (no external services) at double resolution, so they look sharp
 * when printed: pH evolution with the ideal range, anomalies, forecast with its interval and distribution by status.
 */
public final class ChartPainter {

    static final int W = 1000;
    static final int H = 380;
    private static final int L = 64;
    private static final int R = 24;
    private static final int T = 24;
    private static final int B = 52;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es-CO"))
        .withZone(ZoneId.of("America/Bogota"));
    private static final DateTimeFormatter DAY_HOUR = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.forLanguageTag("es-CO"))
        .withZone(ZoneId.of("America/Bogota"));
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("America/Bogota"));
    private static final DateTimeFormatter SECOND = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.of("America/Bogota"));

    private static final Font FONT = load("fonts/DejaVuSans.ttf");
    private static final Font BOLD = load("fonts/DejaVuSansBold.ttf");

    private ChartPainter() {
    }

    /** Font bundled with the project: the charts do not depend on the fonts installed on the server. */
    private static Font load(String resource) {
        try (java.io.InputStream in = ChartPainter.class.getClassLoader().getResourceAsStream(resource)) {
            return Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (java.io.IOException | java.awt.FontFormatException | NullPointerException ex) {
            return new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        }
    }

    public record Point(Instant at, double ph) {
    }

    public record Band(Instant at, double value, double low, double high) {
    }

    public static BufferedImage line(List<Point> points, double idealMin, double idealMax, List<Point> marked,
                                     List<Band> forecast) {
        BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = setup(image);
        long t0 = points.get(0).at().toEpochMilli();
        long t1 = forecast.isEmpty() ? points.get(points.size() - 1).at().toEpochMilli() : forecast.get(forecast.size() - 1).at().toEpochMilli();
        if (t1 == t0) {
            t1 = t0 + 3_600_000;
        }
        double min = Math.min(idealMin, points.stream().mapToDouble(Point::ph).min().orElse(idealMin));
        double max = Math.max(idealMax, points.stream().mapToDouble(Point::ph).max().orElse(idealMax));
        for (Band b : forecast) {
            min = Math.min(min, b.low());
            max = Math.max(max, b.high());
        }
        min = Math.floor((min - 0.2) * 2) / 2;
        max = Math.ceil((max + 0.2) * 2) / 2;
        final double lo = Math.max(0, min);
        final double hi = Math.min(14, max);
        final long start = t0;
        final long end = t1;
        java.util.function.DoubleUnaryOperator yOf = v -> T + (hi - v) / (hi - lo) * (H - T - B);
        java.util.function.LongToDoubleFunction xOf = t -> L + (double) (t - start) / (end - start) * (W - L - R);

        g.setColor(new Color(0xdd, 0xf2, 0xe7));
        g.fillRect(L, (int) yOf.applyAsDouble(Math.min(idealMax, hi)), W - L - R,
            (int) (yOf.applyAsDouble(Math.max(idealMin, lo)) - yOf.applyAsDouble(Math.min(idealMax, hi))));
        double step = hi - lo > 4 ? 1 : 0.5;
        g.setFont(FONT.deriveFont(18f));
        for (double v = lo; v <= hi + 1e-9; v += step) {
            int yy = (int) yOf.applyAsDouble(v);
            g.setColor(PdfWriter.LINE);
            g.drawLine(L, yy, W - R, yy);
            g.setColor(PdfWriter.MUTED);
            String label = String.format(Locale.US, "%.1f", v);
            g.drawString(label, L - 10 - g.getFontMetrics().stringWidth(label), yy + 6);
        }
        DateTimeFormatter axis = axisFormat(end - start);
        String previous = null;
        for (int i = 0; i <= 4; i++) {
            long t = start + (end - start) * i / 4;
            String label = axis.format(Instant.ofEpochMilli(t));
            if (label.equals(previous)) {
                continue;
            }
            previous = label;
            int xx = (int) xOf.applyAsDouble(t);
            g.drawString(label, Math.min(W - R - g.getFontMetrics().stringWidth(label), Math.max(L, xx - g.getFontMetrics().stringWidth(label) / 2)), H - 18);
        }
        if (!forecast.isEmpty()) {
            Path2D band = new Path2D.Double();
            Point last = points.get(points.size() - 1);
            band.moveTo(xOf.applyAsDouble(last.at().toEpochMilli()), yOf.applyAsDouble(last.ph()));
            forecast.forEach(b -> band.lineTo(xOf.applyAsDouble(b.at().toEpochMilli()), yOf.applyAsDouble(b.high())));
            for (int i = forecast.size() - 1; i >= 0; i--) {
                band.lineTo(xOf.applyAsDouble(forecast.get(i).at().toEpochMilli()), yOf.applyAsDouble(forecast.get(i).low()));
            }
            band.closePath();
            g.setColor(new Color(0x7a, 0x4f, 0xd0, 50));
            g.fill(band);
            Path2D fl = new Path2D.Double();
            fl.moveTo(xOf.applyAsDouble(last.at().toEpochMilli()), yOf.applyAsDouble(last.ph()));
            forecast.forEach(b -> fl.lineTo(xOf.applyAsDouble(b.at().toEpochMilli()), yOf.applyAsDouble(b.value())));
            g.setColor(new Color(0x7a, 0x4f, 0xd0));
            g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1, new float[] {10, 8}, 0));
            g.draw(fl);
        }
        Path2D path = new Path2D.Double();
        for (int i = 0; i < points.size(); i++) {
            double x = xOf.applyAsDouble(points.get(i).at().toEpochMilli());
            double yy = yOf.applyAsDouble(points.get(i).ph());
            if (i == 0) {
                path.moveTo(x, yy);
            } else {
                path.lineTo(x, yy);
            }
        }
        g.setColor(PdfWriter.ACCENT);
        g.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(path);
        int r = points.size() > 80 ? 3 : 6;
        for (Point p : points) {
            g.fillOval((int) xOf.applyAsDouble(p.at().toEpochMilli()) - r, (int) yOf.applyAsDouble(p.ph()) - r, 2 * r, 2 * r);
        }
        g.setColor(PdfWriter.CRIT);
        g.setStroke(new BasicStroke(3));
        for (Point p : marked) {
            g.drawOval((int) xOf.applyAsDouble(p.at().toEpochMilli()) - 11, (int) yOf.applyAsDouble(p.ph()) - 11, 22, 22);
        }
        g.dispose();
        return image;
    }

    public static BufferedImage bars(List<String> labels, List<Integer> values, List<Color> colors) {
        BufferedImage image = new BufferedImage(W, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = setup(image);
        int max = Math.max(1, values.stream().mapToInt(Integer::intValue).max().orElse(1));
        int slot = (W - L - R) / labels.size();
        g.setFont(BOLD.deriveFont(22f));
        for (int i = 0; i < labels.size(); i++) {
            int bh = (int) ((300 - 110) * (values.get(i) / (double) max));
            int x = L + i * slot + slot / 4;
            g.setColor(colors.get(i));
            g.fillRoundRect(x, 300 - 60 - bh, slot / 2, Math.max(bh, 2), 14, 14);
            g.setColor(PdfWriter.INK);
            String v = String.valueOf(values.get(i));
            g.drawString(v, x + slot / 4 - g.getFontMetrics().stringWidth(v) / 2, 300 - 70 - bh);
            g.setColor(PdfWriter.MUTED);
            g.setFont(FONT.deriveFont(20f));
            g.drawString(labels.get(i), x + slot / 4 - g.getFontMetrics().stringWidth(labels.get(i)) / 2, 300 - 26);
            g.setFont(BOLD.deriveFont(22f));
        }
        g.dispose();
        return image;
    }

    private static Graphics2D setup(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        return g;
    }

    /** The date detail on the axis depends on the period: days, days and hours, hours or seconds. */
    static DateTimeFormatter axisFormat(long spanMs) {
        long minute = 60_000L;
        if (spanMs >= 3 * 24 * 60 * minute) {
            return DAY;
        }
        if (spanMs >= 24 * 60 * minute) {
            return DAY_HOUR;
        }
        return spanMs >= 10 * minute ? HOUR : SECOND;
    }
}
