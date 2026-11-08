package com.aquasense.vision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquasense.common.BusinessException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ImageSanitizerTest {

    private final ImageSanitizer sanitizer = new ImageSanitizer();

    @Test
    void downscalesLargePhotosAndReencodesAsJpeg() throws Exception {
        byte[] result = sanitizer.sanitize(com.aquasense.support.TestImages.png(3000, 2000));
        assertThat(result[0] & 0xFF).isEqualTo(0xFF);
        assertThat(result[1] & 0xFF).isEqualTo(0xD8);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(result));
        assertThat(decoded.getWidth()).isEqualTo(1024);
        assertThat(decoded.getHeight()).isEqualTo(683);
    }

    @Test
    void rejectsFilesThatAreNotImagesOrTooSmall() throws Exception {
        assertThatThrownBy(() -> sanitizer.sanitize("no soy una imagen".getBytes())).isInstanceOf(BusinessException.class)
            .hasMessageContaining("JPEG o PNG");
        assertThatThrownBy(() -> sanitizer.sanitize(com.aquasense.support.TestImages.png(20, 20))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> sanitizer.sanitize(new byte[0])).isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsDecompressionBombsBeforeDecodingPixels() throws Exception {
        long start = System.nanoTime();
        assertThatThrownBy(() -> sanitizer.sanitize(pngHeader(30_000, 30_000))).isInstanceOf(BusinessException.class)
            .hasMessageContaining("demasiado grande");
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(2000);
    }

    /** Minimal PNG (signature, IHDR and IEND) that only declares its dimensions: a few bytes. */
    private static byte[] pngHeader(int width, int height) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'});
        java.nio.ByteBuffer ihdr = java.nio.ByteBuffer.allocate(13).putInt(width).putInt(height)
            .put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0);
        chunk(out, "IHDR", ihdr.array());
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void chunk(java.io.ByteArrayOutputStream out, String type, byte[] data) throws Exception {
        out.write(java.nio.ByteBuffer.allocate(4).putInt(data.length).array());
        byte[] typeBytes = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        out.write(typeBytes);
        out.write(data);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(typeBytes);
        crc.update(data);
        out.write(java.nio.ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }
}
