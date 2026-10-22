package com.daliwan.tool.core.doc;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfSplitterTest {

    /** 生成一份指定页数的空白 PDF。 */
    private Path createPdf(Path dir, String name, int pages) throws Exception {
        Path file = dir.resolve(name);
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                // 每页宽度递增，用于校验拆分出的确实是对应页
                document.addPage(new PDPage(new PDRectangle(200 + i * 10, 300)));
            }
            document.save(file.toFile());
        }
        return file;
    }

    /** 第 page（1 起始）页的宽度，用于判断拆出来的页面是否正确。 */
    private float widthOf(Path pdf, int page) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            return document.getPage(page - 1).getMediaBox().getWidth();
        }
    }

    private int pagesOf(Path pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            return document.getNumberOfPages();
        }
    }

    @Test
    @DisplayName("读取总页数")
    void countsPages(@TempDir Path dir) throws Exception {
        Path pdf = createPdf(dir, "a.pdf", 5);
        assertEquals(5, PdfSplitter.pageCount(pdf));
    }

    @Test
    @DisplayName("解析页码范围，支持区间、单页与乱序")
    void parsesRanges() throws Exception {
        List<PdfSplitter.PageRange> ranges = PdfSplitter.parseRanges("1-3,5,7-9", 10);
        assertEquals(3, ranges.size());
        assertEquals(1, ranges.get(0).start());
        assertEquals(3, ranges.get(0).end());
        assertEquals(5, ranges.get(1).start());
        assertEquals(5, ranges.get(1).end());
        assertEquals(7, ranges.get(2).start());
        assertEquals(9, ranges.get(2).end());
    }

    @Test
    @DisplayName("起止写反时自动纠正")
    void normalizesReversedRange() throws Exception {
        List<PdfSplitter.PageRange> ranges = PdfSplitter.parseRanges("5-2", 10);
        assertEquals(2, ranges.get(0).start());
        assertEquals(5, ranges.get(0).end());
    }

    @Test
    @DisplayName("支持中文逗号与空格")
    void acceptsChineseComma() throws Exception {
        assertEquals(2, PdfSplitter.parseRanges("1，3", 5).size());
        assertEquals(2, PdfSplitter.parseRanges(" 1 , 3 ", 5).size());
    }

    @Test
    @DisplayName("页码越界时报错并说明总页数")
    void rejectsOutOfRange() {
        ConversionException error = assertThrows(ConversionException.class,
                () -> PdfSplitter.parseRanges("1-99", 5));
        assertTrue(error.getMessage().contains("共 5 页"));
    }

    @Test
    @DisplayName("页码非数字时报错")
    void rejectsNonNumeric() {
        assertThrows(ConversionException.class, () -> PdfSplitter.parseRanges("a-b", 5));
    }

    @Test
    @DisplayName("按范围拆分，每个区间生成一个文件")
    void splitsByRanges(@TempDir Path dir) throws Exception {
        Path pdf = createPdf(dir, "季度报告.pdf", 6);

        List<Path> outputs = new PdfSplitter().split(pdf, "1-2,4");

        assertEquals(2, outputs.size());
        assertEquals("季度报告_p1-2.pdf", outputs.get(0).getFileName().toString());
        assertEquals("季度报告_p4.pdf", outputs.get(1).getFileName().toString());
        assertEquals(2, pagesOf(outputs.get(0)));
        assertEquals(1, pagesOf(outputs.get(1)));
        // 6 页文档上传 "1-2,4"，第一个文件应是原始第 1、2 页，第二个是第 4 页
        assertEquals(200f, widthOf(outputs.get(0), 1));
        assertEquals(210f, widthOf(outputs.get(0), 2));
        assertEquals(230f, widthOf(outputs.get(1), 1));
    }

    @Test
    @DisplayName("页码范围为空时每页拆成一个文件")
    void splitsEveryPage(@TempDir Path dir) throws Exception {
        Path pdf = createPdf(dir, "doc.pdf", 3);

        List<Path> outputs = new PdfSplitter().split(pdf, "");

        assertEquals(3, outputs.size());
        assertEquals("doc_p1.pdf", outputs.get(0).getFileName().toString());
        assertEquals("doc_p3.pdf", outputs.get(2).getFileName().toString());
        for (int i = 0; i < outputs.size(); i++) {
            assertEquals(1, pagesOf(outputs.get(i)));
            assertEquals(200f + i * 10, widthOf(outputs.get(i), 1),
                    "第 " + (i + 1) + " 个文件应对应原始第 " + (i + 1) + " 页");
        }
    }

    @Test
    @DisplayName("同名输出已存在时不覆盖，追加序号")
    void avoidsOverwriting(@TempDir Path dir) throws Exception {
        Path pdf = createPdf(dir, "doc.pdf", 2);
        Files.writeString(dir.resolve("doc_p1.pdf"), "占位");

        List<Path> outputs = new PdfSplitter().split(pdf, "1");

        assertEquals("doc_p1(1).pdf", outputs.get(0).getFileName().toString());
        assertEquals("占位", Files.readString(dir.resolve("doc_p1.pdf")), "原文件不应被覆盖");
    }

    @Test
    @DisplayName("源文件不是 PDF 时给出可读错误")
    void rejectsNonPdf(@TempDir Path dir) throws Exception {
        Path fake = dir.resolve("fake.pdf");
        Files.writeString(fake, "not a pdf");
        assertThrows(ConversionException.class, () -> PdfSplitter.pageCount(fake));
    }
}
