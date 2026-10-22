package com.daliwan.tool.core.doc;

import com.daliwan.tool.util.FileNames;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 拆分：把一份 PDF 按页拆成多个文件，或按指定页码范围提取。
 *
 * <p>直接使用 PDFBox 自带的 {@link Splitter}：没有系统字体依赖，也不会往标准输出打印横幅。
 */
public final class PdfSplitter {

    /** 页码范围，闭区间，页码从 1 开始。 */
    public record PageRange(int start, int end) {

        public int size() {
            return end - start + 1;
        }

        /** 用于文件名，如 {@code p3} 或 {@code p1-5}。 */
        public String label() {
            return start == end ? "p" + start : "p" + start + "-" + end;
        }
    }

    /**
     * 解析页码范围表达式，支持 {@code 1-3,5,7-9} 与中文逗号。
     *
     * @param expression 表达式，空白表示「每页拆一个文件」
     * @param totalPages 文档总页数，用于越界校验
     */
    public static List<PageRange> parseRanges(String expression, int totalPages) throws ConversionException {
        List<PageRange> ranges = new ArrayList<>();
        if (expression == null || expression.isBlank()) {
            return ranges;
        }
        for (String token : expression.split("[,，]")) {
            String part = token.trim();
            if (part.isEmpty()) {
                continue;
            }
            int dash = part.indexOf('-');
            int start;
            int end;
            if (dash > 0 && dash < part.length() - 1) {
                start = parsePage(part.substring(0, dash), totalPages);
                end = parsePage(part.substring(dash + 1), totalPages);
            } else {
                start = parsePage(part, totalPages);
                end = start;
            }
            if (start > end) {
                int swap = start;
                start = end;
                end = swap;
            }
            ranges.add(new PageRange(start, end));
        }
        return ranges;
    }

    private static int parsePage(String text, int totalPages) throws ConversionException {
        int page;
        try {
            page = Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new ConversionException("页码格式不正确：「" + text + "」");
        }
        if (page < 1 || page > totalPages) {
            throw new ConversionException("页码 " + page + " 超出范围，该文档共 " + totalPages + " 页");
        }
        return page;
    }

    /** 读取 PDF 总页数。 */
    public static int pageCount(Path file) throws ConversionException {
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            return document.getNumberOfPages();
        } catch (IOException e) {
            throw new ConversionException("无法读取 PDF：" + e.getMessage(), e);
        }
    }

    /**
     * 按表达式拆分，输出到源文件同目录。
     *
     * @param source     PDF 文件
     * @param expression 页码范围；空白时每页一个文件
     * @return 生成的文件列表
     */
    public List<Path> split(Path source, String expression) throws ConversionException {
        return doSplit(source, expression);
    }

    private List<Path> doSplit(Path source, String expression) throws ConversionException {
        int totalPages = pageCount(source);
        List<PageRange> ranges = parseRanges(expression, totalPages);
        if (ranges.isEmpty()) {
            for (int page = 1; page <= totalPages; page++) {
                ranges.add(new PageRange(page, page));
            }
        }

        Path directory = source.toAbsolutePath().getParent();
        if (directory == null) {
            throw new ConversionException("无法定位源文件所在目录");
        }
        String base = FileNames.baseName(source.getFileName().toString());

        List<Path> outputs = new ArrayList<>();
        // 源文档只解析一次：默认「每页一个文件」时，100 页的 PDF 否则要完整加载 100 遍
        try (PDDocument sourceDocument = Loader.loadPDF(source.toFile())) {
            for (PageRange range : ranges) {
                Path target = FileNames.uniqueTarget(directory, base + "_" + range.label() + ".pdf");
                extract(sourceDocument, target, range);
                outputs.add(target);
            }
        } catch (IOException e) {
            throw new ConversionException("拆分失败：" + rootMessage(e), e);
        }
        return outputs;
    }

    /** 把指定页码范围提取为一份新 PDF。 */
    public void extract(Path source, Path target, PageRange range) throws ConversionException {
        try (PDDocument sourceDocument = Loader.loadPDF(source.toFile())) {
            extract(sourceDocument, target, range);
        } catch (IOException e) {
            throw new ConversionException("拆分失败：" + rootMessage(e), e);
        }
    }

    /** 从已加载的文档里提取页码范围。 */
    private static void extract(PDDocument sourceDocument, Path target, PageRange range)
            throws ConversionException {
        // Splitter 的起止页码就是人读的 1 起始闭区间，与 PageRange 一致
        Splitter splitter = new Splitter();
        splitter.setStartPage(range.start());
        splitter.setEndPage(range.end());
        // 默认每页切一份；设为范围长度才能让这段页码落在同一份文档里
        splitter.setSplitAtPage(Math.max(1, range.size()));
        List<PDDocument> parts;
        try {
            parts = splitter.split(sourceDocument);
        } catch (IOException e) {
            throw new ConversionException("拆分失败：" + rootMessage(e), e);
        }
        try {
            if (parts.size() != 1) {
                throw new ConversionException("拆分结果异常：期望 1 份，实际 " + parts.size() + " 份");
            }
            parts.get(0).save(target.toFile());
        } catch (IOException e) {
            throw new ConversionException("拆分失败：" + rootMessage(e), e);
        } finally {
            for (PDDocument part : parts) {
                try {
                    part.close();
                } catch (IOException ignored) {
                    // 关闭失败不影响已生成的文件
                }
            }
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
