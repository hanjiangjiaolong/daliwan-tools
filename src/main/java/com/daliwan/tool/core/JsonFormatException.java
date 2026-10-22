package com.daliwan.tool.core;

/**
 * JSON 语法错误，携带出错位置，便于界面直接提示。
 */
public class JsonFormatException extends Exception {

    private final int line;
    private final int column;

    public JsonFormatException(String message, int line, int column) {
        super(message);
        this.line = line;
        this.column = column;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    /** 面向用户的中文提示，含行列号。 */
    public String getFriendlyMessage() {
        return "第 " + line + " 行，第 " + column + " 列：" + getMessage();
    }
}
