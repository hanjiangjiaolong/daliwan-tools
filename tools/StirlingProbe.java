import com.daliwan.tool.core.doc.AsposeEngine;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import stirling.software.officeconvert.topdf.OfficeToPdf;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 对比 Stirling Office Convert（MIT）与内置 Aspose 转 PDF 的结果。
 * 只在 JDK 25 下运行（Stirling 要求 Java 25+）。
 */
public class StirlingProbe {

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args.length > 0 ? args[0] : "build/samples");
        Path out = Path.of("build/stirling-out");
        Files.createDirectories(out);

        System.out.println("java.version = " + System.getProperty("java.version"));

        for (String name : new String[]{"sample.docx", "sample.pptx", "sample.xlsx"}) {
            Path src = dir.resolve(name);
            if (!Files.exists(src)) {
                System.out.println("[跳过] 缺少 " + src);
                continue;
            }
            String base = name.replace('.', '-');

            Path stirlingOut = out.resolve(base + "-stirling.pdf");
            long t0 = System.currentTimeMillis();
            String stirlingResult;
            try {
                OfficeToPdf.convert(src, stirlingOut);
                stirlingResult = describe(stirlingOut);
            } catch (Throwable e) {
                stirlingResult = "失败：" + root(e);
            }
            long stirlingMs = System.currentTimeMillis() - t0;

            Path asposeOut = out.resolve(base + "-aspose.pdf");
            long t1 = System.currentTimeMillis();
            String asposeResult;
            try {
                new AsposeEngine().convert(src, asposeOut);
                asposeResult = describe(asposeOut);
            } catch (Throwable e) {
                asposeResult = "失败：" + root(e);
            }
            long asposeMs = System.currentTimeMillis() - t1;

            System.out.println();
            System.out.println("=== " + name + " ===");
            System.out.println("Stirling  " + stirlingMs + " ms  " + stirlingResult);
            System.out.println("Aspose    " + asposeMs + " ms  " + asposeResult);
        }
    }

    private static String describe(Path pdf) {
        if (!Files.exists(pdf)) {
            return "未生成 PDF";
        }
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            String text = new PDFTextStripper().getText(doc);
            String preview = text.replaceAll("\\s+", " ").strip();
            if (preview.length() > 70) {
                preview = preview.substring(0, 70) + "…";
            }
            return String.format("%d 页, %.1f KB, 文本「%s」",
                    doc.getNumberOfPages(), pdf.toFile().length() / 1024.0, preview);
        } catch (Exception e) {
            return "读取失败：" + root(e);
        }
    }

    private static String root(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        String m = c.getMessage();
        return c.getClass().getSimpleName() + (m == null || m.isBlank() ? "" : ": " + m);
    }
}
