package com.daliwan.tool.core;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Unicode 转义与还原。
 *
 * <p>转义格式为 Java / JavaScript 通用的 Unicode 转义序列（反斜杠 + u + 四位十六进制），按 char 处理，
 * 因此 emoji 等增补平面字符会输出由两个转义单元组成的代理对，可原样还原。
 */
public final class UnicodeCodec {

    /** 转义范围。 */
    public enum Scope {
        NON_ASCII("仅非 ASCII"),
        ALL("全部字符");

        private final String label;

        Scope(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** 十六进制字母大小写。 */
    public enum LetterCase {
        LOWER("小写 \\u4e2d"),
        UPPER("大写 \\u4E2D");

        private final String label;

        LetterCase(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final Pattern ESCAPE_PATTERN = Pattern.compile("\\\\u([0-9a-fA-F]{4})");

    private UnicodeCodec() {
    }

    /**
     * 转义为 Unicode 转义序列。
     *
     * @param text 原始文本
     * @param scope 转义范围
     * @param letterCase 十六进制字母大小写
     */
    public static String escape(String text, Scope scope, LetterCase letterCase) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String format = letterCase == LetterCase.UPPER ? "\\u%04X" : "\\u%04x";
        StringBuilder out = new StringBuilder(text.length() * 2);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean needsEscape = scope == Scope.ALL || c > 0x7F;
            if (needsEscape) {
                out.append(String.format(Locale.ROOT, format, (int) c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * 还原 Unicode 转义序列形式的文本；非法或残缺序列原样保留。
     */
    public static String unescape(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        Matcher matcher = ESCAPE_PATTERN.matcher(text);
        StringBuilder out = new StringBuilder(text.length());
        while (matcher.find()) {
            int code = Integer.parseInt(matcher.group(1), 16);
            matcher.appendReplacement(out, Matcher.quoteReplacement(String.valueOf((char) code)));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
