import com.aspose.words.Document;
import com.aspose.words.DocumentBuilder;
import com.daliwan.tool.core.doc.ConversionEngine;
import com.daliwan.tool.core.doc.EngineResolver;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.nio.file.Files;
import java.nio.file.Path;

/** 把转换结果的首页渲染成 PNG，用于肉眼确认有没有评估水印。 */
public class DocRender {

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("doc-render");
        Path docx = dir.resolve("sample.docx");
        Document source = new Document();
        DocumentBuilder builder = new DocumentBuilder(source);
        builder.writeln("大力丸 Word 转 PDF 验证");
        for (int i = 1; i <= 8; i++) {
            builder.writeln("正文第 " + i + " 行。");
        }
        source.save(docx.toString());

        Path pdf = dir.resolve("sample.pdf");
        ConversionEngine engine = EngineResolver.resolve(docx);
        engine.convert(docx, pdf);

        Path out = Path.of(args.length > 0 ? args[0] : "build/verify/doc-convert.png");
        Files.createDirectories(out.getParent());
        try (PDDocument parsed = Loader.loadPDF(pdf.toFile())) {
            java.awt.image.BufferedImage image = new PDFRenderer(parsed).renderImage(0, 2f);
            javax.imageio.ImageIO.write(image, "png", out.toFile());
            System.out.println("[ok] " + out.toAbsolutePath());
        }
    }
}
