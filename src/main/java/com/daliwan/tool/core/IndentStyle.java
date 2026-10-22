package com.daliwan.tool.core;

/** 格式化时可选的缩进风格。 */
public enum IndentStyle {

    TWO_SPACES("  ", "2 空格"),
    FOUR_SPACES("    ", "4 空格"),
    TAB("\t", "Tab");

    private final String unit;
    private final String label;

    IndentStyle(String unit, String label) {
        this.unit = unit;
        this.label = label;
    }

    /** 单个缩进层级的字符内容。 */
    public String unit() {
        return unit;
    }

    /** 界面展示名称。 */
    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
