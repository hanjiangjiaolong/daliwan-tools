package com.daliwan.tool.core;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.CRC32;

/**
 * 去除图片元信息：把 EXIF / XMP / IPTC / GPS / 注释等隐私相关内容从文件里抹掉。
 *
 * <p>做法按容器结构「就地剪裁」而不是解码再编码——JPEG / PNG / WebP / GIF 都是
 * 分块结构，把带元数据的那几块摘掉、其余字节原样搬运即可，像素数据一个字节都不动，
 * 因此<b>没有画质损失、体积只减不增</b>。只有这几个格式之外的（TIFF / BMP 等）
 * 才退回「解码 → 重写」，并明确告知用户属于重新编码。
 *
 * <p>唯一需要特殊对待的是 EXIF 里的方向标记（Orientation）：手机拍的照片像素往往
 * 是横着存的，靠这一个标签让看图软件转正。整段 EXIF 删掉后照片会横躺，
 * 所以默认只保留一个「最小 EXIF 段」，里面仅含方向这一条记录（26 字节），
 * 相机型号、拍摄时间、GPS 坐标等全部不复存在。不需要这个标记的场景可以直接丢弃，
 * 或者选择把方向烘进像素（这一步必然要重新编码）。
 */
public final class ImageMetaStripper {

    /** 方向标记的三种处理方式。 */
    public enum OrientationMode {

        /** 连方向标记一起删掉；带旋转信息的照片可能显示成横躺。 */
        DROP("直接丢弃"),

        /** 只保留方向这一条记录，其余 EXIF 全删；无损，且照片显示方向正确。 */
        KEEP("保留方向标记"),

        /** 按方向旋转／翻转像素后再删掉标记；方向彻底正确，但需要重新编码。 */
        BAKE("摆正像素");

        private final String label;

        OrientationMode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /**
     * 去除时的选项。
     *
     * @param keepIcc       是否保留 ICC 色彩配置。ICC 只描述颜色如何解释，不含任何身份信息；
     *                      删掉它会让 Adobe RGB / Display P3 等广色域图片颜色发灰发淡。
     * @param orientation   方向标记的处理方式
     */
    public record Options(boolean keepIcc, OrientationMode orientation) {

        public static final Options DEFAULT = new Options(true, OrientationMode.KEEP);

        public Options {
            if (orientation == null) {
                orientation = OrientationMode.KEEP;
            }
        }
    }

    /**
     * 处理结果。
     *
     * @param source       源文件
     * @param target       实际写出的文件
     * @param format       格式名，如 JPEG / PNG
     * @param originalBytes 原始字节数
     * @param targetBytes  处理后的字节数
     * @param removed      被移除的内容说明（同类会合并计数）
     * @param warnings     需要提醒用户的事项
     * @param reencoded    是否走了「解码后重写」的路径（有画质损失）
     */
    public record Report(Path source, Path target, String format, long originalBytes, long targetBytes,
                         List<String> removed, List<String> warnings, boolean reencoded) {

        /** 省下的字节数；新文件更大时为负。 */
        public long savedBytes() {
            return originalBytes - targetBytes;
        }

        /** 「共移除 N 类元数据」的短句，状态栏用。 */
        public String headline() {
            if (removed.isEmpty()) {
                return "未发现可移除的元数据";
            }
            return "已移除 " + removed.size() + " 类元数据";
        }
    }

    /** 识别出的容器类型。 */
    private enum Kind {
        JPEG("JPEG"), PNG("PNG"), GIF("GIF"), WEBP("WebP"),
        BMP("BMP"), TIFF("TIFF"), HEIC("HEIC"), UNKNOWN("未知");

        private final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    /** 剥离中间结果。 */
    private record Strip(byte[] bytes, Map<String, Integer> removed, List<String> warnings) {
    }

    private static final byte[] PNG_SIGNATURE =
            {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private ImageMetaStripper() {
    }

    /**
     * 去除一张图片的元信息。
     *
     * <p>先写同目录临时文件再整体替换，中途失败不会留下半截文件；
     * 目标与源相同时（覆盖模式）也是安全的。
     *
     * @param source  源图片
     * @param target  输出路径，可与源相同
     * @param options 选项，{@code null} 取 {@link Options#DEFAULT}
     * @throws IOException 文件不可读、格式不支持或写入失败
     */
    public static Report strip(Path source, Path target, Options options) throws IOException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Options option = options == null ? Options.DEFAULT : options;

        Path in = source.toAbsolutePath().normalize();
        Path out = target.toAbsolutePath().normalize();
        if (!Files.isRegularFile(in)) {
            throw new IOException("文件不存在：" + in);
        }
        Path directory = out.getParent();
        if (directory == null) {
            throw new IOException("目标路径无效：" + out);
        }
        Files.createDirectories(directory);

        long originalBytes = Files.size(in);
        byte[] data = Files.readAllBytes(in);
        Kind kind = kindOf(data);

        Strip strip = switch (kind) {
            case JPEG -> stripJpeg(data, option);
            case PNG -> stripPng(data, option);
            case WEBP -> stripWebp(data, option);
            case GIF -> stripGif(data, option);
            default -> null;
        };

        Path temp = Files.createTempFile(directory, ".daliwan-strip-", ".tmp");
        try {
            Report report;
            if (strip == null) {
                reencode(in, temp, kind, option);
                List<String> warnings = new ArrayList<>();
                warnings.add("该格式无法无损剪裁，已重新编码保存" + ("JPEG".equals(kind.label)
                        ? "（画质会有轻微损失）" : ""));
                report = new Report(in, out, kind.label, originalBytes, Files.size(temp),
                        List.of(), List.copyOf(warnings), true);
            } else {
                Files.write(temp, strip.bytes());
                report = new Report(in, out, kind.label, originalBytes, Files.size(temp),
                        describe(strip.removed()), List.copyOf(strip.warnings()), false);
            }
            Files.move(temp, out, StandardCopyOption.REPLACE_EXISTING);
            return report;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** 给界面用的格式判断：这个后缀能不能处理。 */
    public static boolean isSupported(Path file) {
        String name = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".jpe")
                || name.endsWith(".png") || name.endsWith(".gif") || name.endsWith(".webp")
                || name.endsWith(".bmp") || name.endsWith(".tif") || name.endsWith(".tiff");
    }

    // ---------------------------------------------------------------- JPEG

    /**
     * JPEG 分段剪裁。
     *
     * <p>结构是 SOI + 若干「FFxx + 长度 + 数据」段 + SOS + 熵编码数据 + EOI。
     * 只在段头处动手，SOS 之后原样拷贝。
     */
    private static Strip stripJpeg(byte[] data, Options options) {
        List<int[]> segments = new ArrayList<>();
        int position = 2;
        int sos = -1;
        while (position + 4 <= data.length) {
            if ((data[position] & 0xFF) != 0xFF) {
                return null;   // 结构异常，交给重编码兜底
            }
            int marker = data[position + 1] & 0xFF;
            if (marker == 0xFF) {
                position++;                       // 段间填充字节
                continue;
            }
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD8)) {
                position += 2;
                continue;
            }
            if (marker == 0xD9) {
                break;
            }
            if (marker == 0xDA) {
                sos = position;
                break;
            }
            int length = ((data[position + 2] & 0xFF) << 8) | (data[position + 3] & 0xFF);
            if (length < 2 || position + 2 + length > data.length) {
                return null;
            }
            segments.add(new int[]{marker, position, position + 2 + length});
            position += 2 + length;
        }
        if (sos < 0) {
            return null;
        }

        int orientation = 1;
        boolean hasExif = false;
        for (int[] segment : segments) {
            if (segment[0] == 0xE1 && startsWith(data, segment[1] + 4, "Exif\0\0")) {
                hasExif = true;
                orientation = tiffOrientation(data, segment[1] + 10, segment[2] - segment[1] - 10);
                break;
            }
        }

        if (options.orientation() == OrientationMode.BAKE && orientation > 1) {
            return null;                          // 要动像素，走重编码
        }
        boolean keepOrientation = options.orientation() == OrientationMode.KEEP
                && hasExif && orientation > 1;

        Map<String, Integer> removed = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
        out.write(data, 0, 2);

        boolean exifWritten = false;
        boolean iccRemoved = false;
        for (int[] segment : segments) {
            int marker = segment[0];
            int start = segment[1];
            int end = segment[2];
            int payload = start + 4;

            boolean keep;
            String removedName = null;
            if (marker == 0xE0) {
                keep = startsWith(data, payload, "JFIF\0");
                if (!keep) {
                    removedName = "APP0 缩略图段（JFXX）";
                }
            } else if (marker == 0xE1) {
                if (startsWith(data, payload, "Exif\0\0")) {
                    removedName = "EXIF 段（相机 / 镜头 / 拍摄时间 / GPS）";
                } else if (startsWith(data, payload, "http://ns.adobe.com/xap/1.0/\0")) {
                    removedName = "XMP 段（编辑软件 / 版权信息）";
                } else {
                    removedName = "APP1 扩展段";
                }
                keep = false;
            } else if (marker == 0xE2) {
                if (startsWith(data, payload, "ICC_PROFILE\0")) {
                    keep = options.keepIcc();
                    if (!keep) {
                        removedName = "ICC 色彩配置";
                        iccRemoved = true;
                    }
                } else {
                    keep = false;
                    removedName = "APP2 扩展段（多帧 / 深度图）";
                }
            } else if (marker == 0xEE) {
                // Adobe APP14 标记色彩变换方式，缺失会让部分 CMYK 图片解码出错，且不含个人信息
                keep = true;
            } else if (marker >= 0xE3 && marker <= 0xEF) {
                keep = false;
                removedName = marker == 0xED
                        ? "IPTC / Photoshop 段（作者 / 版权 / 说明）"
                        : "APP" + (marker - 0xE0) + " 扩展段";
            } else if (marker == 0xFE) {
                keep = false;
                removedName = "注释段（COM）";
            } else {
                keep = true;
            }

            if (keep) {
                if (keepOrientation && !exifWritten && marker != 0xE0) {
                    writeExifSegment(out, orientation);
                    exifWritten = true;
                }
                out.write(data, start, end - start);
            } else {
                removed.merge(removedName, 1, Integer::sum);
            }
        }
        if (keepOrientation && !exifWritten) {
            writeExifSegment(out, orientation);
        }
        out.write(data, sos, data.length - sos);

        if (keepOrientation) {
            warnings.add("保留 1 项方向标记（Orientation=" + orientation + "），避免照片横躺；"
                    + "如需彻底清除可选择「直接丢弃」或「摆正像素」");
        }
        if (iccRemoved) {
            warnings.add("已移除 ICC 色彩配置，广色域图片（Adobe RGB / Display P3）可能出现色偏");
        }
        return new Strip(out.toByteArray(), removed, warnings);
    }

    /** 拼一个只含 Orientation 一条记录的 APP1 段（34 字节）。 */
    private static void writeExifSegment(ByteArrayOutputStream out, int orientation) {
        byte[] tiff = minimalTiff(orientation);
        int length = 2 + 6 + tiff.length;
        out.write(0xFF);
        out.write(0xE1);
        out.write((length >> 8) & 0xFF);
        out.write(length & 0xFF);
        out.write('E');
        out.write('x');
        out.write('i');
        out.write('f');
        out.write(0);
        out.write(0);
        out.write(tiff, 0, tiff.length);
    }

    /** 最小 TIFF 结构：IFD0 里只有一个 Orientation 标签。 */
    private static byte[] minimalTiff(int orientation) {
        byte[] tiff = new byte[26];
        tiff[0] = 'M';
        tiff[1] = 'M';
        tiff[2] = 0;
        tiff[3] = 42;
        tiff[4] = 0;
        tiff[5] = 0;
        tiff[6] = 0;
        tiff[7] = 8;                        // IFD0 偏移
        tiff[8] = 0;
        tiff[9] = 1;                        // 1 个条目
        tiff[10] = 0x01;
        tiff[11] = 0x12;                    // 0x0112 Orientation
        tiff[12] = 0;
        tiff[13] = 3;                       // SHORT
        tiff[14] = 0;
        tiff[15] = 0;
        tiff[16] = 0;
        tiff[17] = 1;                       // count = 1
        tiff[18] = (byte) ((orientation >> 8) & 0xFF);
        tiff[19] = (byte) (orientation & 0xFF);
        // 22..25 为下一个 IFD 偏移 = 0，默认已是 0
        return tiff;
    }

    // ---------------------------------------------------------------- PNG

    /**
     * PNG 块剪裁：删掉文本块、eXIf、tIME，其余块（含 IDAT 与 CRC）原样搬运。
     *
     * <p>CRC 是每个块各自校验自己的「类型 + 数据」，整块删除不影响其他块的校验，
     * 因此无需重算，只有新插入的 eXIf 块要现算一个。
     */
    private static Strip stripPng(byte[] data, Options options) {
        int position = 8;
        int orientation = 1;
        while (position + 12 <= data.length) {
            int length = (int) readUInt32(data, position, false);
            String type = new String(data, position + 4, 4, StandardCharsets.ISO_8859_1);
            if (length < 0 || position + 12 + length > data.length) {
                return null;
            }
            if ("eXIf".equals(type)) {
                orientation = tiffOrientation(data, position + 8, length);
            }
            if ("IEND".equals(type)) {
                break;
            }
            position += 12 + length;
        }

        if (options.orientation() == OrientationMode.BAKE && orientation > 1) {
            return null;
        }
        boolean keepOrientation = options.orientation() == OrientationMode.KEEP && orientation > 1;

        Map<String, Integer> removed = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
        out.write(data, 0, 8);

        boolean exifWritten = false;
        boolean iccRemoved = false;
        position = 8;
        while (position + 12 <= data.length) {
            int length = (int) readUInt32(data, position, false);
            if (length < 0 || position + 12 + length > data.length) {
                return null;
            }
            String type = new String(data, position + 4, 4, StandardCharsets.ISO_8859_1);

            boolean keep = true;
            String removedName = null;
            switch (type) {
                case "eXIf" -> {
                    keep = false;
                    removedName = "PNG eXIf 块（EXIF 元数据）";
                }
                case "tEXt" -> {
                    keep = false;
                    removedName = "PNG tEXt 文本块（软件 / 作者 / 说明）";
                }
                case "zTXt" -> {
                    keep = false;
                    removedName = "PNG zTXt 压缩文本块";
                }
                case "iTXt" -> {
                    keep = false;
                    removedName = "PNG iTXt 国际化文本块";
                }
                case "tIME" -> {
                    keep = false;
                    removedName = "PNG tIME 块（修改时间）";
                }
                case "iCCP" -> {
                    keep = options.keepIcc();
                    if (!keep) {
                        removedName = "ICC 色彩配置";
                        iccRemoved = true;
                    }
                }
                default -> {
                }
            }

            if (keep) {
                if (keepOrientation && !exifWritten && !"IHDR".equals(type)) {
                    writePngChunk(out, "eXIf", minimalTiff(orientation));
                    exifWritten = true;
                }
                out.write(data, position, 12 + length);
            } else {
                removed.merge(removedName, 1, Integer::sum);
            }
            if ("IEND".equals(type)) {
                break;
            }
            position += 12 + length;
        }

        if (keepOrientation) {
            warnings.add("保留 1 项方向标记（Orientation=" + orientation + "）");
        }
        if (iccRemoved) {
            warnings.add("已移除 ICC 色彩配置，可能出现色偏");
        }
        return new Strip(out.toByteArray(), removed, warnings);
    }

    private static void writePngChunk(ByteArrayOutputStream out, String type, byte[] payload) {
        byte[] name = type.getBytes(StandardCharsets.ISO_8859_1);
        writeUInt32(out, payload.length);
        out.write(name, 0, name.length);
        out.write(payload, 0, payload.length);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(payload);
        writeUInt32(out, (int) crc.getValue());
    }

    // ---------------------------------------------------------------- WebP

    /**
     * WebP 块剪裁：RIFF 容器，删掉 EXIF / XMP 两个块并重算 RIFF 长度；
     * 同时把 VP8X 头里「存在 EXIF / XMP」的两个标志位清掉，
     * 否则解码器会按标志位去后面找不存在的块。
     */
    private static Strip stripWebp(byte[] data, Options options) {
        int position = 12;
        int orientation = 1;
        while (position + 8 <= data.length) {
            String fourcc = new String(data, position, 4, StandardCharsets.ISO_8859_1);
            int size = (int) readUInt32(data, position + 4, true);
            if (size < 0 || position + 8 + size > data.length) {
                return null;
            }
            if ("EXIF".equals(fourcc)) {
                int payload = position + 8;
                int payloadSize = size;
                if (startsWith(data, payload, "Exif\0\0")) {
                    payload += 6;
                    payloadSize -= 6;
                }
                orientation = tiffOrientation(data, payload, payloadSize);
            }
            position += 8 + size + (size & 1);
        }

        if (options.orientation() == OrientationMode.BAKE && orientation > 1) {
            return null;
        }
        boolean keepOrientation = options.orientation() == OrientationMode.KEEP && orientation > 1;

        Map<String, Integer> removed = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        ByteArrayOutputStream body = new ByteArrayOutputStream(data.length);

        position = 12;
        while (position + 8 <= data.length) {
            String fourcc = new String(data, position, 4, StandardCharsets.ISO_8859_1);
            int size = (int) readUInt32(data, position + 4, true);
            if (size < 0 || position + 8 + size > data.length) {
                return null;
            }
            int padded = 8 + size + (size & 1);

            switch (fourcc) {
                case "VP8X" -> {
                    byte[] payload = Arrays.copyOfRange(data, position + 8, position + 8 + size);
                    if (payload.length >= 1) {
                        payload[0] &= ~0x08;   // EXIF 标志
                        payload[0] &= ~0x04;   // XMP 标志
                    }
                    writeWebpChunk(body, fourcc, payload);
                }
                case "EXIF" -> {
                    if (keepOrientation) {
                        writeWebpChunk(body, "EXIF", minimalTiff(orientation));
                    } else {
                        removed.merge("WebP EXIF 块（EXIF 元数据）", 1, Integer::sum);
                    }
                }
                case "XMP " -> removed.merge("WebP XMP 块（编辑软件 / 版权信息）", 1, Integer::sum);
                default -> body.write(data, position, padded);
            }
            position += padded;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
        out.write('R');
        out.write('I');
        out.write('F');
        out.write('F');
        writeUInt32Le(out, body.size() + 4);
        out.write('W');
        out.write('E');
        out.write('B');
        out.write('P');
        byte[] content = body.toByteArray();
        out.write(content, 0, content.length);

        if (keepOrientation) {
            warnings.add("保留 1 项方向标记（Orientation=" + orientation + "）");
        }
        return new Strip(out.toByteArray(), removed, warnings);
    }

    private static void writeWebpChunk(ByteArrayOutputStream out, String fourcc, byte[] payload) {
        out.write(fourcc.getBytes(StandardCharsets.ISO_8859_1), 0, 4);
        writeUInt32Le(out, payload.length);
        out.write(payload, 0, payload.length);
        if ((payload.length & 1) == 1) {
            out.write(0);
        }
    }

    // ---------------------------------------------------------------- GIF

    /**
     * GIF 块剪裁：只删注释扩展和 XMP 应用扩展，帧数据与调色板原样保留。
     *
     * <p>GIF 是逐块链式结构（0x21 扩展 / 0x2C 图像 / 0x3B 结束），必须顺着链走，
     * 不能按固定偏移跳——每帧都可能带局部调色板。
     */
    private static Strip stripGif(byte[] data, Options options) {
        if (data.length < 13) {
            return null;
        }
        Map<String, Integer> removed = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
        out.write(data, 0, 13);

        int packed = data[10] & 0xFF;
        int position = 13;
        if ((packed & 0x80) != 0) {
            int tableSize = 3 * (1 << ((packed & 0x07) + 1));
            if (position + tableSize > data.length) {
                return null;
            }
            out.write(data, position, tableSize);
            position += tableSize;
        }

        while (position < data.length) {
            int block = data[position] & 0xFF;
            if (block == 0x3B) {
                out.write(data, position, 1);
                break;
            }
            if (block == 0x21) {
                if (position + 3 > data.length) {
                    return null;
                }
                int label = data[position + 1] & 0xFF;
                boolean drop = false;
                String removedName = null;
                int end;
                if (label == 0xFF) {
                    int blockSize = data[position + 2] & 0xFF;
                    int appStart = position + 3;
                    if (blockSize >= 11 && appStart + 11 <= data.length
                            && "XMP DataXMP".equals(new String(data, appStart, 11, StandardCharsets.ISO_8859_1))) {
                        drop = true;
                        removedName = "GIF XMP 应用扩展（元数据）";
                    }
                    end = skipSubBlocks(data, appStart + blockSize);
                } else if (label == 0xFE) {
                    drop = true;
                    removedName = "GIF 注释扩展（COM）";
                    end = skipSubBlocks(data, position + 2);
                } else {
                    int blockSize = data[position + 2] & 0xFF;
                    end = skipSubBlocks(data, position + 3 + blockSize);
                }
                if (end <= position) {
                    return null;
                }
                if (drop) {
                    removed.merge(removedName, 1, Integer::sum);
                } else {
                    out.write(data, position, end - position);
                }
                position = end;
                continue;
            }
            if (block == 0x2C) {
                if (position + 10 > data.length) {
                    return null;
                }
                int imagePacked = data[position + 9] & 0xFF;
                int cursor = position + 10;
                if ((imagePacked & 0x80) != 0) {
                    cursor += 3 * (1 << ((imagePacked & 0x07) + 1));
                }
                if (cursor + 1 > data.length) {
                    return null;
                }
                int end = skipSubBlocks(data, cursor + 1);   // 跳过 LZW 最小码长
                if (end <= position) {
                    return null;
                }
                out.write(data, position, end - position);
                position = end;
                continue;
            }
            return null;   // 遇到无法识别的字节，走重编码兜底
        }
        return new Strip(out.toByteArray(), removed, warnings);
    }

    /** 跳过 GIF 的子块链，返回结束位置（含终止的 0 字节）；失败返回 -1。 */
    private static int skipSubBlocks(byte[] data, int position) {
        int cursor = position;
        while (cursor < data.length) {
            int size = data[cursor] & 0xFF;
            cursor++;
            if (size == 0) {
                return cursor;
            }
            cursor += size;
        }
        return -1;
    }

    // ---------------------------------------------------------------- 重编码兜底

    /**
     * 解码后重写：适用于 TIFF / BMP 这类剪裁不出「独立元数据块」的格式。
     *
     * <p>像素会被重新压缩，体积与画质都可能变化，因此结果里标记 {@code reencoded}。
     */
    private static void reencode(Path source, Path temp, Kind kind, Options options) throws IOException {
        BufferedImage image = ImageIO.read(source.toFile());
        if (image == null) {
            throw new IOException("暂不支持去除该格式的元信息：" + kind.label
                    + "（需要专门的解码器，可先转成 JPEG / PNG 再处理）");
        }
        int orientation = options.orientation() == OrientationMode.DROP ? 1 : readOrientation(source);
        if (orientation > 1) {
            image = applyOrientation(image, orientation);
        }

        if (kind == Kind.JPEG) {
            writeJpeg(image, temp);
            return;
        }
        String format = switch (kind) {
            case PNG -> "png";
            case GIF -> "gif";
            case BMP -> "bmp";
            case TIFF -> "tiff";
            default -> "png";
        };
        if (!ImageIO.write(image, format, temp.toFile())) {
            throw new IOException("该格式暂不支持写回：" + format);
        }
    }

    private static void writeJpeg(BufferedImage image, Path target) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(target.toFile())) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.95f);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    /** 用 metadata-extractor 取方向标记；读不到一律当 1（不旋转）。 */
    private static int readOrientation(Path file) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file.toFile());
            ExifIFD0Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (directory != null && directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                int value = directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
                return value >= 1 && value <= 8 ? value : 1;
            }
        } catch (Exception ignored) {
            // 解析失败说明本来就没有可用的方向标记
        }
        return 1;
    }

    /**
     * 按 EXIF 方向把像素摆正。
     *
     * <p>逐个像素搬运而不是用 Graphics2D 叠加变换，避免重绘时被插值糊掉边缘。
     */
    private static BufferedImage applyOrientation(BufferedImage source, int orientation) {
        int width = source.getWidth();
        int height = source.getHeight();
        boolean swap = orientation >= 5;
        int targetWidth = swap ? height : width;
        int targetHeight = swap ? width : height;
        int type = source.getType() == BufferedImage.TYPE_CUSTOM
                ? BufferedImage.TYPE_INT_ARGB : source.getType();
        BufferedImage target = new BufferedImage(targetWidth, targetHeight, type);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int dx;
                int dy;
                switch (orientation) {
                    case 2 -> {
                        dx = width - 1 - x;
                        dy = y;
                    }
                    case 3 -> {
                        dx = width - 1 - x;
                        dy = height - 1 - y;
                    }
                    case 4 -> {
                        dx = x;
                        dy = height - 1 - y;
                    }
                    case 5 -> {
                        dx = y;
                        dy = x;
                    }
                    case 6 -> {
                        dx = height - 1 - y;
                        dy = x;
                    }
                    case 7 -> {
                        dx = height - 1 - y;
                        dy = width - 1 - x;
                    }
                    case 8 -> {
                        dx = y;
                        dy = width - 1 - x;
                    }
                    default -> {
                        dx = x;
                        dy = y;
                    }
                }
                target.setRGB(dx, dy, source.getRGB(x, y));
            }
        }
        return target;
    }

    // ---------------------------------------------------------------- 工具

    private static Kind kindOf(byte[] data) {
        if (data.length >= 2 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) {
            return Kind.JPEG;
        }
        if (startsWith(data, 0, PNG_SIGNATURE)) {
            return Kind.PNG;
        }
        if (data.length >= 6 && data[0] == 'G' && data[1] == 'I' && data[2] == 'F' && data[3] == '8') {
            return Kind.GIF;
        }
        if (data.length >= 12 && startsWith(data, 0, "RIFF") && startsWith(data, 8, "WEBP")) {
            return Kind.WEBP;
        }
        if (data.length >= 2 && data[0] == 'B' && data[1] == 'M') {
            return Kind.BMP;
        }
        if (data.length >= 4 && ((startsWith(data, 0, "II") && data[2] == 42 && data[3] == 0)
                || (startsWith(data, 0, "MM") && data[2] == 0 && data[3] == 42))) {
            return Kind.TIFF;
        }
        if (data.length >= 12 && startsWith(data, 4, "ftyp")) {
            String brand = new String(data, 8, 4, StandardCharsets.ISO_8859_1);
            if (brand.startsWith("heic") || brand.startsWith("heix") || brand.startsWith("mif1")
                    || brand.startsWith("hevc")) {
                return Kind.HEIC;
            }
        }
        return Kind.UNKNOWN;
    }

    private static List<String> describe(Map<String, Integer> removed) {
        List<String> items = new ArrayList<>();
        removed.forEach((name, count) -> items.add(count > 1 ? name + " ×" + count : name));
        return items;
    }

    private static boolean startsWith(byte[] data, int offset, String text) {
        return startsWith(data, offset, text.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static boolean startsWith(byte[] data, int offset, byte[] prefix) {
        if (offset < 0 || offset + prefix.length > data.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[offset + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static long readUInt32(byte[] data, int position, boolean little) {
        long b0 = data[position] & 0xFF;
        long b1 = data[position + 1] & 0xFF;
        long b2 = data[position + 2] & 0xFF;
        long b3 = data[position + 3] & 0xFF;
        return little ? (b3 << 24 | b2 << 16 | b1 << 8 | b0) : (b0 << 24 | b1 << 16 | b2 << 8 | b3);
    }

    private static int readUInt16(byte[] data, int position, boolean little) {
        int b0 = data[position] & 0xFF;
        int b1 = data[position + 1] & 0xFF;
        return little ? (b1 << 8 | b0) : (b0 << 8 | b1);
    }

    private static void writeUInt32(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    /** RIFF（WebP）容器里的所有长度字段都是小端。 */
    private static void writeUInt32Le(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 24) & 0xFF);
    }

    /**
     * 从 TIFF/EXIF 结构里读方向标记（tag 0x0112）。
     *
     * @param data  完整字节
     * @param offset TIFF 头起点
     * @param length 可读长度
     * @return 1..8；解析不出来返回 1
     */
    private static int tiffOrientation(byte[] data, int offset, int length) {
        if (length < 8 || offset < 0 || offset + length > data.length) {
            return 1;
        }
        boolean little;
        if (data[offset] == 'I' && data[offset + 1] == 'I') {
            little = true;
        } else if (data[offset] == 'M' && data[offset + 1] == 'M') {
            little = false;
        } else {
            return 1;
        }
        long ifd = readUInt32(data, offset + 4, little);
        if (ifd < 8 || ifd + 2 > length) {
            return 1;
        }
        int count = readUInt16(data, offset + (int) ifd, little);
        for (int index = 0; index < count; index++) {
            int entry = offset + (int) ifd + 2 + index * 12;
            if (entry + 12 > offset + length) {
                return 1;
            }
            int tag = readUInt16(data, entry, little);
            if (tag != 0x0112) {
                continue;
            }
            int type = readUInt16(data, entry + 2, little);
            long valueCount = readUInt32(data, entry + 4, little);
            if (valueCount < 1) {
                return 1;
            }
            int value = type == 4
                    ? (int) readUInt32(data, entry + 8, little)
                    : readUInt16(data, entry + 8, little);
            return value >= 1 && value <= 8 ? value : 1;
        }
        return 1;
    }
}
