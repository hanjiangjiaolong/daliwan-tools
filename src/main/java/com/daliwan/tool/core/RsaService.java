package com.daliwan.tool.core;

import javax.crypto.Cipher;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * RSA 密钥生成、加解密与签名验签。
 *
 * <p>三个必须知道的事实：
 * <ol>
 *   <li><b>公钥加密有长度上限</b>：PKCS#1 v1.5 填充下，单块最大明文为「密钥字节数 − 11」，
 *       2048 位密钥约 245 字节。本实现自动分段，因此不需要调用方关心长度。</li>
 *   <li><b>加密用公钥、解密用私钥</b>；签名方向相反，私钥签、公钥验。</li>
 *   <li><b>RSA 慢且结果随机</b>：同一明文两次加密的密文不同（填充随机），这是正常现象，
 *       实际业务通常用 RSA 加密对称密钥，而不是直接加密正文。</li>
 * </ol>
 */
public final class RsaService {

    /** 密钥对（PEM 文本，已做 64 字符换行）。 */
    public record KeyPairText(String privatePem, String publicPem) {
    }

    private static final String TRANSFORMATION = "RSA/ECB/PKCS1Padding";
    private static final String SIGN_ALGORITHM = "SHA256withRSA";
    private static final int PEM_LINE_LENGTH = 64;
    /** PKCS#1 v1.5 填充占用 11 字节。 */
    private static final int PADDING_OVERHEAD = 11;

    private RsaService() {
    }

    /** 生成密钥对，{@code bits} 常用 2048 / 3072 / 4096。 */
    public static KeyPairText generateKeyPair(int bits) throws CipherException {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(bits);
            KeyPair pair = generator.generateKeyPair();
            return new KeyPairText(
                    toPem("PRIVATE KEY", pair.getPrivate().getEncoded()),
                    toPem("PUBLIC KEY", pair.getPublic().getEncoded()));
        } catch (GeneralSecurityException e) {
            throw new CipherException("生成密钥对失败：" + e.getMessage(), e);
        }
    }

    /**
     * 用公钥加密，返回 Base64 密文（自动分段处理超长明文）。
     */
    public static String encrypt(String plaintext, String publicKeyPem) throws CipherException {
        if (plaintext == null || plaintext.isEmpty()) {
            throw new CipherException("待加密内容为空");
        }
        PublicKey key = parsePublicKey(publicKeyPem);
        byte[] data = plaintext.getBytes(StandardCharsets.UTF_8);
        int maxBlock = keyBytesOf(key) - PADDING_OVERHEAD;
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (int offset = 0; offset < data.length; offset += maxBlock) {
                int length = Math.min(maxBlock, data.length - offset);
                out.writeBytes(cipher.doFinal(data, offset, length));
            }
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (GeneralSecurityException e) {
            throw new CipherException("加密失败：" + e.getMessage(), e);
        }
    }

    /** 用私钥解密 Base64 密文。 */
    public static String decrypt(String cipherBase64, String privateKeyPem) throws CipherException {
        byte[] encrypted = decodeBase64Text(cipherBase64, "密文");
        PrivateKey key = parsePrivateKey(privateKeyPem);
        int blockSize = keyBytesOf(key);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (int offset = 0; offset < encrypted.length; offset += blockSize) {
                int length = Math.min(blockSize, encrypted.length - offset);
                out.writeBytes(cipher.doFinal(encrypted, offset, length));
            }
            return out.toString(StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new CipherException("解密失败：私钥不匹配或密文已损坏", e);
        }
    }

    /** 用私钥签名（SHA256withRSA），返回 Base64 签名值。 */
    public static String sign(String text, String privateKeyPem) throws CipherException {
        if (text == null || text.isEmpty()) {
            throw new CipherException("待签名内容为空");
        }
        try {
            Signature signature = Signature.getInstance(SIGN_ALGORITHM);
            signature.initSign(parsePrivateKey(privateKeyPem));
            signature.update(text.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (GeneralSecurityException e) {
            throw new CipherException("签名失败：" + e.getMessage(), e);
        }
    }

    /** 用公钥验签。 */
    public static boolean verify(String text, String signatureBase64, String publicKeyPem)
            throws CipherException {
        byte[] signatureBytes = decodeBase64Text(signatureBase64, "签名值");
        try {
            Signature signature = Signature.getInstance(SIGN_ALGORITHM);
            signature.initVerify(parsePublicKey(publicKeyPem));
            signature.update((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            return signature.verify(signatureBytes);
        } catch (GeneralSecurityException e) {
            throw new CipherException("验签失败：公钥不匹配或签名格式不正确", e);
        }
    }

    // ---------------------------------------------------------------- 密钥解析

    /** 解析公钥，支持 X.509/SPKI 的 PEM 或纯 Base64。 */
    public static PublicKey parsePublicKey(String pem) throws CipherException {
        byte[] der = decodePem(pem, "公钥");
        try {
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (GeneralSecurityException e) {
            throw new CipherException("公钥格式不正确，应为 X.509/PKCS#8 编码的 RSA 公钥", e);
        }
    }

    /** 解析私钥，同时兼容 PKCS#8 与 OpenSSL 传统的 PKCS#1。 */
    public static PrivateKey parsePrivateKey(String pem) throws CipherException {
        if (pem == null || pem.isBlank()) {
            throw new CipherException("私钥为空");
        }
        boolean pkcs1 = pem.contains("BEGIN RSA PRIVATE KEY");
        byte[] der = decodePem(pem, "私钥");
        if (pkcs1) {
            der = wrapPkcs1AsPkcs8(der);
        }
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException e) {
            throw new CipherException("私钥格式不正确，应为 PKCS#8 或 PKCS#1 编码的 RSA 私钥", e);
        }
    }

    /** 从 PEM 文本中取出 DER 字节，兼容带不带头尾标记两种情况。 */
    private static byte[] decodePem(String pem, String label) throws CipherException {
        if (pem == null || pem.isBlank()) {
            throw new CipherException(label + "为空");
        }
        String content = pem
                .replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s+", "");
        try {
            return Base64.getDecoder().decode(content);
        } catch (IllegalArgumentException e) {
            throw new CipherException(label + "不是合法的 Base64 内容", e);
        }
    }

    private static byte[] decodeBase64Text(String text, String label) throws CipherException {
        if (text == null || text.isBlank()) {
            throw new CipherException(label + "为空");
        }
        try {
            return Base64.getDecoder().decode(text.replaceAll("\\s+", ""));
        } catch (IllegalArgumentException e) {
            throw new CipherException(label + "不是合法的 Base64 内容", e);
        }
    }

    private static int keyBytesOf(PublicKey key) throws CipherException {
        if (key instanceof RSAPublicKey rsa) {
            return (rsa.getModulus().bitLength() + 7) / 8;
        }
        throw new CipherException("不是 RSA 公钥");
    }

    private static int keyBytesOf(PrivateKey key) throws CipherException {
        if (key instanceof java.security.interfaces.RSAPrivateKey rsa) {
            return (rsa.getModulus().bitLength() + 7) / 8;
        }
        throw new CipherException("不是 RSA 私钥");
    }

    // ---------------------------------------------------------------- PEM 输出

    private static String toPem(String type, byte[] der) {
        String base64 = Base64.getEncoder().encodeToString(der);
        StringBuilder out = new StringBuilder();
        out.append("-----BEGIN ").append(type).append("-----\n");
        for (int i = 0; i < base64.length(); i += PEM_LINE_LENGTH) {
            out.append(base64, i, Math.min(i + PEM_LINE_LENGTH, base64.length())).append('\n');
        }
        out.append("-----END ").append(type).append("-----");
        return out.toString();
    }

    // ---------------------------------------------------------------- PKCS#1 包装

    /**
     * 把 PKCS#1 私钥包装成 PKCS#8，供 JDK 的 KeyFactory 解析。
     *
     * <pre>
     * PrivateKeyInfo ::= SEQUENCE {
     *   version         INTEGER (0),
     *   algorithm       SEQUENCE { OID rsaEncryption, NULL },
     *   privateKey      OCTET STRING (PKCS#1)
     * }
     * </pre>
     */
    private static byte[] wrapPkcs1AsPkcs8(byte[] pkcs1) {
        byte[] version = {0x02, 0x01, 0x00};
        byte[] algorithmIdentifier = {
                0x30, 0x0d,
                0x06, 0x09,
                0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01,
                0x05, 0x00
        };
        byte[] octetString = derTlv(0x04, pkcs1);
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        content.writeBytes(version);
        content.writeBytes(algorithmIdentifier);
        content.writeBytes(octetString);
        return derTlv(0x30, content.toByteArray());
    }

    private static byte[] derTlv(int tag, byte[] value) {
        byte[] length = derLength(value.length);
        byte[] out = new byte[1 + length.length + value.length];
        out[0] = (byte) tag;
        System.arraycopy(length, 0, out, 1, length.length);
        System.arraycopy(value, 0, out, 1 + length.length, value.length);
        return out;
    }

    private static byte[] derLength(int length) {
        if (length < 0x80) {
            return new byte[]{(byte) length};
        }
        if (length <= 0xFF) {
            return new byte[]{(byte) 0x81, (byte) length};
        }
        if (length <= 0xFFFF) {
            return new byte[]{(byte) 0x82, (byte) (length >> 8), (byte) length};
        }
        return new byte[]{(byte) 0x83, (byte) (length >> 16), (byte) (length >> 8), (byte) length};
    }
}
