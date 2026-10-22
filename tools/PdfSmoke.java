import com.daliwan.tool.core.doc.ImageToPdfConverter;
import com.daliwan.tool.core.doc.PdfMerger;
import com.daliwan.tool.core.doc.PdfSplitter;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * PDF 能力冒烟测试（临时工具，不参与打包）。
 *
 * <p>在 JVM 上跑一遍「图片转 PDF → 拆分 → 合并」，改动 PDF 相关代码后先跑它确认没坏。
 */
public class PdfSmoke {

    /**
     * 主动初始化 AWT： Toolkit / GraphicsEnvironment / ImageIO 全量枚举。
     * 原生镜像里这些类靠 JNI 访问字段，必须让代理把它们都记录下来，
     * 否则运行时会逐个抛 NoSuchFieldError。
     */
    private static void warmUpAwt() {
        java.awt.Toolkit.getDefaultToolkit();
        java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        java.awt.Font.decode(java.awt.Font.DIALOG);
        BufferedImage probe = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        probe.createGraphics().dispose();
        BufferedImage opaque = new BufferedImage(8, 8, BufferedImage.TYPE_BYTE_GRAY);
        opaque.createGraphics().dispose();
        BufferedImage indexed = new BufferedImage(8, 8, BufferedImage.TYPE_BYTE_INDEXED);
        indexed.createGraphics().dispose();
        for (String name : ImageIO.getReaderFormatNames()) {
            ImageIO.getImageReadersByFormatName(name);
        }
        for (String name : ImageIO.getWriterFormatNames()) {
            ImageIO.getImageWritersByFormatName(name);
        }
        ImageIO.getImageReadersByMIMEType("image/png");
        ImageIO.getImageReadersBySuffix("jpg");
    }

    public static void main(String[] args) {
        warmUpAwt();
        probe("org.apache.pdfbox.pdmodel.PDDocument");
        probe("org.dromara.pdf.pdfbox.core.base.Document");
        try {
            Path dir = Files.createTempDirectory("daliwan-pdf-smoke");

            Path imageA = writeImage(dir, "a.png", 240, 320, Color.RED);
            Path imageB = writeImage(dir, "b.png", 300, 200, Color.BLUE);

            Path merged = dir.resolve("images.pdf");
            Path out = new ImageToPdfConverter().convert(List.of(imageA, imageB), merged);
            System.out.println("[image->pdf] " + out + " exists=" + Files.exists(out)
                    + " size=" + Files.size(out));

            List<Path> split = new PdfSplitter().split(out, "");
            System.out.println("[split] files=" + split.size());

            Path merged2 = dir.resolve("merged.pdf");
            new PdfMerger().merge(split, merged2);
            System.out.println("[merge] " + merged2 + " size=" + Files.size(merged2));

            System.out.println("[ok]");
            System.exit(0);
        } catch (Throwable t) {
            t.printStackTrace();
            System.out.println("[fail] " + t);
            System.exit(1);
        }
    }

    private static void probe(String name) {
        try {
            Class<?> only = Class.forName(name, false, PdfSmoke.class.getClassLoader());
            System.out.println("[probe-load] " + name + " -> " + only.getModule());
        } catch (Throwable t) {
            System.out.println("[probe-load-fail] " + name + " -> " + t);
        }
        try {
            Class.forName(name);
            System.out.println("[probe-init] " + name + " ok");
        } catch (Throwable t) {
            System.out.println("[probe-init-fail] " + name + " -> " + t);
        }
    }

    private static Path writeImage(Path dir, String name, int w, int h, Color color) throws Exception {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, w, h);
        g.dispose();
        Path file = dir.resolve(name);
        ImageIO.write(image, "png", file.toFile());
        return file;
    }
}
