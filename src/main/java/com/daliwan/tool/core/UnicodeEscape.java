package com.daliwan.tool.core;

/**
 * 还原 JSON 文本里残留的反斜杠-u 转义。
 *
 * <p>标准 JSON 里的 {@code \u4f70} 解析器会自动还原成「伯」，不用管它；
 * 麻烦的是被再转义过一层的数据——接口日志、把 JSON 当字符串塞进另一个
 * JSON、从程序变量里拷出来的文本里写的是 {@code \\u4f70}，解析出来的
 * 字符串值是 {@code \u4f70} 这六个字符，界面上仍然是一串反斜杠。
 * 这里在解析之前把这种文本还原成正常字符。
 *
 * <p>只认「两个反斜杠 + u + 4 位十六进制」：单层的留给解析器，
 * 普通路径 {@code C:\\users} 里 u 后面不是十六进制，也不会被误伤。
 * 连续的 {@code \\uD83D\\uDE00} 会被合并成一个字符（emoji 的代理对）。
 */
public final class UnicodeEscape {

    private UnicodeEscape() {
    }

    /**
     * 把文本里二次转义的 {@code \\uXXXX} 还原为字符；没有可还原的内容时原样返回。
     */
    public static String decode(String text) {
        if (text == null || text.indexOf('\\') < 0) {
            return text;
        }
        int length = text.length();
        StringBuilder out = new StringBuilder(length);
        // 连续出现的转义先攒着，最后一次性拼成字符串，代理对才能合成一个字符
        StringBuilder pending = new StringBuilder();
        int i = 0;
        while (i < length) {
            if (isEscapedUnit(text, i)) {
                pending.append((char) Integer.parseInt(text.substring(i + 3, i + 7), 16));
                i += 7;
                continue;
            }
            if (pending.length() > 0) {
                out.append(pending);
                pending.setLength(0);
            }
            out.append(text.charAt(i));
            i++;
        }
        if (pending.length() > 0) {
            out.append(pending);
        }
        return out.toString();
    }

    /** 从 {@code index} 起是否是一个 {@code \\uXXXX}。 */
    private static boolean isEscapedUnit(String text, int index) {
        if (index + 6 >= text.length()) {
            return false;
        }
        char marker = text.charAt(index + 2);
        return text.charAt(index) == '\\'
                && text.charAt(index + 1) == '\\'
                && (marker == 'u' || marker == 'U')
                && isHex(text.charAt(index + 3))
                && isHex(text.charAt(index + 4))
                && isHex(text.charAt(index + 5))
                && isHex(text.charAt(index + 6));
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}
