package com.daliwan.tool.util;

/**
 * 行号文本。
 *
 * <p>只做一件事：把行号补成一样宽，让右对齐的行号栏看起来是齐的。
 * 放在 {@code util} 里是因为它不需要 JavaFX，可以直接单测。
 *
 * <p>这里刻意不用 {@code String.format("%1$Ns", line)}：
 * 宽度补空格跟地区设置无关，但 {@code String.format} 会按默认地区解析格式串，
 * 换台机器就多一层不确定性，没必要。
 */
public final class LineNumbers {

    private LineNumbers() {
    }

    /**
     * 行号文本，左侧补空格到与总行数同宽。
     *
     * @param line       行号，从 1 开始
     * @param totalLines 当前总行数；{@code 9 → 10} 时第 1 行会自动从 {@code "1"} 变成 {@code " 1"}
     */
    public static String label(int line, int totalLines) {
        int width = String.valueOf(Math.max(totalLines, 1)).length();
        String text = Integer.toString(line);
        return text.length() >= width ? text : " ".repeat(width - text.length()) + text;
    }
}
