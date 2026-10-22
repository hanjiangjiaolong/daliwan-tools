package com.daliwan.tool.core;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

/**
 * 图片与 Base64 / Data URI 的互转。
 *
 * <p>只做纯字节层面的转换与格式识别，不依赖 JavaFX 或 AWT，便于单测覆盖。
 * 编码时格式以文件头（magic）为准，识别不出来才退回扩展名——把 .png 改名成 .jpg
 * 的图在网页里最容易出问题，按内容判断更可靠。
 *
 * <p>解码方向刻意只接受图片：这个页面叫「图片 Base64」，如果解出来是一段可执行文件
 * 或压缩包却提示「已解码」，用户会以为工具认错了，所以宁可报错。
 */
public final class ImageBase64Codec {

    /** 识别不出格式时使用的通用类型。 */
    public static final String BINARY_MIME = "application/octet-stream";

    /** Data URI 前缀，不带 mime 时使用。 */
    private static final String DATA_URI_PREFIX = "data:";

    private ImageBase64Codec() {
    }

    /**
     * 编码结果。
     *
     * @param base64  纯 Base64 文本（不含前缀、不含换行）
     * @param dataUri 带 mime 的 Data URI，可直接塞进 {@code img src}
     * @param mime    识别出的类型
     * @param bytes   原始字节数
     */
    public record Encoded(String base64, String dataUri, String mime, int bytes) {

        /** 编码后比原始数据膨胀的倍数，用于界面提示体积变化。 */
        public double ratio() {
            return bytes == 0 ? 0 : (double) dataUri.length() / bytes;
        }
    }

    /**
     * 解码结果。
     *
     * @param data      原始字节
     * @param mime      识别出的图片类型
     * @param extension 建议的保存扩展名，不含点
     */
    public record Decoded(byte[] data, String mime, String extension) {
    }

    /**
     * 把图片字节编码成 Base64 与 Data URI。
     *
     * @param data     图片字节
     * @param fileName 文件名，仅用于在文件头识别失败时按扩展名兜底，可为 {@code null}
     */
    public static Encoded encode(byte[] data, String fileName) {
        byte[] content = data == null ? new byte[0] : data;
        String mime = sniff(content);
        if (mime == null) {
            mime = mimeOfExtension(fileName);
        }
        String base64 = Base64.getEncoder().encodeToString(content);
        return new Encoded(base64, DATA_URI_PREFIX + mime + ";base64," + base64, mime, content.length);
    }

    /**
     * 解码 Data URI 或纯 Base64。
     *
     * @param text 粘贴内容，允许带换行与空格（常见于 MIME 与 PEM 片段）
     * @throws IllegalArgumentException 内容为空、不是合法 Base64、或解出来不是图片
     */
    public static Decoded decode(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("没有可解码的内容");
        }
        String source = text.strip();
        String declared = null;
        if (source.regionMatches(true, 0, DATA_URI_PREFIX, 0, DATA_URI_PREFIX.length())) {
            int comma = source.indexOf(',');
            if (comma < 0) {
                throw new IllegalArgumentException("Data URI 缺少逗号，无法定位数据部分");
            }
            String meta = source.substring(DATA_URI_PREFIX.length(), comma);
            if (!meta.toLowerCase(Locale.ROOT).contains(";base64")) {
                throw new IllegalArgumentException("只支持 base64 编码的 Data URI，当前是文本编码");
            }
            int semicolon = meta.indexOf(';');
            declared = (semicolon >= 0 ? meta.substring(0, semicolon) : meta).strip();
            source = source.substring(comma + 1);
        }

        String cleaned = source.replaceAll("\\s+", "");
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("Data URI 里没有数据部分");
        }
        byte[] data;
        try {
            data = Base64.getDecoder().decode(cleaned);
        } catch (IllegalArgumentException standardFailed) {
            try {
                data = Base64.getUrlDecoder().decode(cleaned);
            } catch (IllegalArgumentException urlFailed) {
                throw new IllegalArgumentException("不是合法的 Base64：检查是否含非法字符或填充错误");
            }
        }

        String mime = sniff(data);
        if (mime == null && declared != null && declared.toLowerCase(Locale.ROOT).startsWith("image/")) {
            mime = declared.toLowerCase(Locale.ROOT);
        }
        if (mime == null) {
            throw new IllegalArgumentException("解码结果不是可识别的图片格式（支持 PNG / JPEG / GIF / WebP / BMP / TIFF / ICO / SVG）");
        }
        return new Decoded(data, mime, extensionOf(mime));
    }

    /** 按文件头识别图片类型；识别不出返回 {@code null}。 */
    static String sniff(byte[] data) {
        if (data == null || data.length < 4) {
            return null;
        }
        if (startsWith(data, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        if (startsWith(data, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (startsWith(data, 0x47, 0x49, 0x46, 0x38)) {
            return "image/gif";
        }
        if (startsWith(data, 0x42, 0x4D)) {
            return "image/bmp";
        }
        if (startsWith(data, 0x49, 0x49, 0x2A, 0x00) || startsWith(data, 0x4D, 0x4D, 0x00, 0x2A)) {
            return "image/tiff";
        }
        if (startsWith(data, 0x00, 0x00, 0x01, 0x00)) {
            return "image/x-icon";
        }
        if (data.length >= 12 && startsWith(data, 0x52, 0x49, 0x46, 0x46)
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return "image/webp";
        }
        return sniffSvg(data);
    }

    /** SVG 是文本，靠开头几个字符判断。 */
    private static String sniffSvg(byte[] data) {
        int limit = Math.min(data.length, 512);
        String head = new String(data, 0, limit, StandardCharsets.UTF_8).stripLeading();
        if (!head.startsWith("<")) {
            return null;
        }
        String lower = head.toLowerCase(Locale.ROOT);
        if (lower.startsWith("<svg") || (lower.startsWith("<?xml") && lower.contains("<svg"))) {
            return "image/svg+xml";
        }
        return null;
    }

    private static boolean startsWith(byte[] data, int... signature) {
        if (data.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((data[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    /** 按扩展名给出类型，识别不出按二进制处理。 */
    static String mimeOfExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return BINARY_MIME;
        }
        String name = fileName.toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return BINARY_MIME;
        }
        return switch (name.substring(dot + 1)) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "bmp" -> "image/bmp";
            case "tif", "tiff" -> "image/tiff";
            case "ico" -> "image/x-icon";
            case "svg" -> "image/svg+xml";
            default -> BINARY_MIME;
        };
    }

    /** mime 对应的保存扩展名；未知类型用 {@code bin}。 */
    static String extensionOf(String mime) {
        if (mime == null) {
            return "bin";
        }
        return switch (mime.toLowerCase(Locale.ROOT)) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            case "image/bmp" -> "bmp";
            case "image/tiff" -> "tif";
            case "image/x-icon" -> "ico";
            case "image/svg+xml" -> "svg";
            default -> "bin";
        };
    }

    /** 是否为受支持的图片类型（供界面给出友好提示）。 */
    public static boolean isImageMime(String mime) {
        return mime != null && mime.toLowerCase(Locale.ROOT).startsWith("image/");
    }
}
