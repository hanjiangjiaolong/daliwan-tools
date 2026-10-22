package com.daliwan.tool.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RsaServiceTest {

    /** 与 OpenSSL 交叉验证用的固定密钥对（2048 位，PKCS#1 私钥）。 */
    private static final String OPENSSL_PRIVATE = resource("/pkcs1.pem");
    private static final String OPENSSL_PUBLIC = resource("/pub.pem");

    private static String resource(String path) {
        try (InputStream in = RsaServiceTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("测试资源缺失：" + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.US_ASCII);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("生成的密钥对带正确的 PEM 头尾")
    void generatesPemKeyPair() throws Exception {
        RsaService.KeyPairText keys = RsaService.generateKeyPair(2048);
        assertTrue(keys.privatePem().startsWith("-----BEGIN PRIVATE KEY-----"));
        assertTrue(keys.privatePem().endsWith("-----END PRIVATE KEY-----"));
        assertTrue(keys.publicPem().startsWith("-----BEGIN PUBLIC KEY-----"));
        assertTrue(keys.publicPem().endsWith("-----END PUBLIC KEY-----"));
    }

    @Test
    @DisplayName("短文本加密后可原样解密")
    void encryptDecryptRoundTrip() throws Exception {
        RsaService.KeyPairText keys = RsaService.generateKeyPair(2048);
        String plain = "你好，RSA";
        String cipher = RsaService.encrypt(plain, keys.publicPem());
        assertEquals(plain, RsaService.decrypt(cipher, keys.privatePem()));
    }

    @Test
    @DisplayName("超过单块上限的明文自动分段")
    void longPlaintextIsChunked() throws Exception {
        RsaService.KeyPairText keys = RsaService.generateKeyPair(2048);
        // 2048 位密钥单块上限 245 字节，这里用 1000 个汉字（约 3000 字节）验证分段
        String plain = "数据".repeat(500);
        String cipher = RsaService.encrypt(plain, keys.publicPem());
        assertEquals(plain, RsaService.decrypt(cipher, keys.privatePem()));
    }

    @Test
    @DisplayName("同一明文两次加密的密文不同（填充随机）")
    void ciphertextIsRandomized() throws Exception {
        RsaService.KeyPairText keys = RsaService.generateKeyPair(2048);
        String first = RsaService.encrypt("same", keys.publicPem());
        String second = RsaService.encrypt("same", keys.publicPem());
        assertNotEquals(first, second);
    }

    @Test
    @DisplayName("私钥签名、公钥验签通过")
    void signAndVerify() throws Exception {
        RsaService.KeyPairText keys = RsaService.generateKeyPair(2048);
        String text = "待签名的内容";
        String signature = RsaService.sign(text, keys.privatePem());
        assertTrue(RsaService.verify(text, signature, keys.publicPem()));
    }

    @Test
    @DisplayName("内容被篡改后验签失败")
    void verifyFailsAfterTamper() throws Exception {
        RsaService.KeyPairText keys = RsaService.generateKeyPair(2048);
        String signature = RsaService.sign("原始内容", keys.privatePem());
        assertFalse(RsaService.verify("被改过的内容", signature, keys.publicPem()));
    }

    @Test
    @DisplayName("其他密钥对的签名验不过")
    void verifyFailsWithWrongKey() throws Exception {
        RsaService.KeyPairText signer = RsaService.generateKeyPair(2048);
        RsaService.KeyPairText other = RsaService.generateKeyPair(2048);
        String signature = RsaService.sign("内容", signer.privatePem());
        assertFalse(RsaService.verify("内容", signature, other.publicPem()));
    }

    @Test
    @DisplayName("兼容 OpenSSL 生成的 PKCS#1 私钥")
    void parsesOpenSslPkcs1PrivateKey() throws Exception {
        String cipher = RsaService.encrypt("跨工具互通", OPENSSL_PUBLIC);
        assertEquals("跨工具互通", RsaService.decrypt(cipher, OPENSSL_PRIVATE));
    }

    @Test
    @DisplayName("OpenSSL 私钥签名后可用其公钥验签")
    void signsWithOpenSslKey() throws Exception {
        String signature = RsaService.sign("互通性验证", OPENSSL_PRIVATE);
        assertTrue(RsaService.verify("互通性验证", signature, OPENSSL_PUBLIC));
    }

    @Test
    @DisplayName("公钥格式错误时给出可读提示")
    void rejectsInvalidPublicKey() {
        CipherException error = assertThrows(CipherException.class,
                () -> RsaService.encrypt("x", "-----BEGIN PUBLIC KEY-----\nnope\n-----END PUBLIC KEY-----"));
        assertTrue(error.getMessage().contains("公钥"));
    }

    @Test
    @DisplayName("空明文被拒绝")
    void rejectsEmptyPlaintext() {
        assertThrows(CipherException.class, () -> RsaService.encrypt("", OPENSSL_PUBLIC));
    }

    @Test
    @DisplayName("私钥为空时提示")
    void rejectsEmptyPrivateKey() {
        CipherException error = assertThrows(CipherException.class,
                () -> RsaService.decrypt("AAAA", ""));
        assertTrue(error.getMessage().contains("私钥"));
    }
}
