import com.aspose.cells.Workbook;
import com.aspose.cells.Worksheet;
import com.aspose.slides.ISlide;
import com.aspose.slides.ITextFrame;
import com.aspose.slides.Presentation;
import com.aspose.slides.ShapeType;
import com.daliwan.tool.core.doc.AsposeEngine;
import com.daliwan.tool.core.doc.ConversionEngine;
import com.daliwan.tool.core.doc.EngineResolver;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Word / Excel / PPT 三路转 PDF 冒烟：现场造三份样本，走真实的 EngineResolver 转换，
 * 再用 PDFBox 读回页数与文本，确认不是空壳文件。临时验证工具，不参与打包。
 */
public class AsposeSmoke {

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("aspose-smoke");

        Path docx = makeDocx(dir);
        Path xlsx = makeXlsx(dir);
        Path pptx = makePptx(dir);

        int failed = 0;
        failed += convert(docx, "大力丸 Word 冒烟");
        failed += convert(xlsx, "大力丸 Excel 冒烟");
        failed += convert(pptx, "大力丸 PPT 冒烟");

        System.out.println("[available] " + AsposeEngine.isAvailable()
                + " licensed=" + AsposeEngine.isLicensed()
                + " engine=" + EngineResolver.describeAvailable());
        System.out.println(failed == 0 ? "[ok] all" : "[fail] " + failed + " 个文件");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static int convert(Path source, String marker) {
        long startedAt = System.currentTimeMillis();
        try {
            ConversionEngine engine = EngineResolver.resolve(source);
            Path pdf = source.resolveSibling(
                    source.getFileName().toString().replaceAll("\\.[^.]+$", "") + ".pdf");
            engine.convert(source, pdf);
            long cost = System.currentTimeMillis() - startedAt;
            try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
                String text = new PDFTextStripper().getText(document);
                boolean found = text.contains(marker);
                boolean watermark = text.contains("Evaluation") || text.contains("Aspose");
                System.out.println("[pdf] " + pdf.getFileName() + " " + Files.size(pdf)
                        + " bytes, pages=" + document.getNumberOfPages()
                        + ", 内容=" + found + ", 水印=" + watermark + ", " + cost + " ms");
                return found ? 0 : 1;
            }
        } catch (Exception e) {
            System.out.println("[fail] " + source.getFileName() + " : " + e.getMessage());
            return 1;
        }
    }

    private static Path makeDocx(Path dir) throws Exception {
        Path file = dir.resolve("sample.docx");
        com.aspose.words.Document document = new com.aspose.words.Document();
        com.aspose.words.DocumentBuilder builder = new com.aspose.words.DocumentBuilder(document);
        builder.writeln("大力丸 Word 冒烟");
        builder.writeln("第二行：确认段落没有丢。");
        document.save(file.toString());
        return file;
    }

    private static Path makeXlsx(Path dir) throws Exception {
        Path file = dir.resolve("sample.xlsx");
        Workbook workbook = new Workbook();
        Worksheet sheet = workbook.getWorksheets().get(0);
        sheet.getCells().get("A1").putValue("大力丸 Excel 冒烟");
        sheet.getCells().get("A2").putValue("部门");
        sheet.getCells().get("B2").putValue("数量");
        for (int row = 3; row <= 12; row++) {
            sheet.getCells().get("A" + row).putValue("物料 " + row);
            sheet.getCells().get("B" + row).putValue(row * 10);
        }
        workbook.save(file.toString());
        workbook.dispose();
        return file;
    }

    private static Path makePptx(Path dir) throws Exception {
        Path file = dir.resolve("sample.pptx");
        Presentation presentation = new Presentation();
        try {
            for (int index = 1; index <= 3; index++) {
                ISlide slide = index == 1
                        ? presentation.getSlides().get_Item(0)
                        : presentation.getSlides().addEmptySlide(presentation.getLayoutSlides().get_Item(6));
                var shape = slide.getShapes().addAutoShape(ShapeType.Rectangle, 60, 60, 600, 120);
                ITextFrame frame = shape.getTextFrame();
                frame.setText("大力丸 PPT 冒烟 第 " + index + " 页");
            }
            presentation.save(file.toString(), com.aspose.slides.SaveFormat.Pptx);
        } finally {
            presentation.dispose();
        }
        return file;
    }
}
