package com.daliwan.tool.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PngEncoderTest {

    @Test
    @DisplayName("开头八字节必须是 PNG 签名")
    void startsWithSignature() {
        byte[] png = PngEncoder.encode(new int[]{0xFF112233}, 1, 1);
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', (byte) 0x1A, '\n'};
        assertArrayEquals(signature, java.util.Arrays.copyOf(png, 8));
    }

    @Test
    @DisplayName("编码结果能被标准解码器读回，像素逐点一致")
    void roundTripsThroughImageIo() throws IOException {
        int width = 5;
        int height = 3;
        int[] pixels = new int[width * height];
        for (int i = 0; i < pixels.length; i++) {
            // 故意混进半透明与纯黑纯白，覆盖 alpha 通道
            pixels[i] = switch (i % 4) {
                case 0 -> 0xFF000000;
                case 1 -> 0xFFFFFFFF;
                case 2 -> 0x80112233;
                default -> 0x00FF0000;
            };
        }

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(PngEncoder.encode(pixels, width, height)));
        assertEquals(width, decoded.getWidth());
        assertEquals(height, decoded.getHeight());

        int[] back = new int[width * height];
        decoded.getRGB(0, 0, width, height, back, 0, width);
        assertArrayEquals(pixels, back, "PNG 必须是无损的：alpha 与颜色都要逐点还原");
    }

    @Test
    @DisplayName("可以直接写文件，文件大小合理")
    void writesFile(@TempDir Path dir) throws IOException {
        int[] pixels = new int[64 * 64];
        java.util.Arrays.fill(pixels, 0xFF3366CC);

        Path file = dir.resolve("out.png");
        PngEncoder.write(file, pixels, 64, 64);
        assertTrue(Files.size(file) > 0, "文件不能是空的");
        assertEquals(64, ImageIO.read(file.toFile()).getWidth());
    }

    @Test
    @DisplayName("尺寸或像素不足时直接报错，而不是产出坏图")
    void rejectsBadInput() {
        assertThrows(IllegalArgumentException.class, () -> PngEncoder.encode(new int[4], 0, 4));
        assertThrows(IllegalArgumentException.class, () -> PngEncoder.encode(new int[2], 4, 4));
    }
}
