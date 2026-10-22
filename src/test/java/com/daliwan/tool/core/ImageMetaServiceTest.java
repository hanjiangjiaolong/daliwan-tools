package com.daliwan.tool.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageMetaServiceTest {

    @Test
    @DisplayName("读取 PNG 的尺寸与格式")
    void readsPngDimensions(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("sample.png");
        BufferedImage image = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image, "png", file.toFile());

        ImageMetaService.Info info = ImageMetaService.read(file);
        assertEquals(40, info.width());
        assertEquals(30, info.height());
        assertTrue(info.format().toLowerCase().contains("png"));
        assertEquals("sample.png", info.fileName());
        assertTrue(info.fileSize() > 0);
    }

    @Test
    @DisplayName("概要分组排在最前并包含尺寸")
    void summarySectionComesFirst(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("sample.png");
        ImageIO.write(new BufferedImage(20, 10, BufferedImage.TYPE_INT_ARGB), "png", file.toFile());

        ImageMetaService.Info info = ImageMetaService.read(file);
        List<ImageMetaService.Section> sections = info.sections();
        assertEquals("概要", sections.get(0).name());
        String summary = ImageMetaService.toText(info);
        assertTrue(summary.contains("20 × 10"), summary);
        assertTrue(summary.contains("1:2") || summary.contains("2:1"), summary);
    }

    @Test
    @DisplayName("读取 JPEG 里的 EXIF Make / Model 标签")
    void readsExifFromJpeg(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("photo.jpg");
        Files.write(file, jpegWithExifMake("Canon"));

        ImageMetaService.Info info = ImageMetaService.read(file);
        assertEquals("Canon", info.camera().trim());
        assertTrue(info.sections().stream()
                .anyMatch(s -> s.entries().stream()
                        .anyMatch(e -> "Make".equals(e.tag()) && e.value().contains("Canon"))));
    }

    @Test
    @DisplayName("非图片文件抛出 IOException")
    void rejectsNonImage(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("notes.txt");
        Files.writeString(file, "这不是图片");
        assertThrows(java.io.IOException.class, () -> ImageMetaService.read(file));
    }

    @Test
    @DisplayName("文件大小格式化")
    void formatsSize() {
        assertEquals("512 B", ImageMetaService.formatSize(512));
        assertEquals("1.0 KB", ImageMetaService.formatSize(1024));
        assertEquals("1.50 MB", ImageMetaService.formatSize((long) (1.5 * 1024 * 1024)));
        assertFalse(ImageMetaService.formatSize(3L * 1024 * 1024 * 1024).isEmpty());
    }

    /** 拼一个带 EXIF APP1 段（仅 Make 标签）的最小 JPEG。 */
    private static byte[] jpegWithExifMake(String make) throws Exception {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream jpegOut = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", jpegOut);
        byte[] jpeg = jpegOut.toByteArray();

        byte[] tiff = new byte[32];
        tiff[0] = 'I';
        tiff[1] = 'I';
        tiff[2] = 0x2A;
        tiff[3] = 0x00;
        tiff[4] = 0x08;
        tiff[8] = 0x01;                 // IFD0 有 1 个条目
        tiff[10] = 0x0F;
        tiff[11] = 0x01;                // tag 0x010F Make
        tiff[12] = 0x02;                // type ASCII
        tiff[14] = (byte) (make.length() + 1);
        tiff[18] = 0x1A;                // 值存放在偏移 26
        byte[] value = (make + "\0").getBytes("ASCII");
        System.arraycopy(value, 0, tiff, 26, value.length);

        byte[] app1 = new byte[6 + tiff.length];
        app1[0] = 'E';
        app1[1] = 'x';
        app1[2] = 'i';
        app1[3] = 'f';
        app1[4] = 0;
        app1[5] = 0;
        System.arraycopy(tiff, 0, app1, 6, tiff.length);

        int segLength = app1.length + 2;
        byte[] out = new byte[2 + 4 + app1.length + (jpeg.length - 2)];
        out[0] = (byte) 0xFF;
        out[1] = (byte) 0xD8;           // SOI
        out[2] = (byte) 0xFF;
        out[3] = (byte) 0xE1;           // APP1
        out[4] = (byte) (segLength >> 8);
        out[5] = (byte) segLength;
        System.arraycopy(app1, 0, out, 6, app1.length);
        System.arraycopy(jpeg, 2, out, 6 + app1.length, jpeg.length - 2);
        return out;
    }
}
