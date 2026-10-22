package com.daliwan.tool.core.doc;

/**
 * 转换失败，消息面向最终用户，可直接展示在弹窗中。
 */
public class ConversionException extends Exception {

    public ConversionException(String message) {
        super(message);
    }

    public ConversionException(String message, Throwable cause) {
        super(message, cause);
    }
}
