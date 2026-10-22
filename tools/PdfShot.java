import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;

/** 把 PDF 首页渲染成 PNG，用于肉眼比对版式保真度。用法：PdfShot <pdf> <png> [dpi] */
public class PdfShot {

    public static void main(String[] args) throws Exception {
        Path pdf = Path.of(args[0]);
        Path png = Path.of(args[1]);
        float dpi = args.length > 2 ? Float.parseFloat(args[2]) : 110f;
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            PDFRenderer renderer = new PDFRenderer(doc);
            BufferedImage image = renderer.renderImageWithDPI(0, dpi);
            ImageIO.write(image, "png", png.toFile());
            System.out.println("[png] " + png + "  (" + image.getWidth() + "x" + image.getHeight() + ")");
        }
    }
}
