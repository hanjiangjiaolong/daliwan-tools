import com.daliwan.tool.core.QrCodeService;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 二维码能力冒烟测试（临时工具，不参与打包）。
 *
 * <p>编码再解码跑一个来回，改动二维码相关代码后用它快速复验。
 */
public class QrSmoke {

    public static void main(String[] args) {
        try {
            Path dir = Files.createTempDirectory("daliwan-qr-smoke");
            Path file = dir.resolve("qr.png");

            BitMatrix matrix = QrCodeService.encode("https://example.com/大力丸", 320,
                    QrCodeService.Level.MEDIUM, 1);
            BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);
            ImageIO.write(image, "png", file.toFile());
            System.out.println("[encode] " + file + " size=" + Files.size(file));

            String decoded = QrCodeService.decode(ImageIO.read(file.toFile()));
            System.out.println("[decode] " + decoded);

            System.out.println("[ok]");
            System.exit(0);
        } catch (Throwable t) {
            t.printStackTrace();
            System.out.println("[fail] " + t);
            System.exit(1);
        }
    }
}
