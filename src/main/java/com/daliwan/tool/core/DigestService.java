package com.daliwan.tool.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.LongConsumer;

/**
 * 消息摘要服务，基于 JDK 标准库 {@link MessageDigest}，无第三方依赖。
 *
 * <p>本类不依赖 JavaFX，可独立测试与复用。
 */
public final class DigestService {

    private static final HexFormat HEX = HexFormat.of();
    private static final int BUFFER_SIZE = 64 * 1024;

    private DigestService() {
    }

    /**
     * 计算文本摘要，字符按 UTF-8 编码为字节。
     *
     * <p>编码必须固定，否则同一段中文在不同平台会得到不同结果。
     */
    public static HashResult hashText(String text, HashAlgorithm algorithm) {
        String safe = text == null ? "" : text;
        return hash(safe.getBytes(StandardCharsets.UTF_8), algorithm);
    }

    /** 计算字节数组摘要。 */
    public static HashResult hash(byte[] data, HashAlgorithm algorithm) {
        return new HashResult(algorithm, HEX.formatHex(newDigest(algorithm).digest(data)));
    }

    /**
     * 流式计算文件摘要，内存占用恒定，与文件大小无关。
     *
     * @throws IOException 文件不存在或读取失败
     */
    public static HashResult hashFile(Path file, HashAlgorithm algorithm) throws IOException {
        return hashFile(file, algorithm, bytesRead -> {
        });
    }

    /**
     * 流式计算文件摘要，并回报已读取字节数。
     *
     * @param progress 已读取字节数回调；在计算线程中被调用
     * @throws IOException 文件不存在或读取失败
     */
    public static HashResult hashFile(Path file, HashAlgorithm algorithm, LongConsumer progress)
            throws IOException {
        MessageDigest digest = newDigest(algorithm);
        byte[] buffer = new byte[BUFFER_SIZE];
        long total = 0L;
        try (InputStream in = Files.newInputStream(file)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
                total += read;
                progress.accept(total);
            }
        }
        return new HashResult(algorithm, HEX.formatHex(digest.digest()));
    }

    private static MessageDigest newDigest(HashAlgorithm algorithm) {
        try {
            return MessageDigest.getInstance(algorithm.jdkName());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JRE 未提供 " + algorithm.jdkName() + " 算法", e);
        }
    }
}
