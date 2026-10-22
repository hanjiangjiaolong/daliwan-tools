package com.daliwan.tool.core;

/**
 * 支持的消息摘要算法。
 *
 * <p>全部由 JDK 的 {@code MessageDigest} 提供，无需第三方库。
 */
public enum HashAlgorithm {

    MD5("MD5", 32),
    SHA1("SHA-1", 40),
    SHA256("SHA-256", 64),
    SHA384("SHA-384", 96),
    SHA512("SHA-512", 128);

    private final String jdkName;
    private final int hexLength;

    HashAlgorithm(String jdkName, int hexLength) {
        this.jdkName = jdkName;
        this.hexLength = hexLength;
    }

    /** 传给 {@code MessageDigest.getInstance} 的标准名称。 */
    public String jdkName() {
        return jdkName;
    }

    /** 十六进制摘要的字符数。 */
    public int hexLength() {
        return hexLength;
    }

    /** 输出位数，用于界面展示。 */
    public int bitLength() {
        return hexLength * 4;
    }

    /** 是否提供 16 位截取形式（仅 MD5 有此惯例）。 */
    public boolean supportsSixteenDigitForm() {
        return hexLength == 32;
    }

    /** 界面展示名称。 */
    public String label() {
        return jdkName + " · " + bitLength() + " 位";
    }

    /**
     * 下拉框展示名称。
     *
     * <p>日常语境里的「MD5 32 位」指 32 个十六进制字符，与摘要位宽（128 位）不是同一概念，
     * 因此 MD5 额外标注字符数，其余算法直接用标准名，避免「256 位」与「64 个字符」混淆。
     */
    @Override
    public String toString() {
        return supportsSixteenDigitForm() ? jdkName + "（" + hexLength + " 位）" : jdkName;
    }
}
