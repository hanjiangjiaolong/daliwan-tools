package com.daliwan.tool.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageBase64CodecTest {

    /** 1×1 的透明 PNG。 */
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Test
    @DisplayName("编码 PNG 得到 image/png 与 data URI")
    void encodesPng() {
        ImageBase64Codec.Encoded encoded = ImageBase64Codec.encode(PNG, "dot.png");

        assertEquals("image/png", encoded.mime());
        assertEquals(PNG.length, encoded.bytes());
        assertTrue(encoded.base64().startsWith("iVBORw0KGgo"));
        assertEquals("data:image/png;base64," + encoded.base64(), encoded.dataUri());
    }

    @Test
    @DisplayName("文件头优先于扩展名")
    void magicWinsOverExtension() {
        ImageBase64Codec.Encoded encoded = ImageBase64Codec.encode(PNG, "其实是png.jpg");

        assertEquals("image/png", encoded.mime());
    }

    @Test
    @DisplayName("识别不出文件头时按扩展名兜底")
    void fallsBackToExtension() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8);
        assertEquals("image/svg+xml", ImageBase64Codec.encode(svg, "icon.svg").mime());

        byte[] unknown = new byte[]{0x01, 0x02, 0x03, 0x04};
        assertEquals(ImageBase64Codec.BINARY_MIME, ImageBase64Codec.encode(unknown, "a.bin").mime());
        assertEquals(ImageBase64Codec.BINARY_MIME, ImageBase64Codec.encode(unknown, null).mime());
    }

    @Test
    @DisplayName("解码 Data URI")
    void decodesDataUri() {
        String dataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(PNG);

        ImageBase64Codec.Decoded decoded = ImageBase64Codec.decode(dataUri);

        assertArrayEquals(PNG, decoded.data());
        assertEquals("image/png", decoded.mime());
        assertEquals("png", decoded.extension());
    }

    @Test
    @DisplayName("纯 Base64 且带换行也能解码")
    void decodesPlainWithLineBreaks() {
        String base64 = Base64.getEncoder().encodeToString(PNG);
        String wrapped = base64.substring(0, 20) + "\r\n" + base64.substring(20);

        assertArrayEquals(PNG, ImageBase64Codec.decode(wrapped).data());
    }

    @Test
    @DisplayName("编码再解码得到原始字节")
    void roundTrip() {
        String dataUri = ImageBase64Codec.encode(PNG, "x.png").dataUri();

        assertArrayEquals(PNG, ImageBase64Codec.decode(dataUri).data());
    }

    @Test
    @DisplayName("解出非图片内容时明确报错")
    void rejectsNonImage() {
        String base64 = Base64.getEncoder().encodeToString("hello world".getBytes(StandardCharsets.UTF_8));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ImageBase64Codec.decode(base64));
        assertTrue(error.getMessage().contains("不是可识别的图片"));
    }

    @Test
    @DisplayName("非法 Base64 给出可读提示")
    void rejectsBrokenBase64() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ImageBase64Codec.decode("@@@@"));
        assertTrue(error.getMessage().contains("Base64"));
    }

    @Test
    @DisplayName("空内容直接拒绝")
    void rejectsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> ImageBase64Codec.decode("   "));
        assertThrows(IllegalArgumentException.class, () -> ImageBase64Codec.decode(null));
    }

    @Test
    @DisplayName("Data URI 缺少逗号时提示清楚")
    void rejectsDataUriWithoutComma() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ImageBase64Codec.decode("data:image/png;base64"));
        assertTrue(error.getMessage().contains("逗号"));
    }

    @Test
    @DisplayName("非 base64 的 Data URI 被拒绝")
    void rejectsUrlEncodedDataUri() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ImageBase64Codec.decode("data:image/svg+xml,%3Csvg%3E"));
        assertTrue(error.getMessage().contains("base64"));
    }

    @Test
    @DisplayName("SVG Data URI 按文本解码")
    void decodesSvgDataUri() {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1\" height=\"1\"/>";
        String dataUri = "data:image/svg+xml;base64,"
                + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));

        ImageBase64Codec.Decoded decoded = ImageBase64Codec.decode(dataUri);

        assertEquals("image/svg+xml", decoded.mime());
        assertEquals("svg", decoded.extension());
        assertEquals(svg, new String(decoded.data(), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("WebP 需要同时匹配 RIFF 与 WEBP")
    void sniffsWebp() {
        byte[] riff = new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};
        assertEquals("image/webp", ImageBase64Codec.encode(riff, "a.webp").mime());

        byte[] riffOnly = new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'A', 'V', 'I', ' '};
        assertEquals(ImageBase64Codec.BINARY_MIME, ImageBase64Codec.encode(riffOnly, "a.bin").mime());
    }

    @Test
    @DisplayName("各扩展名映射到正确类型")
    void extensionMapping() {
        assertEquals("image/jpeg", ImageBase64Codec.mimeOfExtension("a.JPEG"));
        assertEquals("image/tiff", ImageBase64Codec.mimeOfExtension("a.tif"));
        assertEquals("image/x-icon", ImageBase64Codec.mimeOfExtension("favicon.ico"));
        assertEquals(ImageBase64Codec.BINARY_MIME, ImageBase64Codec.mimeOfExtension("noext"));
        assertEquals("jpg", ImageBase64Codec.extensionOf("image/jpeg"));
        assertEquals("bin", ImageBase64Codec.extensionOf("application/pdf"));
    }

    @Test
    @DisplayName("编码后体积倍数有值可依")
    void ratioIsMeasurable() {
        ImageBase64Codec.Encoded encoded = ImageBase64Codec.encode(PNG, "dot.png");

        assertTrue(encoded.ratio() > 1.0);
    }
}
