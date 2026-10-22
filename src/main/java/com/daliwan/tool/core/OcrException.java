package com.daliwan.tool.core;

/**
 * OCR 识别过程中的可预期错误。
 */
public class OcrException extends Exception {

    public OcrException(String message) {
        super(message);
    }

    public OcrException(String message, Throwable cause) {
        super(message, cause);
    }
}
