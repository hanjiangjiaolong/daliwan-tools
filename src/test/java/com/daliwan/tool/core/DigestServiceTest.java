package com.daliwan.tool.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 摘要算法测试，向量取自 RFC 1321 与 NIST FIPS 180-4。
 */
class DigestServiceTest {

    @Test
    @DisplayName("MD5 标准向量：abc")
    void md5KnownVector() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72",
                DigestService.hashText("abc", HashAlgorithm.MD5).hexLower());
    }

    @Test
    @DisplayName("MD5 标准向量：空串")
    void md5EmptyString() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e",
                DigestService.hashText("", HashAlgorithm.MD5).hexLower());
    }

    @Test
    @DisplayName("SHA-1 标准向量：abc")
    void sha1KnownVector() {
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d",
                DigestService.hashText("abc", HashAlgorithm.SHA1).hexLower());
    }

    @Test
    @DisplayName("SHA-256 标准向量：abc")
    void sha256KnownVector() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                DigestService.hashText("abc", HashAlgorithm.SHA256).hexLower());
    }

    @Test
    @DisplayName("SHA-512 标准向量：abc")
    void sha512KnownVector() {
        assertEquals("ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a"
                        + "2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f",
                DigestService.hashText("abc", HashAlgorithm.SHA512).hexLower());
    }

    @Test
    @DisplayName("各算法摘要长度与位数一致")
    void digestLengthsMatchAlgorithm() {
        for (HashAlgorithm algorithm : HashAlgorithm.values()) {
            String hex = DigestService.hashText("x", algorithm).hexLower();
            assertEquals(algorithm.hexLength(), hex.length(), algorithm.jdkName());
        }
        assertEquals(128, HashAlgorithm.MD5.bitLength());
        assertEquals(512, HashAlgorithm.SHA512.bitLength());
    }

    @Test
    @DisplayName("16 位截取形式仅 MD5 提供")
    void sixteenDigitFormOnlyForMd5() {
        HashResult md5 = DigestService.hashText("abc", HashAlgorithm.MD5);
        assertEquals("3cd24fb0d6963f7d", md5.hex16Lower());
        assertEquals("3CD24FB0D6963F7D", md5.hex16Upper());
        assertTrue(md5.supportsSixteenDigitForm());

        HashResult sha256 = DigestService.hashText("abc", HashAlgorithm.SHA256);
        assertFalse(sha256.supportsSixteenDigitForm());
        assertEquals("", sha256.hex16Lower());
    }

    @Test
    @DisplayName("Base64 形式可还原为摘要字节")
    void base64FormIsDecodable() {
        HashResult result = DigestService.hashText("abc", HashAlgorithm.MD5);
        byte[] decoded = Base64.getDecoder().decode(result.base64());
        assertEquals(16, decoded.length);
        assertEquals("900150983cd24fb0d6963f7d28e17f72", java.util.HexFormat.of().formatHex(decoded));
    }

    @Test
    @DisplayName("中文按 UTF-8 编码计算")
    void handlesChineseText() {
        String text = "中文测试";
        assertEquals(DigestService.hash(text.getBytes(StandardCharsets.UTF_8), HashAlgorithm.MD5).hexLower(),
                DigestService.hashText(text, HashAlgorithm.MD5).hexLower());
    }

    @Test
    @DisplayName("文件流式计算与进度回调")
    void hashesFileWithProgress(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("abc.txt");
        Files.writeString(file, "abc");

        AtomicLong lastReported = new AtomicLong(-1);
        HashResult result = DigestService.hashFile(file, HashAlgorithm.SHA256, lastReported::set);

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                result.hexLower());
        assertEquals(3L, lastReported.get());
    }

    @Test
    @DisplayName("空文件摘要与空串一致")
    void hashesEmptyFile(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("empty.bin");
        Files.createFile(file);
        assertEquals("d41d8cd98f00b204e9800998ecf8427e",
                DigestService.hashFile(file, HashAlgorithm.MD5).hexLower());
    }

    @Test
    @DisplayName("摘要长度不符时拒绝构造")
    void rejectsInvalidDigestLength() {
        assertThrows(IllegalArgumentException.class,
                () -> new HashResult(HashAlgorithm.MD5, "abc"));
    }
}
