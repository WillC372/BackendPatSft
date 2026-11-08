package com.aquasense.support;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Imágenes sintéticas para pruebas (un recuadro de color sobre fondo negro). No son fotos reales.
 */
public final class TestImages {

    private TestImages() {
    }

    public static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(new java.awt.Color(110, 185, 70));
        g.fillRect(width / 3, height / 3, width / 3, height / 3);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
