package com.daliwan.tool.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * 极简 PNG 编码器：把 ARGB 像素数组写成 PNG。
 *
 * <p>为什么不用 {@code javax.imageio.ImageIO}：ImageIO 的编解码器是靠 SPI 服务发现注册的，
 * 打 fat jar 时少合并一个 {@code META-INF/services} 就编不出图，而且 {@code ImageIO.write}
 * 找不到编码器时只是**静默返回 false**，界面上看到的就是「点了保存却没反应」。
 * 二维码只需要「8 位 RGBA、非隔行」这一种 PNG 形态，自己实现不到一百行，
 * 只依赖 {@code java.util.zip}，行为稳定可控。
 *
 * <p>只支持这一种格式，也就不做参数化：宽高取自调用方，像素必须是**非预乘**的
 * {@code 0xAARRGGBB}（JavaFX 的 {@code PixelFormat.getIntArgbInstance()} 正是这个约定）。
 */
public final class PngEncoder {

    private static final byte[] SIGNATURE = {
            (byte) 0x89, 'P', 'N', 'G', '\r', '\n', (byte) 0x1A, '\n'
    };

    /** 颜色类型 6 = 真彩色 + alpha；位深固定 8；压缩方式与隔行方式固定为 0。 */
    private static final byte BIT_DEPTH = 8;
    private static final byte COLOR_TYPE_RGBA = 6;

    private PngEncoder() {
    }

    /**
     * 编码成 PNG 字节。
     *
     * @param argb   长度至少为 {@code width * height} 的像素数组，{@code 0xAARRGGBB}
     * @param width  图片宽度
     * @param height 图片高度
     */
    public static byte[] encode(int[] argb, int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("尺寸非法：" + width + "×" + height);
        }
        if (argb == null || argb.length < width * height) {
            throw new IllegalArgumentException("像素数据不足：需要 " + (width * height) + " 个");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(1024, width * height * 2));
        out.writeBytes(SIGNATURE);

        byte[] ihdr = new byte[13];
        putInt(ihdr, 0, width);
        putInt(ihdr, 4, height);
        ihdr[8] = BIT_DEPTH;
        ihdr[9] = COLOR_TYPE_RGBA;
        writeChunk(out, "IHDR", ihdr);

        writeChunk(out, "IDAT", deflate(rawScanlines(argb, width, height)));
        writeChunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    /** 编码并写入文件。 */
    public static void write(Path file, int[] argb, int width, int height) throws IOException {
        Files.write(file, encode(argb, width, height));
    }

    /**
     * 拼出「原始扫描行 + 过滤类型」的数据块：每行开头一个过滤方式字节（这里一律用
     * 「不过滤」），后面跟着该行每个像素的 R、G、B、A。
     */
    private static byte[] rawScanlines(int[] argb, int width, int height) {
        int stride = width * 4;
        byte[] raw = new byte[height * (stride + 1)];
        int p = 0;
        for (int y = 0; y < height; y++) {
            raw[p++] = 0;
            int row = y * width;
            for (int x = 0; x < width; x++) {
                int pixel = argb[row + x];
                raw[p++] = (byte) (pixel >>> 16);
                raw[p++] = (byte) (pixel >>> 8);
                raw[p++] = (byte) pixel;
                raw[p++] = (byte) (pixel >>> 24);
            }
        }
        return raw;
    }

    /** zlib 压缩（PNG 的 IDAT 要求 zlib 封装，Deflater 默认就是）。 */
    private static byte[] deflate(byte[] raw) {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
        try {
            deflater.setInput(raw);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, raw.length / 8));
            byte[] buffer = new byte[8192];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    /** 块结构：长度(4) + 类型(4) + 数据 + CRC32(4)，CRC 覆盖类型与数据。 */
    private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data) {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);

        out.write(data.length >>> 24);
        out.write(data.length >>> 16);
        out.write(data.length >>> 8);
        out.write(data.length);
        out.writeBytes(typeBytes);
        out.writeBytes(data);
        int value = (int) crc.getValue();
        out.write(value >>> 24);
        out.write(value >>> 16);
        out.write(value >>> 8);
        out.write(value);
    }

    private static void putInt(byte[] target, int offset, int value) {
        target[offset] = (byte) (value >>> 24);
        target[offset + 1] = (byte) (value >>> 16);
        target[offset + 2] = (byte) (value >>> 8);
        target[offset + 3] = (byte) value;
    }
}
