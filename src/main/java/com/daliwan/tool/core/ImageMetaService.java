package com.daliwan.tool.core;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 图片元数据读取：文件信息、图像尺寸与 EXIF / IPTC / XMP / GPS 等标签。
 *
 * <p>尺寸与格式用 JDK 自带的 ImageIO 解析（覆盖面广、不依赖格式库），
 * 元数据用 metadata-extractor 解析各容器格式；两侧都失败才抛异常。
 */
public final class ImageMetaService {

    /** 一条元数据（标签名 → 值）。 */
    public record Entry(String tag, String value) {
    }

    /** 一组元数据（分组名 → 若干条目）。 */
    public record Section(String name, List<Entry> entries) {
    }

    /**
     * 一张图片的完整信息。
     *
     * @param fileName  文件名
     * @param filePath  完整路径
     * @param fileSize  字节数
     * @param format    格式名（JPEG / PNG / …），未知时为空串
     * @param width     像素宽，未知为 0
     * @param height    像素高，未知为 0
     * @param camera    相机「厂牌 型号」，无则空串
     * @param dateTaken 拍摄时间原文，无则空串
     * @param gps       「纬度, 经度」坐标，无则空串
     * @param sections  全部元数据分组
     */
    public record Info(String fileName, String filePath, long fileSize, String format,
                       int width, int height, String camera, String dateTaken, String gps,
                       List<Section> sections) {
    }

    /** 分组名中文化；未列出的原样显示。 */
    private static final Map<String, String> SECTION_NAMES = Map.ofEntries(
            Map.entry("Exif IFD0", "EXIF 基本"),
            Map.entry("Exif SubIFD", "EXIF 拍摄参数"),
            Map.entry("Exif Thumbnail", "EXIF 缩略图"),
            Map.entry("GPS", "GPS 定位"),
            Map.entry("Interoperability", "互操作"),
            Map.entry("IPTC", "IPTC"),
            Map.entry("XMP", "XMP"),
            Map.entry("ICC Profile", "ICC 配置"),
            Map.entry("Adobe JFIF", "JFIF"),
            Map.entry("Adobe APP14", "Adobe APP14"),
            Map.entry("JPEG", "JPEG"),
            Map.entry("JFIF", "JFIF"),
            Map.entry("PNG", "PNG"),
            Map.entry("GIF", "GIF"),
            Map.entry("BMP", "BMP"),
            Map.entry("WebP", "WebP"),
            Map.entry("ICO", "ICO"),
            Map.entry("File Type", "文件类型"),
            Map.entry("FileType", "文件类型"),
            Map.entry("QuickTime", "QuickTime"),
            Map.entry("Photoshop", "Photoshop"),
            Map.entry("Camera ID", "相机标识"),
            Map.entry("Makernote", "厂商注释"),
            Map.entry("Fujifilm Makernote", "富士注释"),
            Map.entry("Canon Makernote", "佳能注释"),
            Map.entry("Nikon Makernote", "尼康注释"),
            Map.entry("Sony Makernote", "索尼注释"),
            Map.entry("Olympus Makernote", "奥林巴斯注释"),
            Map.entry("Panasonic Makernote", "松下注释"),
            Map.entry("Pentax Makernote", "宾得注释"),
            Map.entry("Apple Makernote", "苹果注释"),
            Map.entry("Samsung Makernote", "三星注释"),
            Map.entry("DJI Makernote", "大疆注释"));

    /** 无信息量、直接跳过的分组。 */
    private static final List<String> SKIP_SECTIONS =
            List.of("File", "Huffman", "File Type Directory", "JPEG Processing");

    private static final DateTimeFormatter FILE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ImageMetaService() {
    }

    /**
     * 读取图片的全部元数据。
     *
     * @param file 图片文件
     * @throws IOException 文件不存在、不可读或不是可识别的图片
     */
    public static Info read(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("文件不存在：" + file);
        }
        Path absolute = file.toAbsolutePath();
        long size = Files.size(absolute);

        // 1. 文件信息
        List<Entry> fileEntries = new ArrayList<>();
        fileEntries.add(new Entry("名称", absolute.getFileName().toString()));
        fileEntries.add(new Entry("路径", absolute.getParent() == null
                ? absolute.toString() : absolute.getParent().toString()));
        fileEntries.add(new Entry("大小", formatSize(size)));
        fileEntries.add(new Entry("修改时间", formatFileTime(Files.getLastModifiedTime(absolute))));

        // 2. 尺寸与格式（ImageIO）
        String format = "";
        int width = 0;
        int height = 0;
        try (ImageInputStream in = ImageIO.createImageInputStream(absolute.toFile())) {
            if (in != null) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
                if (readers.hasNext()) {
                    ImageReader reader = readers.next();
                    try {
                        reader.setInput(in, true, true);
                        width = reader.getWidth(0);
                        height = reader.getHeight(0);
                        format = reader.getFormatName();
                    } finally {
                        reader.dispose();
                    }
                }
            }
        } catch (IOException e) {
            // 元数据可能仍可读（例如 HEIC/RAW），留待下一步兜底
        }

        // 3. 元数据标签
        List<Section> sections = new ArrayList<>();
        Metadata metadata = null;
        try {
            metadata = ImageMetadataReader.readMetadata(absolute.toFile());
        } catch (ImageProcessingException | IOException e) {
            if (width == 0 && height == 0) {
                throw new IOException("无法识别的图片格式：" + e.getMessage(), e);
            }
        }

        String camera = "";
        String dateTaken = "";
        String gps = "";
        if (metadata != null) {
            if (width == 0 || height == 0) {
                int[] wh = sizeFromMetadata(metadata);
                width = wh[0];
                height = wh[1];
            }
            if (format.isEmpty()) {
                format = findValue(metadata, "File Type", "File Type Name", "Detected File Type Name");
            }

            ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (ifd0 != null) {
                camera = joinNonEmpty(" ",
                        ifd0.getDescription(ExifIFD0Directory.TAG_MAKE),
                        ifd0.getDescription(ExifIFD0Directory.TAG_MODEL));
            }
            ExifSubIFDDirectory sub = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            if (sub != null) {
                dateTaken = sub.getDescription(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
                if (dateTaken == null) {
                    dateTaken = sub.getDescription(ExifSubIFDDirectory.TAG_DATETIME_DIGITIZED);
                }
            }
            if (dateTaken == null && ifd0 != null) {
                dateTaken = ifd0.getDescription(ExifIFD0Directory.TAG_DATETIME);
            }
            GpsDirectory gpsDir = metadata.getFirstDirectoryOfType(GpsDirectory.class);
            if (gpsDir != null) {
                try {
                    com.drew.lang.GeoLocation location = gpsDir.getGeoLocation();
                    if (location != null && !location.isZero()) {
                        gps = String.format(Locale.ROOT, "%.6f, %.6f",
                                location.getLatitude(), location.getLongitude());
                    }
                } catch (RuntimeException ignored) {
                    // GPS 字段不完整时放弃坐标，不影响其余标签
                }
            }

            Map<String, List<Entry>> bySection = new LinkedHashMap<>();
            for (Directory directory : metadata.getDirectories()) {
                String name = directory.getName();
                if (SKIP_SECTIONS.contains(name)) {
                    continue;
                }
                List<Entry> entries = new ArrayList<>();
                for (Tag tag : directory.getTags()) {
                    String value = tag.getDescription();
                    if (value == null || value.isBlank()) {
                        continue;
                    }
                    entries.add(new Entry(tag.getTagName(), value.trim()));
                }
                if (entries.isEmpty()) {
                    continue;
                }
                String label = SECTION_NAMES.getOrDefault(name, name);
                bySection.computeIfAbsent(label, k -> new ArrayList<>()).addAll(entries);
            }
            bySection.forEach((k, v) -> sections.add(new Section(k, v)));
        }

        // 4. 概要排在最前
        List<Entry> summary = new ArrayList<>();
        summary.add(new Entry("格式", format.isEmpty() ? "未知" : format));
        if (width > 0 && height > 0) {
            summary.add(new Entry("尺寸", width + " × " + height + " 像素"));
            summary.add(new Entry("宽高比", aspectRatio(width, height)));
            summary.add(new Entry("百万像素", String.format(Locale.ROOT, "%.2f MP", width * height / 1_000_000.0)));
            summary.add(new Entry("方向", width >= height ? "横向" : "纵向"));
        } else {
            summary.add(new Entry("尺寸", "未知"));
        }
        if (!camera.isEmpty()) {
            summary.add(new Entry("相机", camera));
        }
        if (dateTaken != null && !dateTaken.isEmpty()) {
            summary.add(new Entry("拍摄时间", dateTaken));
        }
        if (!gps.isEmpty()) {
            summary.add(new Entry("GPS 坐标", gps));
        }

        List<Section> all = new ArrayList<>();
        all.add(new Section("概要", summary));
        all.add(new Section("文件", fileEntries));
        all.addAll(sections);

        return new Info(absolute.getFileName().toString(), absolute.getParent() == null
                ? absolute.toString() : absolute.getParent().toString(),
                size, format.isEmpty() ? "未知" : format, width, height,
                camera, dateTaken == null ? "" : dateTaken, gps, List.copyOf(all));
    }

    /** 渲染成纯文本，便于复制与粘贴到工单。 */
    public static String toText(Info info) {
        StringBuilder out = new StringBuilder();
        for (Section section : info.sections()) {
            out.append("== ").append(section.name()).append(" ==\n");
            for (Entry entry : section.entries()) {
                out.append(entry.tag()).append(": ").append(entry.value()).append('\n');
            }
            out.append('\n');
        }
        return out.toString();
    }

    /** 人类可读的文件大小。 */
    public static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.ROOT, "%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format(Locale.ROOT, "%.2f MB", mb);
        }
        return String.format(Locale.ROOT, "%.2f GB", mb / 1024.0);
    }

    // ---------------------------------------------------------------- 实现

    private static String formatFileTime(FileTime time) {
        return FILE_TIME_FORMAT.format(
                time.toInstant().atZone(ZoneId.systemDefault()));
    }

    /** ImageReader 拿不到尺寸时，从标签值兜底。 */
    private static int[] sizeFromMetadata(Metadata metadata) {
        int width = parseInt(findValue(metadata, "Image Width", "Image Width Pixels", "Exif Image Width"));
        int height = parseInt(findValue(metadata, "Image Height", "Image Height Pixels", "Exif Image Height"));
        return new int[]{width, height};
    }

    private static String findValue(Metadata metadata, String... tagNames) {
        for (Directory directory : metadata.getDirectories()) {
            for (String name : tagNames) {
                for (Tag tag : directory.getTags()) {
                    if (name.equalsIgnoreCase(tag.getTagName())) {
                        String value = tag.getDescription();
                        if (value != null && !value.isBlank()) {
                            return value.trim();
                        }
                    }
                }
            }
        }
        return "";
    }

    private static int parseInt(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        String digits = text.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String aspectRatio(int width, int height) {
        int g = gcd(width, height);
        if (g <= 0) {
            return "";
        }
        String ratio = (width / g) + ":" + (height / g);
        double value = (double) width / height;
        if (width / g > 100 || height / g > 100) {
            ratio = String.format(Locale.ROOT, "%.3f:1", value);
        }
        return ratio;
    }

    private static int gcd(int a, int b) {
        while (b != 0) {
            int t = a % b;
            a = b;
            b = t;
        }
        return a;
    }

    private static String joinNonEmpty(String sep, String... parts) {
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(sep);
            }
            out.append(part.trim());
        }
        return out.toString();
    }
}
