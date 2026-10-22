package com.daliwan.tool.core.doc;

import com.aspose.cells.TxtLoadOptions;
import com.aspose.cells.Workbook;
import com.aspose.slides.Presentation;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 内置 Aspose 引擎：Word / Excel / PPT 全部就地转换，不依赖本机装的 Office
 * 或 LibreOffice，也不走 COM，界面不会被 Office 弹窗打断。
 *
 * <p>三个格式分属 Aspose 的三个产品，jar 都随程序打包：
 * <ul>
 *   <li>{@code Aspose.Words} —— doc / docx / rtf / odt / wps</li>
 *   <li>{@code Aspose.Cells} —— xls / xlsx / xlsm / xlsb / csv / ods</li>
 *   <li>{@code Aspose.Slides} —— ppt / pptx / pptm / pps / ppsx / odp</li>
 * </ul>
 *
 * <p>没有授权文件时按评估版运行，输出会带水印。把对应产品的授权文件
 * （{@code Aspose.Words.Java.lic} / {@code Aspose.Cells.Java.lic} /
 * {@code Aspose.Slides.Java.lic}，或一份 {@code license.lic}）放到程序目录
 * 或程序目录的 {@code lib} 下即可自动生效。
 */
public class AsposeEngine implements ConversionEngine {

    /** 三个产品，决定用哪套 API 与哪份授权文件。 */
    public enum Family {
        WORD, EXCEL, SLIDES
    }

    private static final List<String> WORD_EXTENSIONS =
            List.of(".doc", ".docx", ".rtf", ".odt", ".wps");

    private static final List<String> EXCEL_EXTENSIONS =
            List.of(".xls", ".xlsx", ".xlsm", ".xlsb", ".csv", ".ods");

    private static final List<String> SLIDES_EXTENSIONS =
            List.of(".ppt", ".pptx", ".pptm", ".pps", ".ppsx", ".odp");

    private static final Set<Family> LICENSE_APPLIED = java.util.EnumSet.noneOf(Family.class);

    @Override
    public String name() {
        return "Aspose";
    }

    /** 三个产品是否都在类路径上；打好的包里恒为真，缺 jar 时退回 LibreOffice。 */
    public static boolean isAvailable() {
        return isAvailable(Family.WORD) && isAvailable(Family.EXCEL) && isAvailable(Family.SLIDES);
    }

    /** 单个产品是否可用。 */
    public static boolean isAvailable(Family family) {
        String className = switch (family) {
            case WORD -> "com.aspose.words.Document";
            case EXCEL -> "com.aspose.cells.Workbook";
            case SLIDES -> "com.aspose.slides.Presentation";
        };
        try {
            Class.forName(className, false, AsposeEngine.class.getClassLoader());
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /** 判断扩展名属于哪个产品，不认识的返回 null。 */
    public static Family familyOf(Path file) {
        String extension = extensionOf(file);
        if (extension.isEmpty()) {
            return null;
        }
        if (WORD_EXTENSIONS.contains(extension)) {
            return Family.WORD;
        }
        if (EXCEL_EXTENSIONS.contains(extension)) {
            return Family.EXCEL;
        }
        if (SLIDES_EXTENSIONS.contains(extension)) {
            return Family.SLIDES;
        }
        return null;
    }

    /** 判断扩展名是否在 Aspose 的能力范围内。 */
    public static boolean supports(Path file) {
        return familyOf(file) != null;
    }

    /** 全部可转换的扩展名，供界面提示与右键菜单注册复用。 */
    public static List<String> extensions() {
        List<String> all = new java.util.ArrayList<>();
        all.addAll(WORD_EXTENSIONS);
        all.addAll(EXCEL_EXTENSIONS);
        all.addAll(SLIDES_EXTENSIONS);
        return List.copyOf(all);
    }

    /**
     * 授权文件的候选位置：程序目录、其 lib 子目录。
     *
     * @param family 产品；传 null 时列出通用授权文件名
     */
    public static List<Path> licenseCandidates(Family family) {
        Path home = Path.of(System.getProperty("toolbox.home", "."));
        String product = switch (family == null ? Family.WORD : family) {
            case WORD -> "Aspose.Words.Java.lic";
            case EXCEL -> "Aspose.Cells.Java.lic";
            case SLIDES -> "Aspose.Slides.Java.lic";
        };
        return List.of(
                home.resolve(product),
                home.resolve("lib").resolve(product),
                home.resolve("license.lic"),
                home.resolve("lib").resolve("license.lic")
        );
    }

    /** 程序目录是否放了任意一份授权文件；未授权时三个产品都按评估版输出。 */
    public static boolean isLicensed() {
        for (Family family : Family.values()) {
            if (isLicensed(family)) {
                return true;
            }
        }
        return false;
    }

    /** 指定产品是否已放授权文件。 */
    public static boolean isLicensed(Family family) {
        return licenseCandidates(family).stream().anyMatch(Files::isRegularFile);
    }

    @Override
    public void convert(Path source, Path pdf) throws ConversionException {
        Family family = familyOf(source);
        if (family == null) {
            throw new ConversionException("不支持的文件类型：" + source.getFileName());
        }
        if (!isAvailable(family)) {
            throw new ConversionException("内置 Aspose 引擎缺少 " + family + " 组件，"
                    + "请改用完整版程序或安装 LibreOffice");
        }
        applyLicenseOnce(family);
        try {
            switch (family) {
                case WORD -> convertWord(source, pdf);
                case EXCEL -> convertExcel(source, pdf);
                case SLIDES -> convertSlides(source, pdf);
            }
        } catch (ConversionException e) {
            throw e;
        } catch (Exception e) {
            throw new ConversionException("Aspose 转换失败：" + rootMessage(e), e);
        }
    }

    // ---------------------------------------------------------------- 各产品

    private static void convertWord(Path source, Path pdf) throws Exception {
        com.aspose.words.Document document = new com.aspose.words.Document(source.toString());
        try {
            document.save(pdf.toString(), com.aspose.words.SaveFormat.PDF);
        } finally {
            // Aspose 需要显式释放，否则重复调用会占住文件句柄
            document.cleanup();
        }
    }

    private static void convertExcel(Path source, Path pdf) throws Exception {
        Workbook workbook = readWorkbook(source);
        try {
            workbook.save(pdf.toString(), com.aspose.cells.SaveFormat.PDF);
        } finally {
            workbook.dispose();
        }
    }

    private static void convertSlides(Path source, Path pdf) throws Exception {
        Presentation presentation = new Presentation(source.toString());
        try {
            presentation.save(pdf.toString(), com.aspose.slides.SaveFormat.Pdf);
        } finally {
            presentation.dispose();
        }
    }

    /**
     * 读取工作表。
     *
     * <p>CSV 没有编码声明，Aspose 默认按 ISO-8859-1 解读，中文会变成问号。
     * 这里先按 UTF-8 试解一遍字节，解不通再退回 GBK —— 国内导出的 CSV 绝大多数
     * 是这两种编码之一，比直接问用户强。
     */
    private static Workbook readWorkbook(Path source) throws Exception {
        if (!".csv".equals(extensionOf(source))) {
            return new Workbook(source.toString());
        }
        TxtLoadOptions options = new TxtLoadOptions();
        options.setEncoding(textEncoding(source));
        return new Workbook(source.toString(), options);
    }

    /** UTF-8 能严格解出字符就用 UTF-8，否则按 GBK 处理。 */
    private static com.aspose.cells.Encoding textEncoding(Path source) throws Exception {
        try {
            byte[] bytes = Files.readAllBytes(source);
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return com.aspose.cells.Encoding.getEncoding("UTF-8");
        } catch (CharacterCodingException e) {
            return com.aspose.cells.Encoding.getEncoding(charsetOr("GBK"));
        } catch (Exception e) {
            return com.aspose.cells.Encoding.getEncoding("UTF-8");
        }
    }

    private static String charsetOr(String name) {
        try {
            return Charset.isSupported(name) ? name : "UTF-8";
        } catch (Exception e) {
            return "UTF-8";
        }
    }

    /** 若程序目录存在该产品的授权文件则自动加载，缺失时按评估版运行（输出带水印）。 */
    private static synchronized void applyLicenseOnce(Family family) {
        if (LICENSE_APPLIED.contains(family)) {
            return;
        }
        LICENSE_APPLIED.add(family);
        for (Path licenseFile : licenseCandidates(family)) {
            if (!Files.isRegularFile(licenseFile)) {
                continue;
            }
            try {
                String path = licenseFile.toString();
                switch (family) {
                    case WORD -> new com.aspose.words.License().setLicense(path);
                    case EXCEL -> new com.aspose.cells.License().setLicense(path);
                    case SLIDES -> new com.aspose.slides.License().setLicense(path);
                }
                return;
            } catch (Exception ignored) {
                // 授权文件格式不对，继续按评估版运行
            }
        }
    }

    private static String extensionOf(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot) : "";
    }

    private static String rootMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }
}
