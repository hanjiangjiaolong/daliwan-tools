package com.daliwan.tool.core;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 对称加解密（AES / DES / 3DES），基于 JDK 内置的 {@code javax.crypto}。
 *
 * <p>填充统一使用 PKCS5Padding（对 AES 而言等价于 PKCS7），因此加解密长度不必对齐块大小。
 * 模式支持 ECB 与 CBC；CBC 需要与块等长的初始向量。
 *
 * <p>安全提示：DES 密钥空间仅 56 位，现代硬件可在数天内暴力穷举；ECB 模式对相同明文块
 * 产生相同密文块，会泄露数据结构。两者仅适合教学、兼容旧系统与内部测试。
 */
public final class SymmetricCipher {

    /** 支持的算法与密钥长度。 */
    public enum Algorithm {
        AES_128("AES", 16, 16, "AES-128"),
        AES_192("AES", 24, 16, "AES-192"),
        AES_256("AES", 32, 16, "AES-256"),
        DES("DES", 8, 8, "DES"),
        TRIPLE_DES("DESede", 24, 8, "3DES");

        private final String jdkName;
        private final int keyBytes;
        private final int blockBytes;
        private final String label;

        Algorithm(String jdkName, int keyBytes, int blockBytes, String label) {
            this.jdkName = jdkName;
            this.keyBytes = keyBytes;
            this.blockBytes = blockBytes;
            this.label = label;
        }

        public String jdkName() {
            return jdkName;
        }

        /** 密钥所需的字节数。 */
        public int keyBytes() {
            return keyBytes;
        }

        /** 分组长度，也是 CBC 模式初始向量的长度。 */
        public int blockBytes() {
            return blockBytes;
        }

        @Override
        public String toString() {
            return label + "（" + keyBytes + " 字节密钥）";
        }
    }

    /** 分组模式。 */
    public enum Mode {
        ECB("ECB", false),
        CBC("CBC", true);

        private final String label;
        private final boolean needsIv;

        Mode(String label, boolean needsIv) {
            this.label = label;
            this.needsIv = needsIv;
        }

        public boolean needsIv() {
            return needsIv;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** 密钥与初始向量的书写形式。 */
    public enum KeyFormat {
        TEXT("文本"),
        HEX("十六进制");

        private final String label;

        KeyFormat(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** 密文的书写形式。 */
    public enum Encoding {
        BASE64("Base64"),
        HEX("十六进制");

        private final String label;

        Encoding(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final String PADDING = "PKCS5Padding";

    private SymmetricCipher() {
    }

    /**
     * 加密。
     *
     * @param plaintext 明文，按 UTF-8 转字节
     * @param algorithm 算法
     * @param mode      模式
     * @param key       密钥字节，长度须与算法匹配
     * @param iv        CBC 模式的初始向量；ECB 模式传 null
     * @param encoding  输出编码
     * @return 密文
     * @throws CipherException 参数不合法或加密失败
     */
    public static String encrypt(String plaintext, Algorithm algorithm, Mode mode,
                                 byte[] key, byte[] iv, Encoding encoding) throws CipherException {
        byte[] input = (plaintext == null ? "" : plaintext).getBytes(StandardCharsets.UTF_8);
        try {
            Cipher cipher = build(Cipher.ENCRYPT_MODE, algorithm, mode, key, iv);
            return encode(cipher.doFinal(input), encoding);
        } catch (GeneralSecurityException e) {
            throw new CipherException(describe(e), e);
        }
    }

    /**
     * 解密。
     *
     * @param cipherText 密文字符串
     * @param algorithm  算法
     * @param mode       模式
     * @param key        密钥字节
     * @param iv         CBC 模式的初始向量；ECB 模式传 null
     * @param encoding   密文的编码形式
     * @return 明文
     * @throws CipherException 参数不合法、密文格式错误或密钥不匹配
     */
    public static String decrypt(String cipherText, Algorithm algorithm, Mode mode,
                                 byte[] key, byte[] iv, Encoding encoding) throws CipherException {
        byte[] input = decodeInput(cipherText, encoding);
        try {
            Cipher cipher = build(Cipher.DECRYPT_MODE, algorithm, mode, key, iv);
            return new String(cipher.doFinal(input), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new CipherException(describe(e), e);
        }
    }

    /** 把界面上输入的密钥或向量文本转为字节。 */
    public static byte[] parseKeyMaterial(String text, KeyFormat format) throws CipherException {
        if (text == null || text.isEmpty()) {
            throw new CipherException("请填写密钥");
        }
        if (format == KeyFormat.TEXT) {
            return text.getBytes(StandardCharsets.UTF_8);
        }
        String cleaned = text.replaceAll("[\\s:,-]", "");
        if (cleaned.isEmpty()) {
            throw new CipherException("十六进制内容为空");
        }
        if (cleaned.length() % 2 != 0) {
            throw new CipherException("十六进制长度必须为偶数，当前 " + cleaned.length() + " 个字符");
        }
        try {
            return HexFormat.of().parseHex(cleaned);
        } catch (IllegalArgumentException e) {
            throw new CipherException("十六进制格式不正确");
        }
    }

    /** 计算输入文本的字节数，用于界面提示。 */
    public static int byteLength(String text, KeyFormat format) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        if (format == KeyFormat.TEXT) {
            return text.getBytes(StandardCharsets.UTF_8).length;
        }
        String cleaned = text.replaceAll("[\\s:,-]", "");
        return cleaned.length() / 2;
    }

    private static Cipher build(int opmode, Algorithm algorithm, Mode mode,
                                byte[] key, byte[] iv) throws GeneralSecurityException, CipherException {
        if (key == null || key.length != algorithm.keyBytes()) {
            throw new CipherException("密钥长度应为 " + algorithm.keyBytes() + " 字节，当前 "
                    + (key == null ? 0 : key.length) + " 字节");
        }
        if (mode.needsIv()) {
            if (iv == null || iv.length != algorithm.blockBytes()) {
                throw new CipherException("CBC 模式需要 " + algorithm.blockBytes() + " 字节初始向量，当前 "
                        + (iv == null ? 0 : iv.length) + " 字节");
            }
        }
        Cipher cipher = Cipher.getInstance(algorithm.jdkName() + "/" + mode + "/" + PADDING);
        SecretKeySpec keySpec = new SecretKeySpec(key, algorithm.jdkName());
        if (mode.needsIv()) {
            cipher.init(opmode, keySpec, new IvParameterSpec(iv));
        } else {
            cipher.init(opmode, keySpec);
        }
        return cipher;
    }

    private static byte[] decodeInput(String text, Encoding encoding) throws CipherException {
        if (text == null || text.isBlank()) {
            throw new CipherException("请填写待解密的密文");
        }
        String cleaned = text.replaceAll("\\s+", "");
        try {
            return encoding == Encoding.BASE64
                    ? Base64.getDecoder().decode(cleaned)
                    : HexFormat.of().parseHex(cleaned.replaceAll("[:,-]", ""));
        } catch (IllegalArgumentException e) {
            throw new CipherException("密文不是合法的 " + encoding + " 编码");
        }
    }

    private static String encode(byte[] data, Encoding encoding) {
        return encoding == Encoding.BASE64
                ? Base64.getEncoder().encodeToString(data)
                : HexFormat.of().formatHex(data).toUpperCase(Locale.ROOT);
    }

    private static String describe(GeneralSecurityException e) {
        if (e instanceof BadPaddingException || e instanceof IllegalBlockSizeException) {
            return "解密失败：密钥不匹配、初始向量错误，或密文已损坏";
        }
        if (e instanceof InvalidKeyException) {
            return "密钥不被接受：" + e.getMessage();
        }
        return "运算失败：" + e.getMessage();
    }
}
