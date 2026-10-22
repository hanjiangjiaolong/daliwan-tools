package com.daliwan.tool.core.doc;

import stirling.software.officeconvert.topdf.OfficeToPdf;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 内置开源引擎：Word / Excel / PPT 就地转 PDF，基于 Stirling Office Convert
 * （MIT 许可，Stirling PDF 团队出品），PDFBox + Apache POI 纯 Java 渲染，
 * 不依赖本机装的 Office 或 LibreOffice，也不走外部进程。
 *
 * <p>上游按 <b>Java 25</b> 编译发布，因此本引擎只能在 Java 25+ 的运行时上工作；
 * 低版本 JDK 上类加载即失败，{@link #isAvailable()} 返回 false，
 * {@link EngineResolver} 会自动退回 LibreOffice。
 *
 * <p>支持的扩展名与旧引擎保持同一范围，唯独去掉了 {@code .wps}——
 * WPS 私有格式不在上游支持列表里，与其注册了转不出来，不如一开始就不挂。
 */
public class StirlingEngine implements ConversionEngine {

    /** 单文件转换上限；超时由上游中断并报错，与旧行为一致。 */
    private static final Duration TIMEOUT = Duration.ofSeconds(180);

    private static final List<String> WORD_EXTENSIONS =
            List.of(".doc", ".docx", ".rtf", ".odt");

    private static final List<String> EXCEL_EXTENSIONS =
            List.of(".xls", ".xlsx", ".xlsm", ".xlsb", ".csv", ".ods");

    private static final List<String> SLIDES_EXTENSIONS =
            List.of(".ppt", ".pptx", ".pptm", ".pps", ".ppsx", ".odp");

    @Override
    public String name() {
        return "Stirling";
    }

    /** 引擎类是否在类路径上且可加载（Java 25+ 才成立）；打好的包里恒为真。 */
    public static boolean isAvailable() {
        try {
            Class.forName("stirling.software.officeconvert.topdf.OfficeToPdf",
                    false, StirlingEngine.class.getClassLoader());
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /** 全部可转换的扩展名，供界面提示与右键菜单注册复用。 */
    public static List<String> extensions() {
        List<String> all = new ArrayList<>();
        all.addAll(WORD_EXTENSIONS);
        all.addAll(EXCEL_EXTENSIONS);
        all.addAll(SLIDES_EXTENSIONS);
        return List.copyOf(all);
    }

    /** 判断扩展名是否在引擎能力范围内。 */
    public static boolean supports(Path file) {
        return WORD_EXTENSIONS.contains(extensionOf(file))
                || EXCEL_EXTENSIONS.contains(extensionOf(file))
                || SLIDES_EXTENSIONS.contains(extensionOf(file));
    }

    @Override
    public void convert(Path source, Path pdf) throws ConversionException {
        if (!isAvailable()) {
            throw new ConversionException("内置 Stirling 引擎不可用（需要 Java 25 及以上运行环境），"
                    + "请改用完整版程序或安装 LibreOffice");
        }
        try {
            OfficeToPdf.convert(source, pdf, OfficeToPdf.Options.defaults().timeout(TIMEOUT));
        } catch (Exception e) {
            throw new ConversionException("Stirling 转换失败：" + rootMessage(e), e);
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
