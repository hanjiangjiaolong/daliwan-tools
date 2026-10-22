import com.daliwan.tool.core.QrCodeService;
import com.google.zxing.common.BitMatrix;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 二维码「保存图片」路径冒烟测试（临时工具，不参与打包）。
 *
 * <p>完整复刻 QrCodePane 里 生成 → WritableImage → BufferedImage → ImageIO 写 PNG 的过程，
 * 用于确认原生镜像里这条链路是否可用。
 */
public class QrSaveSmoke {

    public static void main(String[] args) {
        try {
            BitMatrix matrix = QrCodeService.encode("https://example.com/大力丸", 512,
                    QrCodeService.Level.MEDIUM, 2);
            WritableImage fx = render(matrix);
            System.out.println("[fx] " + fx.getWidth() + "x" + fx.getHeight());

            BufferedImage buffered = toBufferedImage(fx);
            System.out.println("[awt] " + buffered.getWidth() + "x" + buffered.getHeight()
                    + " type=" + buffered.getType());

            Path out = Files.createTempDirectory("daliwan-qr-save").resolve("qrcode.png");
            boolean written = ImageIO.write(buffered, "png", out.toFile());
            System.out.println("[write] returned=" + written
                    + " exists=" + Files.exists(out)
                    + " size=" + (Files.exists(out) ? Files.size(out) : -1));
            System.out.println("[writers] " + String.join(",", ImageIO.getWriterFormatNames()));
            System.out.println("[ok]");
            System.exit(0);
        } catch (Throwable t) {
            t.printStackTrace();
            System.out.println("[fail] " + t);
            System.exit(1);
        }
    }

    private static WritableImage render(BitMatrix matrix) {
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        int fg = 0xFF111827;
        int bg = 0xFFFFFFFF;
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            int offset = y * width;
            for (int x = 0; x < width; x++) {
                pixels[offset + x] = matrix.get(x, y) ? fg : bg;
            }
        }
        WritableImage image = new WritableImage(width, height);
        image.getPixelWriter().setPixels(0, 0, width, height,
                PixelFormat.getIntArgbInstance(), pixels, 0, width);
        return image;
    }

    private static BufferedImage toBufferedImage(Image image) {
        int width = (int) Math.round(image.getWidth());
        int height = (int) Math.round(image.getHeight());
        int[] pixels = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height,
                PixelFormat.getIntArgbInstance(), pixels, 0, width);
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        buffered.setRGB(0, 0, width, height, pixels, 0, width);
        return buffered;
    }
}
