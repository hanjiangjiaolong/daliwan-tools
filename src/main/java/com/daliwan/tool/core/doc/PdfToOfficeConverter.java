package com.daliwan.tool.core.doc;

import com.daliwan.tool.util.FileNames;
import stirling.software.officeconvert.PdfToDocx;
import stirling.software.officeconvert.PdfToPptx;
import stirling.software.officeconvert.PdfToText;
import stirling.software.officeconvert.PdfToXlsx;

import java.nio.file.Path;
import java.util.Locale;

/**
 * 反方向的转换：PDF → 可编辑的 Office 文档。
 *
 * <p>与 {@link StirlingEngine} 同属 Stirling Office Convert（MIT）的两个模块：
 * 那边是 Office 渲染成 PDF，这里是 PDF 反解回段落、表格、列表再用 POI 写出。
 * 同样不依赖本机 Office / LibreOffice，也不起外部进程。
 *
 * <p>能还原的是「内容」而不是「版式的像素级复刻」：段落、标题、列表、表格、
 * 页眉页脚会变成真的 Word 元素，但复杂排版（多栏混排、艺术字、手写批注）
 * 只能退化成近似结果。这点必须在界面上说清楚，不能让用户以为和 PDF 一模一样。
 *
 * <p>上游按 <b>Java 25</b> 编译发布，{@link #isAvailable()} 在本机运行时不够新时
 * 返回 false，调用方据此提示而不是抛一串 ClassNotFoundException。
 */
public final class PdfToOfficeConverter {

    /**
     * PDF 可以转成哪些东西。
     *
     * <p>每一项都带三样东西：输出扩展名、界面上的名字、右键菜单用的命令行开关。
     * 三者绑在一起是为了避免「菜单写着转 Word、实际写出 xlsx」这类漂移。
     */
    public enum Target {
        WORD("docx", "Word", "--to-docx"),
        EXCEL("xlsx", "Excel", "--to-xlsx"),
        SLIDES("pptx", "PPT", "--to-pptx"),
        TEXT("txt", "文本", "--to-txt");

        private final String extension;
        private final String label;
        private final String flag;

        Target(String extension, String label, String flag) {
            this.extension = extension;
            this.label = label;
            this.flag = flag;
        }

        /** 输出扩展名，不带点。 */
        public String extension() {
            return extension;
        }

        /** 界面与右键菜单上显示的名字。 */
        public String label() {
            return label;
        }

        /** 命令行开关，注册表里写的就是它。 */
        public String flag() {
            return flag;
        }
    }

    private PdfToOfficeConverter() {
    }

    /** 核心类是否在类路径上且可加载（Java 25+ 才成立）。 */
    public static boolean isAvailable() {
        try {
            Class.forName("stirling.software.officeconvert.PdfToDocx",
                    false, PdfToOfficeConverter.class.getClassLoader());
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /** 由命令行开关反查目标格式；认不出来时返回 null。 */
    public static Target fromFlag(String flag) {
        if (flag == null) {
            return null;
        }
        String normalized = flag.trim().toLowerCase(Locale.ROOT);
        for (Target target : Target.values()) {
            if (target.flag().equals(normalized)) {
                return target;
            }
        }
        return null;
    }

    /** 该文件是不是 PDF（按扩展名判断，不读内容）。 */
    public static boolean isPdf(Path file) {
        if (file == null || file.getFileName() == null) {
            return false;
        }
        return ".pdf".equals(FileNames.extension(file.getFileName().toString()));
    }

    /**
     * 输出路径：源 PDF 同目录、同名换扩展名，重名时追加序号，绝不覆盖已有文件。
     */
    public static Path targetOf(Path pdf, Target target) {
        Path absolute = pdf.toAbsolutePath();
        Path directory = absolute.getParent();
        String base = FileNames.baseName(absolute.getFileName().toString());
        return FileNames.uniqueTarget(directory, base + "." + target.extension());
    }

    /**
     * 转一份 PDF。
     *
     * @param pdf     源 PDF
     * @param output  输出文件，由 {@link #targetOf} 之类保证不覆盖
     * @param target  目标格式
     */
    public static void convert(Path pdf, Path output, Target target) throws ConversionException {
        if (!isAvailable()) {
            throw new ConversionException("PDF 转 Office 组件不可用（需要 Java 25 及以上运行环境），"
                    + "请使用完整版大力丸发行包");
        }
        try {
            switch (target) {
                case WORD -> PdfToDocx.convert(pdf, output, PdfToDocx.Options.defaults());
                case EXCEL -> PdfToXlsx.convert(pdf, output, PdfToXlsx.Options.defaults());
                case SLIDES -> PdfToPptx.convert(pdf, output, PdfToPptx.Options.defaults());
                case TEXT -> PdfToText.convert(pdf, output, PdfToDocx.Options.defaults());
            }
        } catch (Exception e) {
            throw new ConversionException("PDF 转 " + target.label() + " 失败：" + rootMessage(e), e);
        }
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
