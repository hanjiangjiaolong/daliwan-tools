import com.daliwan.tool.core.doc.PdfToOfficeConverter;
import com.daliwan.tool.ui.PdfToOfficePane;
import com.daliwan.tool.ui.ThemeManager;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

/**
 * PDF 转 Office 的端到端自检（临时验证工具，不参与打包）。
 *
 * <p>做两件事：① 造一份带文字的 PDF，四种目标格式各转一次，打印产物大小与文本内容；
 * ② 把新页面渲染出来截图，确认下拉、按钮、提示文字没有溢出或错位。
 *
 * <p>用法：{@code java Pdf2OfficeShot <输出目录> [dark|light]}。
 */
public final class Pdf2OfficeShot {

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "build/verify");
        String theme = args.length > 1 ? args[1] : "dark";
        Files.createDirectories(out);

        System.out.println("[engine] available=" + PdfToOfficeConverter.isAvailable());
        Path pdf = out.resolve("sample.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 16);
                stream.newLineAtOffset(60, 700);
                stream.showText("Daliwan converts PDF back to Office");
                stream.newLineAtOffset(0, -24);
                stream.showText("second line with a table-ish layout");
                stream.endText();
            }
            document.save(pdf.toFile());
        }

        if (PdfToOfficeConverter.isAvailable()) {
            for (PdfToOfficeConverter.Target target : PdfToOfficeConverter.Target.values()) {
                Path target1 = PdfToOfficeConverter.targetOf(pdf, target);
                long startedAt = System.currentTimeMillis();
                PdfToOfficeConverter.convert(pdf, target1, target);
                long cost = System.currentTimeMillis() - startedAt;
                System.out.println("[convert] " + target.label() + " -> " + target1.getFileName()
                        + " (" + Files.size(target1) + " 字节, " + cost + " ms)");
                if (target == PdfToOfficeConverter.Target.TEXT) {
                    System.out.println("[text] " + Files.readString(target1).replace("\r", ""));
                }
            }
        }

        CountDownLatch done = new CountDownLatch(1);
        Platform.startup(() -> {
            try {
                Stage stage = new Stage();
                PdfToOfficePane pane = new PdfToOfficePane();
                Scene scene = new Scene(pane, 960, 620);
                ThemeManager.install(scene, theme);
                stage.setScene(scene);
                stage.show();
                Platform.runLater(() -> {
                    try {
                        Thread.sleep(600);
                        WritableImage image = scene.snapshot(null);
                        int width = (int) image.getWidth();
                        int height = (int) image.getHeight();
                        javafx.scene.image.PixelReader reader = image.getPixelReader();
                        BufferedImage buffered =
                                new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                        for (int y = 0; y < height; y++) {
                            for (int x = 0; x < width; x++) {
                                buffered.setRGB(x, y, reader.getArgb(x, y));
                            }
                        }
                        File file = out.resolve("pdf2office-" + theme + ".png").toFile();
                        ImageIO.write(buffered, "png", file);
                        System.out.println("[shot] " + file.getAbsolutePath());
                    } catch (Exception e) {
                        System.out.println("[shot] failed: " + e.getMessage());
                    } finally {
                        done.countDown();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                done.countDown();
            }
        });
        done.await();
        Platform.exit();
        System.exit(0);
    }
}
