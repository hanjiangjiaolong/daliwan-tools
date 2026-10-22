import com.aspose.words.Document;
import com.aspose.words.DocumentBuilder;
import com.daliwan.tool.core.doc.AsposeEngine;
import com.daliwan.tool.core.doc.ConversionEngine;
import com.daliwan.tool.core.doc.EngineResolver;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Word → PDF 冒烟：先造一份 docx，再走真实的 EngineResolver 转 PDF，
 * 最后用 PDFBox 读回文本，确认内容对、且知道评估版水印长什么样。
 *
 * <p>用法：java DocSmoke（JVM）或打成原生 exe 复验。
 */
public class DocSmoke {

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("doc-smoke");
        Path docx = dir.resolve("sample.docx");

        Document source = new Document();
        DocumentBuilder builder = new DocumentBuilder(source);
        builder.writeln("大力丸 Word 转 PDF 冒烟测试");
        for (int i = 1; i <= 30; i++) {
            builder.writeln("正文第 " + i + " 行，用于确认长文档没有被截断。");
        }
        source.save(docx.toString());
        System.out.println("[docx] " + docx + "  " + Files.size(docx) + " bytes");

        System.out.println("[available] AsposeEngine=" + AsposeEngine.isAvailable()
                + " licensed=" + AsposeEngine.isLicensed()
                + " capability=" + EngineResolver.describeCapability());

        ConversionEngine engine = EngineResolver.resolve(docx);
        System.out.println("[engine] " + engine.name());

        Path pdf = dir.resolve("sample.pdf");
        engine.convert(docx, pdf);
        System.out.println("[pdf] " + pdf + "  " + Files.size(pdf) + " bytes");

        try (PDDocument parsed = Loader.loadPDF(pdf.toFile())) {
            System.out.println("[pdf] pages=" + parsed.getNumberOfPages());
            String text = new PDFTextStripper().getText(parsed);
            boolean hasLine = text.contains("正文第 30 行");
            boolean watermark = text.contains("Evaluation") || text.contains("Aspose");
            System.out.println("[text] 末行保留=" + hasLine + " 水印=" + watermark);
            if (watermark) {
                int at = Math.max(text.indexOf("Evaluation"), text.indexOf("Aspose"));
                System.out.println("[text] 水印片段: " + text.substring(at, Math.min(at + 90, text.length()))
                        .replace('\n', ' '));
            }
            System.out.println("[text] head: " + text.substring(0, Math.min(300, text.length())).replace('\n', '|'));
            System.out.println(hasLine ? "[ok] convert" : "[fail] 内容缺失");
        }
    }
}
