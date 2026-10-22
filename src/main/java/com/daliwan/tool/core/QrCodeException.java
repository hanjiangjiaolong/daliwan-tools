package com.daliwan.tool.core;

/**
 * 二维码生成 / 识别过程中的可预期错误。
 */
public class QrCodeException extends Exception {

    public QrCodeException(String message) {
        super(message);
    }

    public QrCodeException(String message, Throwable cause) {
        super(message, cause);
    }
}
