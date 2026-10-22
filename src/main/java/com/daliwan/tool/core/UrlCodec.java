package com.daliwan.tool.core;

import java.io.ByteArrayOutputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Locale;

/**
 * URL 百分号编码。
 *
 * <p>提供两套规则：
 * <ul>
 *   <li><b>表单编码</b>（application/x-www-form-urlencoded）：空格编码为 {@code +}，
 *       与 {@code java.net.URLEncoder} 行为一致，适用于查询参数</li>
 *   <li><b>RFC 3986</b>：空格编码为 {@code %20}，仅保留 {@code A-Z a-z 0-9 - _ . ~}，
 *       适用于路径片段与需要严格合规的场景</li>
 * </ul>
 */
public final class UrlCodec {

    /** 编码规则。 */
    public enum Rule {
        FORM("表单编码（空格 → +）"),
        RFC3986("RFC 3986（空格 → %20）");

        private final String label;

        Rule(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final String UNRESERVED =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";

    private UrlCodec() {
    }

    /** 按指定规则编码。 */
    public static String encode(String text, Rule rule, Charset charset) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return rule == Rule.FORM ? encodeForm(text, charset) : encodeRfc3986(text, charset);
    }

    /** 按指定规则解码。 */
    public static String decode(String text, Rule rule, Charset charset) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return rule == Rule.FORM ? decodeForm(text, charset) : decodeRfc3986(text, charset);
    }

    /** 表单编码：空格转为 {@code +}。 */
    public static String encodeForm(String text, Charset charset) {
        return URLEncoder.encode(text, charset);
    }

    /** RFC 3986 编码：空格转为 {@code %20}，百分号后使用大写十六进制。 */
    public static String encodeRfc3986(String text, Charset charset) {
        StringBuilder out = new StringBuilder(text.length() * 2);
        for (byte b : text.getBytes(charset)) {
            int value = b & 0xFF;
            char c = (char) value;
            if (value < 0x80 && UNRESERVED.indexOf(c) >= 0) {
                out.append(c);
            } else {
                out.append('%').append(String.format(Locale.ROOT, "%02X", value));
            }
        }
        return out.toString();
    }

    /** 表单解码：{@code +} 视为空格。 */
    public static String decodeForm(String text, Charset charset) {
        return URLDecoder.decode(text, charset);
    }

    /** RFC 3986 解码：{@code +} 保持原样，仅处理百分号转义。 */
    public static String decodeRfc3986(String text, Charset charset) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(text.length());
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '%' && i + 2 < text.length()) {
                int high = Character.digit(text.charAt(i + 1), 16);
                int low = Character.digit(text.charAt(i + 2), 16);
                if (high >= 0 && low >= 0) {
                    out.write((high << 4) + low);
                    i += 3;
                    continue;
                }
            }
            byte[] bytes = String.valueOf(c).getBytes(charset);
            out.write(bytes, 0, bytes.length);
            i++;
        }
        return new String(out.toByteArray(), charset);
    }
}
