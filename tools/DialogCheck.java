package com.daliwan.tool.core.cli;

import com.daliwan.tool.util.SystemTheme;

import javax.imageio.ImageIO;
import javax.swing.JDialog;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 结果卡片截图（临时验证工具，不参与打包）。
 *
 * <p>直接构造 {@link PdfResultDialog} 的窗口内容，把卡片画到一张透明底的图上，
 * 免得靠屏幕截图受分辨率与其他窗口干扰。
 *
 * <p>用法：{@code DialogCheck <输出png> [ok|fail|warn|dark|dark-fail]}
 */
public class DialogCheck {

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "build/verify/pdf-dialog.png";
        String mode = args.length > 1 ? args[1] : "ok";
        boolean dark = mode.startsWith("dark");
        String theme = dark ? "dark" : "light";
        SystemTheme.remember(theme);

        List<PdfCommand.Outcome> outcomes = new ArrayList<>();
        String message = null;
        switch (mode) {
            case "fail", "dark-fail" -> outcomes.add(new PdfCommand.Outcome(
                    Path.of("C:\\工作\\季度汇报.pptx"), null,
                    "转换失败：不支持的文档格式"));
            case "warn" -> {
                outcomes.add(new PdfCommand.Outcome(
                        Path.of("C:\\工作\\清单.xlsx"), Path.of("C:\\工作\\清单.pdf"), null));
                outcomes.add(new PdfCommand.Outcome(
                        Path.of("C:\\工作\\季度汇报.pptx"), null,
                        "转换失败：不支持的文档格式"));
            }
            case "many" -> {
                for (int i = 1; i <= 7; i++) {
                    outcomes.add(new PdfCommand.Outcome(
                            Path.of("C:\\工作\\第" + i + "份材料.xlsx"),
                            Path.of("C:\\工作\\第" + i + "份材料.pdf"), null));
                }
            }
            default -> {
                outcomes.add(new PdfCommand.Outcome(
                        Path.of("C:\\工作\\清单.xlsx"), Path.of("C:\\工作\\清单.pdf"), null));
                outcomes.add(new PdfCommand.Outcome(
                        Path.of("C:\\工作\\2026年第三季度经营分析与预算执行情况说明.docx"),
                        Path.of("C:\\工作\\2026年第三季度经营分析与预算执行情况说明.pdf"), null));
            }
        }

        JDialog dialog = PdfResultDialog.build(outcomes, message);
        Component card = dialog.getContentPane();
        Dimension size = card.getSize();
        // 卡片四角是透明的，垫一层中性底色才能看出阴影的形状
        BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(dark ? new Color(0x3A3D46) : new Color(0x9AA0AA));
        g.fillRect(0, 0, size.width, size.height);
        card.printAll(g);
        g.dispose();
        ImageIO.write(image, "png", new java.io.File(out));
        System.out.println("[shot] " + out + " " + size.width + "x" + size.height);
        System.exit(0);
    }
}
