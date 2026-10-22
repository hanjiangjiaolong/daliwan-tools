package com.daliwan.tool.core;

/**
 * 对称加解密过程中的可预期错误，消息面向用户。
 */
public class CipherException extends Exception {

    public CipherException(String message) {
        super(message);
    }

    public CipherException(String message, Throwable cause) {
        super(message, cause);
    }
}
