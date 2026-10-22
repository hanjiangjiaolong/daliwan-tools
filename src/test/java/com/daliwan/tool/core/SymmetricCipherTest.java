package com.daliwan.tool.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 对称加解密测试。固定向量由 OpenSSL 3.5 生成，用于确认与外部实现的互操作性。
 *
 * <pre>
 * printf 'hello world' | openssl enc -aes-128-ecb -K 3132...3436 -base64 -A
 * </pre>
 */
class SymmetricCipherTest {

    private static final String PLAIN = "hello world";
    private static final String KEY16 = "1234567890123456";
    private static final String KEY32 = "12345678901234567890123456789012";
    private static final String KEY24 = "123456789012345678901234";
    private static final String KEY8 = "12345678";
    private static final String IV16 = "abcdefghijklmnop";

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("AES-128/ECB 与 OpenSSL 结果一致")
    void aes128EcbMatchesOpenSsl() throws Exception {
        String cipherText = SymmetricCipher.encrypt(PLAIN, SymmetricCipher.Algorithm.AES_128,
                SymmetricCipher.Mode.ECB, bytes(KEY16), null, SymmetricCipher.Encoding.BASE64);
        assertEquals("roLzT3GBhVQw22WrUPAdsw==", cipherText);
    }

    @Test
    @DisplayName("AES-256/ECB 与 OpenSSL 结果一致")
    void aes256EcbMatchesOpenSsl() throws Exception {
        String cipherText = SymmetricCipher.encrypt(PLAIN, SymmetricCipher.Algorithm.AES_256,
                SymmetricCipher.Mode.ECB, bytes(KEY32), null, SymmetricCipher.Encoding.BASE64);
        assertEquals("0UBET6L5fwkwIhTkzd8nhg==", cipherText);
    }

    @Test
    @DisplayName("AES-128/CBC 与 OpenSSL 结果一致")
    void aes128CbcMatchesOpenSsl() throws Exception {
        String cipherText = SymmetricCipher.encrypt(PLAIN, SymmetricCipher.Algorithm.AES_128,
                SymmetricCipher.Mode.CBC, bytes(KEY16), bytes(IV16), SymmetricCipher.Encoding.BASE64);
        assertEquals("KHJBtEPv43ymPh5yZK/+wg==", cipherText);
    }

    @Test
    @DisplayName("3DES/ECB 与 OpenSSL 结果一致")
    void tripleDesMatchesOpenSsl() throws Exception {
        String cipherText = SymmetricCipher.encrypt(PLAIN, SymmetricCipher.Algorithm.TRIPLE_DES,
                SymmetricCipher.Mode.ECB, bytes(KEY24), null, SymmetricCipher.Encoding.BASE64);
        assertEquals("SdHQCpbVRzk4JSGbnhUMLg==", cipherText);
    }

    @Test
    @DisplayName("各算法与模式均可往返")
    void roundTripsAllCombinations() throws Exception {
        String plain = "中文 mixed 123!@#";
        for (SymmetricCipher.Algorithm algorithm : SymmetricCipher.Algorithm.values()) {
            byte[] key = new byte[algorithm.keyBytes()];
            byte[] iv = new byte[algorithm.blockBytes()];
            for (int i = 0; i < key.length; i++) {
                key[i] = (byte) ('a' + i % 26);
            }
            for (int i = 0; i < iv.length; i++) {
                iv[i] = (byte) ('0' + i % 10);
            }
            for (SymmetricCipher.Mode mode : SymmetricCipher.Mode.values()) {
                for (SymmetricCipher.Encoding encoding : SymmetricCipher.Encoding.values()) {
                    String cipherText = SymmetricCipher.encrypt(plain, algorithm, mode, key,
                            mode.needsIv() ? iv : null, encoding);
                    String decrypted = SymmetricCipher.decrypt(cipherText, algorithm, mode, key,
                            mode.needsIv() ? iv : null, encoding);
                    assertEquals(plain, decrypted,
                            algorithm + "/" + mode + "/" + encoding);
                }
            }
        }
    }

    @Test
    @DisplayName("相同明文在 ECB 下重复加密得到相同密文，CBC 则不同")
    void ecbIsDeterministicWhileCbcIsNot() throws Exception {
        String first = SymmetricCipher.encrypt("block-block-block!", SymmetricCipher.Algorithm.AES_128,
                SymmetricCipher.Mode.ECB, bytes(KEY16), null, SymmetricCipher.Encoding.BASE64);
        String second = SymmetricCipher.encrypt("block-block-block!", SymmetricCipher.Algorithm.AES_128,
                SymmetricCipher.Mode.ECB, bytes(KEY16), null, SymmetricCipher.Encoding.BASE64);
        assertEquals(first, second);

        String cbc1 = SymmetricCipher.encrypt("block-block-block!", SymmetricCipher.Algorithm.AES_128,
                SymmetricCipher.Mode.CBC, bytes(KEY16), bytes(IV16), SymmetricCipher.Encoding.BASE64);
        String cbc2 = SymmetricCipher.encrypt("block-block-block!", SymmetricCipher.Algorithm.AES_128,
                SymmetricCipher.Mode.CBC, bytes(KEY16), bytes("ponmlkjihgfedcba"),
                SymmetricCipher.Encoding.BASE64);
        assertNotEquals(cbc1, cbc2);
    }

    @Test
    @DisplayName("Hex 输出可被解析回来")
    void hexEncodingRoundTrips() throws Exception {
        String cipherHex = SymmetricCipher.encrypt(PLAIN, SymmetricCipher.Algorithm.AES_128,
                SymmetricCipher.Mode.ECB, bytes(KEY16), null, SymmetricCipher.Encoding.HEX);
        System.out.println("AES-128-ECB hex: " + cipherHex);
        assertEquals(32, cipherHex.length());
        assertEquals(PLAIN, SymmetricCipher.decrypt(cipherHex, SymmetricCipher.Algorithm.AES_128,
                SymmetricCipher.Mode.ECB, bytes(KEY16), null, SymmetricCipher.Encoding.HEX));
    }

    @Test
    @DisplayName("密钥长度不符时拒绝")
    void rejectsWrongKeyLength() {
        CipherException error = assertThrows(CipherException.class, () ->
                SymmetricCipher.encrypt(PLAIN, SymmetricCipher.Algorithm.AES_256,
                        SymmetricCipher.Mode.ECB, bytes(KEY16), null, SymmetricCipher.Encoding.BASE64));
        assertTrue(error.getMessage().contains("32"));
    }

    @Test
    @DisplayName("CBC 缺少初始向量时拒绝")
    void rejectsMissingIv() {
        assertThrows(CipherException.class, () ->
                SymmetricCipher.encrypt(PLAIN, SymmetricCipher.Algorithm.AES_128,
                        SymmetricCipher.Mode.CBC, bytes(KEY16), null, SymmetricCipher.Encoding.BASE64));
    }

    @Test
    @DisplayName("错误密钥解密失败并给出可读原因")
    void wrongKeyFailsWithReadableMessage() throws Exception {
        String cipherText = SymmetricCipher.encrypt(PLAIN, SymmetricCipher.Algorithm.AES_128,
                SymmetricCipher.Mode.CBC, bytes(KEY16), bytes(IV16), SymmetricCipher.Encoding.BASE64);
        CipherException error = assertThrows(CipherException.class, () ->
                SymmetricCipher.decrypt(cipherText, SymmetricCipher.Algorithm.AES_128,
                        SymmetricCipher.Mode.CBC, bytes("6543210987654321"), bytes(IV16),
                        SymmetricCipher.Encoding.BASE64));
        assertTrue(error.getMessage().contains("解密失败"));
    }

    @Test
    @DisplayName("非 Base64 密文给出格式提示")
    void rejectsInvalidBase64() {
        CipherException error = assertThrows(CipherException.class, () ->
                SymmetricCipher.decrypt("这不是 base64!!!", SymmetricCipher.Algorithm.AES_128,
                        SymmetricCipher.Mode.ECB, bytes(KEY16), null, SymmetricCipher.Encoding.BASE64));
        assertTrue(error.getMessage().contains("Base64"));
    }

    @Test
    @DisplayName("十六进制密钥支持空格与冒号分隔")
    void parsesHexKeyWithSeparators() throws Exception {
        byte[] key = SymmetricCipher.parseKeyMaterial("31323334353637383930:313233343536",
                SymmetricCipher.KeyFormat.HEX);
        assertEquals(16, key.length);
        assertEquals(KEY16, new String(key, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("奇数长度的十六进制被拒绝")
    void rejectsOddHexLength() {
        assertThrows(CipherException.class, () ->
                SymmetricCipher.parseKeyMaterial("31323", SymmetricCipher.KeyFormat.HEX));
    }

    @Test
    @DisplayName("字节长度统计与编码方式一致")
    void reportsByteLength() {
        assertEquals(16, SymmetricCipher.byteLength(KEY16, SymmetricCipher.KeyFormat.TEXT));
        assertEquals(16, SymmetricCipher.byteLength("31323334353637383930313233343536",
                SymmetricCipher.KeyFormat.HEX));
        assertEquals(6, SymmetricCipher.byteLength("中文", SymmetricCipher.KeyFormat.TEXT));
        assertEquals(0, SymmetricCipher.byteLength("", SymmetricCipher.KeyFormat.TEXT));
    }
}
