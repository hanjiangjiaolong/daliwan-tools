import com.daliwan.tool.core.OcrService;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * OCR 能力冒烟测试（临时工具，不参与打包）。
 *
 * <p>画一张带中英文的图片 → 预热引擎 → 识别 → 再按文本框分块识别一遍，
 * 用来确认 RapidOCR 的动态库与模型在打好的 jar 里能被正确解压加载。
 * 用法：把本文件对着 target/daliwan_tool.jar 编译后运行。
 */
public class OcrSmoke {

    public static void main(String[] args) {
        try {
            Path dir = Files.createTempDirectory("daliwan-ocr-smoke");
            Path image = dir.resolve("sample.png");
            writeSample(image);
            System.out.println("[image] " + image + " size=" + Files.size(image));

            long warmStart = System.currentTimeMillis();
            OcrService.warmUp();
            System.out.println("[warmup] " + (System.currentTimeMillis() - warmStart) + " ms");

            long start = System.currentTimeMillis();
            String text = OcrService.recognize(image, OcrService.Options.DEFAULT);
            System.out.println("[recognize] " + (System.currentTimeMillis() - start) + " ms");
            System.out.println("---- 文本 ----");
            System.out.println(text);

            String again = OcrService.recognize(image, OcrService.Options.DEFAULT);
            if (!text.equals(again)) {
                throw new IllegalStateException("两次识别结果不一致，疑似编码问题");
            }
            System.out.println("[repeat] 两次结果一致");

            System.out.println("[blocks] " + OcrService.recognizeBlocks(image, null));

            System.out.println("[ok]");
            System.exit(0);
        } catch (Throwable t) {
            t.printStackTrace();
            System.out.println("[fail] " + t);
            System.exit(1);
        }
    }

    private static void writeSample(Path target) throws Exception {
        String[] lines = {
                "大力丸 OCR 冒烟测试",
                "Hello RapidOCR 12345",
                "本地运行，不联网"
        };
        BufferedImage image = new BufferedImage(900, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setColor(Color.BLACK);
        // 用系统里普遍存在的中文字体，缺字体时退化成方框但不影响流程验证
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 36));
        for (int i = 0; i < lines.length; i++) {
            g.drawString(lines[i], 40, 80 + i * 80);
        }
        g.dispose();
        ImageIO.write(image, "png", target.toFile());
    }
}
