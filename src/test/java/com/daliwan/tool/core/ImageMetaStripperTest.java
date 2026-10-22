package com.daliwan.tool.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageMetaStripperTest {

    @Test
    @DisplayName("JPEG：相机信息被删除，方向标记按默认策略保留")
    void stripsJpegExifButKeepsOrientation(@TempDir Path dir) throws Exception {
        Path source = dir.resolve("photo.jpg");
        Files.write(source, jpegWithExif("Canon", 6));
        Path target = dir.resolve("clean.jpg");

        ImageMetaStripper.Report report =
                ImageMetaStripper.strip(source, target, ImageMetaStripper.Options.DEFAULT);

        assertEquals("JPEG", report.format());
        assertFalse(report.reencoded(), "JPEG 应当无损剪裁");
        assertTrue(report.removed().stream().anyMatch(text -> text.contains("EXIF")), report.removed().toString());

        ImageMetaService.Info after = ImageMetaService.read(target);
        assertTrue(after.camera().isBlank(), "相机信息应被清除，实际：" + after.camera());
        String text = ImageMetaService.toText(after);
        assertFalse(text.contains("Canon"), text);
        assertTrue(text.contains("6"), "方向标记应保留：" + text);
        // 像素数据没动，尺寸不变
        assertEquals(8, after.width());
        assertEquals(8, after.height());
    }

    @Test
    @DisplayName("JPEG：选择丢弃方向后不再有任何 EXIF")
    void dropsJpegExifEntirely(@TempDir Path dir) throws Exception {
        Path source = dir.resolve("photo.jpg");
        Files.write(source, jpegWithExif("Nikon", 8));
        Path target = dir.resolve("clean.jpg");

        ImageMetaStripper.strip(source, target,
                new ImageMetaStripper.Options(true, ImageMetaStripper.OrientationMode.DROP));

        ImageMetaService.Info after = ImageMetaService.read(target);
        assertTrue(after.camera().isBlank());
        assertTrue(after.sections().stream().noneMatch(section -> section.name().contains("EXIF")),
                ImageMetaService.toText(after));
        // 去掉 EXIF 后应当更小
        assertTrue(Files.size(target) < Files.size(source));
    }

    @Test
    @DisplayName("JPEG：ICC 色彩配置可保留也可移除")
    void keepsOrDropsIccProfile(@TempDir Path dir) throws Exception {
        Path source = dir.resolve("wide.jpg");
        Files.write(source, jpegWithIcc());
        Path keep = dir.resolve("keep.jpg");
        Path drop = dir.resolve("drop.jpg");

        ImageMetaStripper.strip(source, keep, new ImageMetaStripper.Options(true, ImageMetaStripper.OrientationMode.KEEP));
        ImageMetaStripper.Report dropped = ImageMetaStripper.strip(source, drop,
                new ImageMetaStripper.Options(false, ImageMetaStripper.OrientationMode.KEEP));

        assertTrue(contains(keep, "ICC_PROFILE"), "保留 ICC 时应原样留在文件里");
        assertFalse(contains(drop, "ICC_PROFILE"), "关闭保留后应被移除");
        assertTrue(dropped.removed().stream().anyMatch(text -> text.contains("ICC")), dropped.removed().toString());
        assertTrue(dropped.warnings().stream().anyMatch(text -> text.contains("色偏")), dropped.warnings().toString());
    }

    @Test
    @DisplayName("PNG：文本块被删除，图片仍可正常解码")
    void stripsPngTextChunks(@TempDir Path dir) throws Exception {
        Path source = dir.resolve("shot.png");
        Files.write(source, pngWithText("Software", "Photoshop 25.0"));
        Path target = dir.resolve("clean.png");

        ImageMetaStripper.Report report =
                ImageMetaStripper.strip(source, target, ImageMetaStripper.Options.DEFAULT);

        assertEquals("PNG", report.format());
        assertTrue(report.removed().stream().anyMatch(text -> text.contains("tEXt")), report.removed().toString());
        assertFalse(contains(target, "Photoshop"), "文本内容不应留在文件里");

        BufferedImage image = ImageIO.read(target.toFile());
        assertEquals(12, image.getWidth());
        assertEquals(9, image.getHeight());
    }

    @Test
    @DisplayName("PNG：eXIf 里的方向标记按默认策略保留")
    void keepsPngOrientation(@TempDir Path dir) throws Exception {
        Path source = dir.resolve("rotated.png");
        Files.write(source, pngWithExif(6));
        Path target = dir.resolve("clean.png");

        ImageMetaStripper.strip(source, target, ImageMetaStripper.Options.DEFAULT);

        ImageMetaService.Info after = ImageMetaService.read(target);
        assertTrue(ImageMetaService.toText(after).contains("Orientation"), ImageMetaService.toText(after));
        assertEquals(12, ImageIO.read(target.toFile()).getWidth());
    }

    @Test
    @DisplayName("WebP：EXIF / XMP 块被移除且 RIFF 长度重算正确")
    void stripsWebpChunks(@TempDir Path dir) throws Exception {
        Path source = dir.resolve("pic.webp");
        Files.write(source, webpWithMetadata());
        Path target = dir.resolve("clean.webp");

        ImageMetaStripper.Report report =
                ImageMetaStripper.strip(source, target, ImageMetaStripper.Options.DEFAULT);

        assertEquals("WebP", report.format());
        byte[] cleaned = Files.readAllBytes(target);
        assertFalse(contains(target, "XMP "), "XMP 块应被移除");
        // RIFF 长度字段 = 总长 - 8
        int riffSize = (cleaned[4] & 0xFF) | ((cleaned[5] & 0xFF) << 8)
                | ((cleaned[6] & 0xFF) << 16) | ((cleaned[7] & 0xFF) << 24);
        assertEquals(cleaned.length - 8, riffSize);
    }

    @Test
    @DisplayName("GIF：注释扩展被删除，帧数据保留")
    void stripsGifComment(@TempDir Path dir) throws Exception {
        Path source = dir.resolve("anim.gif");
        Files.write(source, gifWithComment("author: somebody"));
        Path target = dir.resolve("clean.gif");

        ImageMetaStripper.Report report =
                ImageMetaStripper.strip(source, target, ImageMetaStripper.Options.DEFAULT);

        assertEquals("GIF", report.format());
        assertTrue(report.removed().stream().anyMatch(text -> text.contains("注释")), report.removed().toString());
        assertFalse(contains(target, "somebody"));
        BufferedImage image = ImageIO.read(target.toFile());
        assertEquals(2, image.getWidth());
        assertEquals(2, image.getHeight());
    }

    @Test
    @DisplayName("BMP 等格式退回重编码并如实标记")
    void fallsBackToReencode(@TempDir Path dir) throws Exception {
        Path source = dir.resolve("plain.bmp");
        ImageIO.write(new BufferedImage(16, 10, BufferedImage.TYPE_INT_RGB), "bmp", source.toFile());
        Path target = dir.resolve("clean.bmp");

        ImageMetaStripper.Report report =
                ImageMetaStripper.strip(source, target, ImageMetaStripper.Options.DEFAULT);

        assertTrue(report.reencoded());
        assertTrue(report.warnings().stream().anyMatch(text -> text.contains("重新编码")), report.warnings().toString());
        BufferedImage image = ImageIO.read(target.toFile());
        assertEquals(16, image.getWidth());
        assertEquals(10, image.getHeight());
    }

    @Test
    @DisplayName("原地覆盖也走临时文件，结果一致")
    void overwritesSourceSafely(@TempDir Path dir) throws Exception {
        Path source = dir.resolve("photo.jpg");
        byte[] original = jpegWithExif("Sony", 1);
        Files.write(source, original);

        ImageMetaStripper.Report report =
                ImageMetaStripper.strip(source, source, ImageMetaStripper.Options.DEFAULT);

        assertEquals(source, report.target());
        assertFalse(contains(source, "Sony"));
        assertEquals(8, ImageIO.read(source.toFile()).getWidth());
    }

    @Test
    @DisplayName("非图片文件抛出 IOException")
    void rejectsNonImage(@TempDir Path dir) throws Exception {
        Path source = dir.resolve("notes.txt");
        Files.writeString(source, "这不是图片");
        Path target = dir.resolve("out.txt");
        assertThrows(java.io.IOException.class,
                () -> ImageMetaStripper.strip(source, target, ImageMetaStripper.Options.DEFAULT));
    }

    // ---------------------------------------------------------------- 构造样本

    /** 拼一个只带 Make + Orientation 的 JPEG。 */
    private static byte[] jpegWithExif(String make, int orientation) throws Exception {
        byte[] jpeg = plainJpeg();
        byte[] app1 = exifApp1(make, orientation);
        byte[] out = new byte[2 + app1.length + (jpeg.length - 2)];
        out[0] = (byte) 0xFF;
        out[1] = (byte) 0xD8;
        System.arraycopy(app1, 0, out, 2, app1.length);
        System.arraycopy(jpeg, 2, out, 2 + app1.length, jpeg.length - 2);
        return out;
    }

    /** 拼一个带 ICC APP2 段的 JPEG。 */
    private static byte[] jpegWithIcc() throws Exception {
        byte[] jpeg = plainJpeg();
        byte[] payload = new byte[6 + 128];
        System.arraycopy("ICC_PROFILE\0".getBytes(StandardCharsets.ISO_8859_1), 0, payload, 0, 12);
        payload[12] = 1;                        // 序号
        payload[13] = 1;                        // 总数
        for (int i = 14; i < payload.length; i++) {
            payload[i] = (byte) i;
        }
        int length = payload.length + 2;
        byte[] out = new byte[2 + 4 + payload.length + (jpeg.length - 2)];
        out[0] = (byte) 0xFF;
        out[1] = (byte) 0xD8;
        out[2] = (byte) 0xFF;
        out[3] = (byte) 0xE2;
        out[4] = (byte) (length >> 8);
        out[5] = (byte) length;
        System.arraycopy(payload, 0, out, 6, payload.length);
        System.arraycopy(jpeg, 2, out, 6 + payload.length, jpeg.length - 2);
        return out;
    }

    private static byte[] plainJpeg() throws Exception {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    private static byte[] exifApp1(String make, int orientation) {
        byte[] makeBytes = (make + "\0").getBytes(StandardCharsets.US_ASCII);
        int makeOffset = 38;
        byte[] tiff = new byte[38 + makeBytes.length];
        tiff[0] = 'I';
        tiff[1] = 'I';
        tiff[2] = 42;
        tiff[3] = 0;
        tiff[4] = 8;                           // IFD0 偏移
        tiff[8] = 2;                           // 两个条目
        // 条目 1：Make（0x010F，ASCII）
        tiff[10] = 0x0F;
        tiff[11] = 0x01;
        tiff[12] = 2;
        tiff[14] = (byte) makeBytes.length;
        tiff[18] = (byte) (makeOffset & 0xFF);
        tiff[19] = (byte) ((makeOffset >> 8) & 0xFF);
        // 条目 2：Orientation（0x0112，SHORT）
        tiff[22] = 0x12;
        tiff[23] = 0x01;
        tiff[24] = 3;
        tiff[26] = 1;
        tiff[30] = (byte) orientation;
        System.arraycopy(makeBytes, 0, tiff, makeOffset, makeBytes.length);

        byte[] payload = new byte[6 + tiff.length];
        System.arraycopy("Exif\0\0".getBytes(StandardCharsets.ISO_8859_1), 0, payload, 0, 6);
        System.arraycopy(tiff, 0, payload, 6, tiff.length);

        int length = payload.length + 2;
        byte[] app1 = new byte[4 + payload.length];
        app1[0] = (byte) 0xFF;
        app1[1] = (byte) 0xE1;
        app1[2] = (byte) (length >> 8);
        app1[3] = (byte) length;
        System.arraycopy(payload, 0, app1, 4, payload.length);
        return app1;
    }

    private static byte[] pngWithText(String key, String value) throws Exception {
        byte[] png = plainPng();
        byte[] chunk = pngChunk("tEXt", (key + "\0" + value).getBytes(StandardCharsets.ISO_8859_1));
        return insertAfterHeader(png, chunk);
    }

    private static byte[] pngWithExif(int orientation) throws Exception {
        byte[] png = plainPng();
        byte[] tiff = new byte[26];
        tiff[0] = 'M';
        tiff[1] = 'M';
        tiff[3] = 42;
        tiff[7] = 8;
        tiff[9] = 1;
        tiff[10] = 0x01;
        tiff[11] = 0x12;
        tiff[13] = 3;
        tiff[17] = 1;
        tiff[18] = (byte) (orientation >> 8);
        tiff[19] = (byte) orientation;
        return insertAfterHeader(png, pngChunk("eXIf", tiff));
    }

    private static byte[] plainPng() throws Exception {
        BufferedImage image = new BufferedImage(12, 9, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /** 插到 IHDR 之后：签名 8 + IHDR 块 25 = 33。 */
    private static byte[] insertAfterHeader(byte[] png, byte[] chunk) {
        byte[] out = new byte[png.length + chunk.length];
        System.arraycopy(png, 0, out, 0, 33);
        System.arraycopy(chunk, 0, out, 33, chunk.length);
        System.arraycopy(png, 33, out, 33 + chunk.length, png.length - 33);
        return out;
    }

    private static byte[] pngChunk(String type, byte[] payload) {
        byte[] name = type.getBytes(StandardCharsets.ISO_8859_1);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeInt(out, payload.length);
        out.write(name, 0, 4);
        out.write(payload, 0, payload.length);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(payload);
        writeInt(out, (int) crc.getValue());
        return out.toByteArray();
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static byte[] webpWithMetadata() {
        byte[] vp8x = new byte[10];
        vp8x[0] = 0x08 | 0x04;                 // 声明含 EXIF + XMP
        byte[] exif = new byte[26];
        exif[0] = 'M';
        exif[1] = 'M';
        exif[3] = 42;
        exif[7] = 8;
        exif[9] = 1;
        exif[10] = 0x01;
        exif[11] = 0x12;
        exif[13] = 3;
        exif[17] = 1;
        exif[19] = 6;

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(webpChunk("VP8X", vp8x));
        body.writeBytes(webpChunk("EXIF", exif));
        body.writeBytes(webpChunk("XMP ", "<x:xmpmeta/>".getBytes(StandardCharsets.ISO_8859_1)));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("RIFF".getBytes(StandardCharsets.ISO_8859_1));
        writeLittleInt(out, body.size() + 4);
        out.writeBytes("WEBP".getBytes(StandardCharsets.ISO_8859_1));
        out.writeBytes(body.toByteArray());
        return out.toByteArray();
    }

    private static byte[] webpChunk(String fourcc, byte[] payload) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(fourcc.getBytes(StandardCharsets.ISO_8859_1));
        writeLittleInt(out, payload.length);
        out.write(payload, 0, payload.length);
        if ((payload.length & 1) == 1) {
            out.write(0);
        }
        return out.toByteArray();
    }

    private static void writeLittleInt(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 24) & 0xFF);
    }

    private static byte[] gifWithComment(String comment) throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ImageIO.write(image, "gif", buffer);
        byte[] gif = buffer.toByteArray();

        int packed = gif[10] & 0xFF;
        int position = 13;
        if ((packed & 0x80) != 0) {
            position += 3 * (1 << ((packed & 0x07) + 1));
        }

        ByteArrayOutputStream ext = new ByteArrayOutputStream();
        ext.write(0x21);
        ext.write(0xFE);
        byte[] text = comment.getBytes(StandardCharsets.ISO_8859_1);
        for (int offset = 0; offset < text.length; offset += 255) {
            int size = Math.min(255, text.length - offset);
            ext.write(size);
            ext.write(text, offset, size);
        }
        ext.write(0);

        byte[] commentBlock = ext.toByteArray();
        byte[] out = new byte[gif.length + commentBlock.length];
        System.arraycopy(gif, 0, out, 0, position);
        System.arraycopy(commentBlock, 0, out, position, commentBlock.length);
        System.arraycopy(gif, position, out, position + commentBlock.length, gif.length - position);
        return out;
    }

    private static boolean contains(Path file, String text) throws Exception {
        return new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1).contains(text);
    }
}
