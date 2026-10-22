import com.daliwan.tool.util.PngEncoder;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * PNG 编码器冒烟测试（临时工具，不参与打包）。
 *
 * <p>只用 java.util.zip，不碰 AWT 与 ImageIO，用来确认原生镜像里
 * 二维码「保存图片」这条路完全可用。
 */
public class PngSmoke {

    public static void main(String[] args) {
        try {
            int size = 512;
            int[] pixels = new int[size * size];
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    pixels[y * size + x] = ((x + y) % 32 < 16) ? 0xFF111827 : 0xFFFFFFFF;
                }
            }
            byte[] png = PngEncoder.encode(pixels, size, size);
            boolean signatureOk = png.length > 8
                    && (png[0] & 0xFF) == 0x89 && png[1] == 'P' && png[2] == 'N' && png[3] == 'G';
            System.out.println("[encode] bytes=" + png.length + " signature=" + signatureOk);

            Path out = Files.createTempDirectory("daliwan-png-smoke").resolve("qr.png");
            PngEncoder.write(out, pixels, size, size);
            System.out.println("[write] " + out + " size=" + Files.size(out));

            boolean ok = signatureOk && Files.size(out) == png.length;
            System.out.println(ok ? "[ok]" : "[fail]");
            System.exit(ok ? 0 : 1);
        } catch (Throwable t) {
            t.printStackTrace();
            System.out.println("[fail] " + t);
            System.exit(1);
        }
    }
}
