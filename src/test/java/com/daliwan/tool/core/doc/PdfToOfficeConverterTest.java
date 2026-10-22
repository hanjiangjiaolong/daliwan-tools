package com.daliwan.tool.core.doc;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfToOfficeConverterTest {

    @Test
    @DisplayName("开关能反查到目标格式，认不出来的返回 null")
    void resolvesTargetByFlag() {
        assertEquals(PdfToOfficeConverter.Target.WORD, PdfToOfficeConverter.fromFlag("--to-docx"));
        assertEquals(PdfToOfficeConverter.Target.EXCEL, PdfToOfficeConverter.fromFlag("--to-xlsx"));
        assertEquals(PdfToOfficeConverter.Target.SLIDES, PdfToOfficeConverter.fromFlag("--to-pptx"));
        assertEquals(PdfToOfficeConverter.Target.TEXT, PdfToOfficeConverter.fromFlag("--to-txt"));
        assertNull(PdfToOfficeConverter.fromFlag("--to-pdf"), "反方向的开关不该被这里认走");
        assertNull(PdfToOfficeConverter.fromFlag(null));
        assertNull(PdfToOfficeConverter.fromFlag("docx"));
    }

    @Test
    @DisplayName("目标格式自带扩展名、显示名与开关，三者一一对应")
    void targetCarriesExtensionLabelFlag() {
        assertEquals("docx", PdfToOfficeConverter.Target.WORD.extension());
        assertEquals("Word", PdfToOfficeConverter.Target.WORD.label());
        assertEquals("--to-docx", PdfToOfficeConverter.Target.WORD.flag());
        assertEquals("txt", PdfToOfficeConverter.Target.TEXT.extension());
        assertEquals("文本", PdfToOfficeConverter.Target.TEXT.label());
    }

    @Test
    @DisplayName("只认 PDF，大小写都算")
    void detectsPdfByExtension() {
        assertTrue(PdfToOfficeConverter.isPdf(Path.of("D:\\docs\\合同.pdf")));
        assertTrue(PdfToOfficeConverter.isPdf(Path.of("合同.PDF")));
        assertFalse(PdfToOfficeConverter.isPdf(Path.of("合同.docx")));
        assertFalse(PdfToOfficeConverter.isPdf(null));
    }

    @Test
    @DisplayName("输出文件放在源 PDF 同目录，换掉扩展名")
    void namesOutputBesideSource(@TempDir Path dir) {
        Path pdf = dir.resolve("报告.pdf");
        assertEquals(dir.resolve("报告.docx"),
                PdfToOfficeConverter.targetOf(pdf, PdfToOfficeConverter.Target.WORD));
        assertEquals(dir.resolve("报告.txt"),
                PdfToOfficeConverter.targetOf(pdf, PdfToOfficeConverter.Target.TEXT));
    }

    @Test
    @DisplayName("同名文件已存在时追加序号，绝不覆盖")
    void avoidsOverwriting(@TempDir Path dir) throws Exception {
        Path pdf = dir.resolve("报告.pdf");
        Files.writeString(dir.resolve("报告.docx"), "existing");

        Path target = PdfToOfficeConverter.targetOf(pdf, PdfToOfficeConverter.Target.WORD);
        assertEquals(dir.resolve("报告(1).docx"), target);
        assertFalse(Files.exists(target));
    }

    @Test
    @DisplayName("真转一份：PDF 里的文字能落进 Word / 文本")
    void convertsRealPdf(@TempDir Path dir) throws Exception {
        if (!PdfToOfficeConverter.isAvailable()) {
            // 本机运行时不是 Java 25 时跳过：这是环境限制，不是逻辑错误
            return;
        }
        Path pdf = dir.resolve("sample.pdf");
        writeSamplePdf(pdf);

        Path docx = PdfToOfficeConverter.targetOf(pdf, PdfToOfficeConverter.Target.WORD);
        PdfToOfficeConverter.convert(pdf, docx, PdfToOfficeConverter.Target.WORD);
        assertTrue(Files.isRegularFile(docx), "应当生成 .docx");
        assertTrue(Files.size(docx) > 0, "生成的文件不该是空的");

        Path txt = PdfToOfficeConverter.targetOf(pdf, PdfToOfficeConverter.Target.TEXT);
        PdfToOfficeConverter.convert(pdf, txt, PdfToOfficeConverter.Target.TEXT);
        String text = Files.readString(txt);
        assertTrue(text.contains("Daliwan"), "文本内容应带上源 PDF 里的字，实际：" + text);
    }

    @Test
    @DisplayName("组件不可用时给出人话提示，而不是抛一堆类加载异常")
    void reportsUnavailableEngine() {
        if (PdfToOfficeConverter.isAvailable()) {
            return;
        }
        ConversionException error = org.junit.jupiter.api.Assertions.assertThrows(
                ConversionException.class,
                () -> PdfToOfficeConverter.convert(Path.of("a.pdf"), Path.of("a.docx"),
                        PdfToOfficeConverter.Target.WORD));
        assertNotNull(error.getMessage());
        assertTrue(error.getMessage().contains("Java 25"), "实际提示：" + error.getMessage());
    }

    /** 写一页带文字的 PDF，用作转换输入。 */
    private static void writeSamplePdf(Path file) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 14);
                stream.newLineAtOffset(60, 700);
                stream.showText("Daliwan sample paragraph");
                stream.endText();
            }
            document.save(file.toFile());
        }
    }
}
