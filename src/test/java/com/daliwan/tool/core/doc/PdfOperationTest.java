package com.daliwan.tool.core.doc;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfOperationTest {

    private Path createPdf(Path dir, String name, int pages) throws Exception {
        Path file = dir.resolve(name);
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                document.addPage(new PDPage(new PDRectangle(200 + i * 10, 300)));
            }
            document.save(file.toFile());
        }
        return file;
    }

    private int pagesOf(Path pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            return document.getNumberOfPages();
        }
    }

    private Path createImage(Path dir, String name, int width, int height, Color color) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();
        Path file = dir.resolve(name);
        ImageIO.write(image, "png", file.toFile());
        return file;
    }

    // ---------------------------------------------------------------- 合并

    @Test
    @DisplayName("合并后页数为各文件之和")
    void mergesPages(@TempDir Path dir) throws Exception {
        Path a = createPdf(dir, "a.pdf", 2);
        Path b = createPdf(dir, "b.pdf", 3);
        Path output = dir.resolve("merged.pdf");

        new PdfMerger().merge(List.of(a, b), output);

        assertEquals(5, pagesOf(output));
        assertTrue(Files.size(output) > 0);
    }

    @Test
    @DisplayName("合并保持列表顺序")
    void keepsOrder(@TempDir Path dir) throws Exception {
        // 两份文件的页面宽度刻意不同，才能判断拼接顺序
        Path a = dir.resolve("a.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(new PDRectangle(200, 300)));
            document.save(a.toFile());
        }
        Path b = dir.resolve("b.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(new PDRectangle(250, 300)));
            document.save(b.toFile());
        }
        Path output = dir.resolve("merged.pdf");

        new PdfMerger().merge(List.of(a, b), output);

        try (PDDocument document = Loader.loadPDF(output.toFile())) {
            assertEquals(200f, document.getPage(0).getMediaBox().getWidth());
            assertEquals(250f, document.getPage(1).getMediaBox().getWidth());
        }
    }

    @Test
    @DisplayName("少于两个文件拒绝合并")
    void rejectsSingleFile(@TempDir Path dir) throws Exception {
        Path a = createPdf(dir, "a.pdf", 1);
        ConversionException error = assertThrows(ConversionException.class,
                () -> new PdfMerger().merge(List.of(a), dir.resolve("out.pdf")));
        assertTrue(error.getMessage().contains("两个"));
    }

    @Test
    @DisplayName("空列表拒绝合并")
    void rejectsEmptyList(@TempDir Path dir) {
        assertThrows(ConversionException.class,
                () -> new PdfMerger().merge(List.of(), dir.resolve("out.pdf")));
    }

    // ---------------------------------------------------------------- 图片转 PDF

    @Test
    @DisplayName("每张图片一页，页面尺寸与像素一致")
    void convertsImagesToPages(@TempDir Path dir) throws Exception {
        Path first = createImage(dir, "a.png", 120, 90, Color.RED);
        Path second = createImage(dir, "b.png", 80, 60, Color.BLUE);
        Path output = dir.resolve("images.pdf");

        new ImageToPdfConverter().convert(List.of(first, second), output);

        try (PDDocument document = Loader.loadPDF(output.toFile())) {
            assertEquals(2, document.getNumberOfPages());
            assertEquals(120f, document.getPage(0).getMediaBox().getWidth());
            assertEquals(90f, document.getPage(0).getMediaBox().getHeight());
            assertEquals(80f, document.getPage(1).getMediaBox().getWidth());
        }
    }

    @Test
    @DisplayName("识别支持的图片扩展名")
    void recognizesImageExtensions() {
        assertTrue(ImageToPdfConverter.isSupported(Path.of("a.PNG")));
        assertTrue(ImageToPdfConverter.isSupported(Path.of("a.jpeg")));
        assertTrue(ImageToPdfConverter.isSupported(Path.of("a.tif")));
        assertTrue(!ImageToPdfConverter.isSupported(Path.of("a.pdf")));
        assertTrue(!ImageToPdfConverter.isSupported(Path.of("noext")));
    }

    @Test
    @DisplayName("不支持的格式给出可读提示")
    void rejectsUnsupportedFormat(@TempDir Path dir) throws Exception {
        Path fake = dir.resolve("note.txt");
        Files.writeString(fake, "x");
        ConversionException error = assertThrows(ConversionException.class,
                () -> new ImageToPdfConverter().convert(List.of(fake), dir.resolve("out.pdf")));
        assertTrue(error.getMessage().contains("不支持的图片格式"));
    }

    @Test
    @DisplayName("空图片列表被拒绝")
    void rejectsEmptyImages(@TempDir Path dir) {
        assertThrows(ConversionException.class,
                () -> new ImageToPdfConverter().convert(List.of(), dir.resolve("out.pdf")));
    }

    @Test
    @DisplayName("读取图片尺寸")
    void readsImageSize(@TempDir Path dir) throws Exception {
        Path image = createImage(dir, "s.png", 64, 32, Color.GREEN);
        int[] size = ImageToPdfConverter.sizeOf(image);
        assertEquals(64, size[0]);
        assertEquals(32, size[1]);
    }

    @Test
    @DisplayName("PDF 合并后再拆分的往返保持页数")
    void mergeThenSplit(@TempDir Path dir) throws Exception {
        Path a = createPdf(dir, "a.pdf", 2);
        Path b = createPdf(dir, "b.pdf", 2);
        Path merged = dir.resolve("merged.pdf");
        new PdfMerger().merge(List.of(a, b), merged);

        List<Path> parts = new PdfSplitter().split(merged, "1-2,3-4");
        assertEquals(2, parts.size());
        assertEquals(2, pagesOf(parts.get(0)));
        assertEquals(2, pagesOf(parts.get(1)));
    }
}
