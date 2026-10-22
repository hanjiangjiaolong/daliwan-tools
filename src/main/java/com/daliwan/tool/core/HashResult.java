package com.daliwan.tool.core;

import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 一次摘要计算的结果。
 *
 * <p>仅保存算法与十六进制摘要，其余形式按需派生。
 *
 * @param algorithm 使用的算法
 * @param hex       十六进制摘要（统一存小写）
 */
public record HashResult(HashAlgorithm algorithm, String hex) {

    private static final HexFormat HEX = HexFormat.of();
    private static final int SIXTEEN_START = 8;
    private static final int SIXTEEN_END = 24;

    public HashResult {
        if (algorithm == null) {
            throw new IllegalArgumentException("算法不能为空");
        }
        if (hex == null || hex.length() != algorithm.hexLength()) {
            throw new IllegalArgumentException(
                    algorithm.jdkName() + " 的摘要长度应为 " + algorithm.hexLength() + " 个十六进制字符");
        }
        hex = hex.toLowerCase(Locale.ROOT);
    }

    /** 32 位小写（对 MD5 而言）。 */
    public String hexLower() {
        return hex;
    }

    /** 全大写形式。 */
    public String hexUpper() {
        return hex.toUpperCase(Locale.ROOT);
    }

    /** 摘要字节的 Base64 表示，常见于 HTTP 头与部分接口签名。 */
    public String base64() {
        return Base64.getEncoder().encodeToString(bytes());
    }

    /** 十六进制还原为字节数组。 */
    public byte[] bytes() {
        return HEX.parseHex(hex);
    }

    /** 是否具备 16 位截取形式（仅 MD5）。 */
    public boolean supportsSixteenDigitForm() {
        return algorithm.supportsSixteenDigitForm();
    }

    /** 32 位结果的第 9 至 24 个字符，非 MD5 时返回空串。 */
    public String hex16Lower() {
        return supportsSixteenDigitForm() ? hex.substring(SIXTEEN_START, SIXTEEN_END) : "";
    }

    /** 16 位形式的大写。 */
    public String hex16Upper() {
        return supportsSixteenDigitForm() ? hex16Lower().toUpperCase(Locale.ROOT) : "";
    }
}
