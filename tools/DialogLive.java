package com.daliwan.tool.core.cli;

import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

/**
 * 真窗口验证（临时工具）：弹出结果卡片后截屏，确认透明窗口、阴影与淡入是否正常。
 *
 * <p>用法：{@code DialogLive <输出png>}
 */
public class DialogLive {

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "build/verify/dialog-live.png";
        PdfResultDialog.show(List.of(
                new PdfCommand.Outcome(Path.of("C:\\工作\\清单.xlsx"),
                        Path.of("C:\\工作\\清单.pdf"), null)), null);

        Thread.sleep(2600);
        Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        Rectangle area = new Rectangle(bounds.x + bounds.width - 520, bounds.y + bounds.height - 320, 520, 320);
        BufferedImage image = new Robot().createScreenCapture(area);
        javax.imageio.ImageIO.write(image, "png", new java.io.File(out));
        System.out.println("[shot] " + out + " " + Toolkit.getDefaultToolkit().getScreenSize());
        System.exit(0);
    }
}
