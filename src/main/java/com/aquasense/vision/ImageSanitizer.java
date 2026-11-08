package com.aquasense.vision;

import com.aquasense.common.BusinessException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Normalizes the photo before sending it to the AI: accepts JPEG or PNG, shrinks it to at most 1024 px
 * (fewer tokens and lower cost) and re-encodes it as JPEG. Re-encoding removes the EXIF
 * metadata, including the GPS location. The image is processed in memory and is not stored.
 */
@Component
public class ImageSanitizer {

    static final int MAX_SIDE = 1024;
    static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final int MIN_SIDE = 64;
    /** Limit checked before decoding: a PNG of a few KB can declare 30,000 × 30,000 pixels (decompression bomb). */
    static final long MAX_PIXELS = 50_000_000L;
    static final int MAX_DECLARED_SIDE = 12_000;
    /** Large photos are decoded subsampled so they do not take unnecessary memory. */
    private static final int DECODE_TARGET_SIDE = 2 * MAX_SIDE;

    /**
     * Reads the dimensions from the header first (without decoding the pixels) and rejects disproportionate images.
     * Then it decodes with subsampling, so the memory used does not depend on the declared size.
     */
    static BufferedImage decode(byte[] input) {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
            java.util.Iterator<ImageReader> readers = stream == null ? java.util.Collections.emptyIterator() : ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw unsupported();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_DECLARED_SIDE || height > MAX_DECLARED_SIDE
                    || (long) width * height > MAX_PIXELS) {
                    throw BusinessException.badRequest("La foto es demasiado grande en píxeles. Usa una foto de cámara normal (máximo 50 megapíxeles).");
                }
                ImageReadParam param = reader.getDefaultReadParam();
                int step = Math.max(1, Math.max(width, height) / DECODE_TARGET_SIDE);
                param.setSourceSubsampling(step, step, 0, 0);
                BufferedImage image = reader.read(0, param);
                if (image == null) {
                    throw unsupported();
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof BusinessException business) {
                throw business;
            }
            throw unsupported();
        }
    }

    private static BusinessException unsupported() {
        return new BusinessException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Formato no compatible. Usa una foto JPEG o PNG.");
    }

    public byte[] sanitize(byte[] input) {
        if (input == null || input.length == 0) {
            throw BusinessException.badRequest("Adjunta una foto.");
        }
        if (input.length > MAX_BYTES) {
            throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "La foto supera 10 MB.");
        }
        BufferedImage source = decode(input);
        if (source.getWidth() < MIN_SIDE || source.getHeight() < MIN_SIDE) {
            throw BusinessException.badRequest("La foto es demasiado pequeña para analizarla.");
        }
        double scale = Math.min(1.0, (double) MAX_SIDE / Math.max(source.getWidth(), source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(source, 0, 0, width, height, java.awt.Color.WHITE, null);
        g.dispose();
        return toJpeg(target);
    }

    private static byte[] toJpeg(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(0.85f);
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), params);
            writer.dispose();
            stream.flush();
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not encode image", ex);
        }
    }
}
