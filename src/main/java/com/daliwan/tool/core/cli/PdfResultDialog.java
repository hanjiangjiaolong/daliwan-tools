package com.daliwan.tool.core.cli;

import com.daliwan.tool.core.ToolSettings;
import com.daliwan.tool.util.SystemTheme;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * 右键菜单转换完成后的轻量结果卡片。
 *
 * <p>用 Swing 而不是 JavaFX：这条路径不启动主界面，为了一个「已生成」的提示
 * 去拉起整个 JavaFX 运行时并不划算。
 *
 * <p>外观是自绘的：系统无边框窗口 + 逐像素透明的窗口背景，卡片本身画出圆角、
 * 层叠阴影与底部倒计时进度条，配色跟随程序的主题设置（深色/浅色）。
 * 显示时淡入并轻微上浮，鼠标移入会暂停自动关闭，方便看清文件名。
 */
final class PdfResultDialog {

    /** 卡片四周留给阴影的空白。 */
    private static final int SHADOW = 14;
    /** 卡片圆角。 */
    private static final int RADIUS = 14;
    /** 卡片内容宽度（不含阴影）。 */
    private static final int CONTENT_WIDTH = 400;
    /** 成功后自动关闭的时间。 */
    private static final int AUTO_CLOSE_MILLIS = 12_000;
    /** 列表最多显示几行，超出折叠成一句。 */
    private static final int MAX_ROWS = 5;

    private PdfResultDialog() {
    }

    static void show(List<PdfCommand.Outcome> outcomes, String message) {
        if (GraphicsEnvironment.isHeadless()) {
            printToConsole(outcomes, message);
            return;
        }
        try {
            SwingUtilities.invokeLater(() -> {
                try {
                    JDialog dialog = build(outcomes, message);
                    // 逐像素透明只有支持的平台才有；不支持就直接显示，不做动画
                    boolean translucent = Boolean.TRUE.equals(
                            dialog.getRootPane().getClientProperty("daliwan.translucent"));
                    if (translucent) {
                        try {
                            dialog.setOpacity(0f);
                        } catch (Throwable e) {
                            translucent = false;
                        }
                    }
                    dialog.setVisible(true);
                    Card card = (Card) dialog.getContentPane();
                    if (translucent) {
                        fadeIn(dialog, () -> card.startCountdown(dialog, AUTO_CLOSE_MILLIS));
                    } else {
                        card.startCountdown(dialog, AUTO_CLOSE_MILLIS);
                    }
                } catch (Throwable e) {
                    printToConsole(outcomes, message);
                }
            });
        } catch (Throwable e) {
            printToConsole(outcomes, message);
        }
    }

    /** 没有图形环境时退化成控制台输出，转换本身已经完成了。 */
    private static void printToConsole(List<PdfCommand.Outcome> outcomes, String message) {
        System.out.println(message == null ? "转 PDF 结束" : message);
        for (PdfCommand.Outcome outcome : outcomes) {
            System.out.println((outcome.ok() ? "[ok] " : "[fail] ") + outcome.source()
                    + (outcome.ok() ? " -> " + outcome.target() : " : " + outcome.error()));
        }
    }

    /** 构造窗口内容但不显示；测试与截图工具用。 */
    static JDialog build(List<PdfCommand.Outcome> outcomes, String message) {
        int success = (int) outcomes.stream().filter(PdfCommand.Outcome::ok).count();
        int failed = outcomes.size() - success;
        Severity severity = message != null || success == 0
                ? Severity.FAIL
                : (failed > 0 ? Severity.WARN : Severity.OK);

        Palette palette = Palette.current();
        JDialog dialog = new JDialog((Frame) null, "大力丸 · 转 PDF", false);
        dialog.setUndecorated(true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setAlwaysOnTop(true);
        // 不抢焦点：用户刚在资源管理器里选了文件，键盘焦点不该被这张卡片抢走
        dialog.setFocusableWindowState(false);
        dialog.setIconImage(loadIcon());
        dialog.getRootPane().setOpaque(false);
        dialog.getRootPane().registerKeyboardAction(
                (ActionEvent e) -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        // 逐像素透明只有在支持半透明的平台上可用，不支持就退回普通不透明窗口
        boolean translucent = false;
        try {
            dialog.setBackground(new Color(0, 0, 0, 0));
            translucent = true;
        } catch (Throwable ignored) {
            // 忽略：阴影会画在窗口内部，看起来略平但功能不受影响
        }
        dialog.getRootPane().putClientProperty("daliwan.translucent", translucent);

        Card card = new Card(palette, severity == Severity.OK);
        card.setLayout(new BorderLayout(0, 14));
        card.setBorder(BorderFactory.createEmptyBorder(
                SHADOW + 20, SHADOW + 22, SHADOW + 20, SHADOW + 22));

        card.add(header(palette, severity, outcomes, success, failed, message), BorderLayout.NORTH);
        card.add(rows(palette, outcomes), BorderLayout.CENTER);
        card.add(footer(palette, outcomes, success, failed, message), BorderLayout.SOUTH);

        dialog.setContentPane(card);
        dialog.pack();
        dialog.setMinimumSize(dialog.getSize());
        placeBottomRight(dialog);
        return dialog;
    }

    // ------------------------------------------------------------------ 头部

    private static JComponent header(Palette palette, Severity severity, List<PdfCommand.Outcome> outcomes,
                                     int success, int failed, String message) {
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(false);
        header.add(new Badge(palette, severity), BorderLayout.WEST);

        JPanel text = new JPanel();
        text.setLayout(new javax.swing.BoxLayout(text, javax.swing.BoxLayout.Y_AXIS));
        text.setOpaque(false);
        text.setBorder(BorderFactory.createEmptyBorder(1, 0, 0, 0));

        JLabel title = new JLabel(severity.title);
        title.setFont(font(Font.BOLD, 16f));
        title.setForeground(severity.titleColor(palette));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel(truncate(severity.describe(outcomes, success, failed, message), 40));
        subtitle.setFont(font(Font.PLAIN, 12.5f));
        subtitle.setForeground(palette.muted);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        text.add(title);
        text.add(subtitle);
        header.add(text, BorderLayout.CENTER);
        header.add(closeButton(palette), BorderLayout.EAST);
        return header;
    }

    private static JComponent closeButton(Palette palette) {
        FlatButton close = new FlatButton("", palette.muted, null, palette.hover, -1) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                // 自带字体里不一定有 ✕，直接画两条线更稳
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? getForeground().darker() : getForeground());
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                double cx = getWidth() / 2.0;
                double cy = getHeight() / 2.0;
                double arm = 4.5;
                g2.draw(new java.awt.geom.Line2D.Double(cx - arm, cy - arm, cx + arm, cy + arm));
                g2.draw(new java.awt.geom.Line2D.Double(cx + arm, cy - arm, cx - arm, cy + arm));
                g2.dispose();
            }
        };
        close.setPreferredSize(new Dimension(26, 26));
        close.setToolTipText("关闭");
        close.addActionListener(e -> {
            Window window = SwingUtilities.getWindowAncestor(close);
            if (window != null) {
                window.dispose();
            }
        });
        // 让按钮在垂直方向与标题首行对齐
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(close, BorderLayout.NORTH);
        return wrapper;
    }

    // ------------------------------------------------------------------ 列表

    private static JComponent rows(Palette palette, List<PdfCommand.Outcome> outcomes) {
        JPanel list = new JPanel();
        list.setLayout(new javax.swing.BoxLayout(list, javax.swing.BoxLayout.Y_AXIS));
        list.setOpaque(false);

        int shown = 0;
        for (PdfCommand.Outcome outcome : outcomes) {
            if (shown >= MAX_ROWS) {
                JLabel more = new JLabel("还有 " + (outcomes.size() - shown) + " 个文件未列出");
                more.setFont(font(Font.PLAIN, 12f));
                more.setForeground(palette.faint);
                more.setBorder(BorderFactory.createEmptyBorder(6, 2, 0, 0));
                more.setAlignmentX(Component.LEFT_ALIGNMENT);
                list.add(more);
                break;
            }
            if (shown > 0) {
                list.add(javax.swing.Box.createVerticalStrut(7));
            }
            if (outcome.ok()) {
                String name = outcome.target() == null ? "" : outcome.target().getFileName().toString();
                list.add(fileRow(palette, "✓", palette.success, name, null));
            } else {
                String name = outcome.source() == null ? "" : outcome.source().getFileName().toString();
                list.add(fileRow(palette, "✕", palette.danger, name, oneLine(outcome.error())));
            }
            shown++;
        }
        return list;
    }

    /** 一行结果：状态符号 + 文件名（失败时下方再补一行原因）。 */
    private static JComponent fileRow(Palette palette, String glyph, Color glyphColor,
                                      String name, String detail) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel mark = new JLabel(glyph);
        mark.setFont(font(Font.BOLD, 12f));
        mark.setForeground(glyphColor);
        mark.setPreferredSize(new Dimension(14, 18));
        mark.setVerticalAlignment(JLabel.TOP);
        row.add(mark, BorderLayout.WEST);

        JPanel lines = new JPanel();
        lines.setLayout(new javax.swing.BoxLayout(lines, javax.swing.BoxLayout.Y_AXIS));
        lines.setOpaque(false);

        JLabel title = new JLabel(truncate(name, 34));
        title.setFont(font(Font.PLAIN, 12.5f));
        title.setForeground(palette.text);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        lines.add(title);

        if (detail != null && !detail.isBlank()) {
            JLabel reason = new JLabel(truncate(detail, 44));
            reason.setFont(font(Font.PLAIN, 11.5f));
            reason.setForeground(palette.faint);
            reason.setAlignmentX(Component.LEFT_ALIGNMENT);
            reason.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));
            lines.add(reason);
        }
        row.add(lines, BorderLayout.CENTER);
        return row;
    }

    // ------------------------------------------------------------------ 底部

    private static JComponent footer(Palette palette, List<PdfCommand.Outcome> outcomes,
                                     int success, int failed, String message) {
        JPanel footer = new JPanel(new BorderLayout(10, 0));
        footer.setOpaque(false);

        JLabel hint = new JLabel(severityHint(success, failed, message));
        hint.setFont(font(Font.PLAIN, 11.5f));
        hint.setForeground(palette.faint);
        hint.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));
        footer.add(hint, BorderLayout.CENTER);

        JPanel actions = new JPanel();
        actions.setLayout(new javax.swing.BoxLayout(actions, javax.swing.BoxLayout.X_AXIS));
        actions.setOpaque(false);

        if (success > 0) {
            FlatButton open = new FlatButton("打开文件夹", palette.onAccent, palette.accent, palette.accentHover, 8);
            open.setFont(font(Font.PLAIN, 12.5f));
            open.setPadding(new Insets(7, 16, 7, 16));
            open.addActionListener(e -> {
                openFolder(outcomes);
                disposeWindow(open);
            });
            actions.add(open);
            actions.add(javax.swing.Box.createHorizontalStrut(8));
        }

        FlatButton close = new FlatButton("关闭", palette.text2, null, palette.hover, 8);
        close.setFont(font(Font.PLAIN, 12.5f));
        close.setBorderColor(palette.border);
        close.setPadding(new Insets(7, 16, 7, 16));
        close.addActionListener(e -> disposeWindow(close));
        actions.add(close);

        footer.add(actions, BorderLayout.EAST);
        return footer;
    }

    private static void disposeWindow(Component component) {
        Window window = SwingUtilities.getWindowAncestor(component);
        if (window != null) {
            window.dispose();
        }
    }

    private static String severityHint(int success, int failed, String message) {
        if (message != null) {
            return "";
        }
        if (failed == 0) {
            return "12 秒后自动关闭 · 鼠标移入暂停";
        }
        if (success == 0) {
            return "源文件未改动，请检查格式或换引擎重试";
        }
        return "失败的文件保持原样，可单独重试";
    }

    // ------------------------------------------------------------------ 卡片

    /** 窗口内容：自绘圆角卡片、阴影、底部倒计时进度条。 */
    private static final class Card extends JPanel {

        private final Palette palette;
        private final boolean countdown;
        private double fraction = 1.0;
        private long deadlineAt;
        private long pausedAt;
        private boolean hovering;

        Card(Palette palette, boolean countdown) {
            this.palette = palette;
            this.countdown = countdown;
            setOpaque(false);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    if (hovering) {
                        return;
                    }
                    hovering = true;
                    pausedAt = System.currentTimeMillis();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (!hovering) {
                        return;
                    }
                    hovering = false;
                    deadlineAt += System.currentTimeMillis() - pausedAt;
                }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            size.width = Math.max(size.width, SHADOW * 2 + CONTENT_WIDTH);
            return size;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            double x = SHADOW;
            double y = SHADOW;
            double w = getWidth() - SHADOW * 2.0;
            double h = getHeight() - SHADOW * 2.0;

            // 层叠圆角矩形当阴影：越靠里越实，外圈逐渐透明，比单层描边自然
            for (int i = SHADOW; i >= 1; i--) {
                int alpha = Math.min(255, 5 + (SHADOW - i) * 3);
                g2.setComposite(AlphaComposite.SrcOver);
                g2.setColor(new Color(palette.shadow.getRed(), palette.shadow.getGreen(),
                        palette.shadow.getBlue(), alpha));
                g2.fill(new RoundRectangle2D.Double(x - i, y - i + 3, w + i * 2, h + i * 2 + 2,
                        RADIUS + i, RADIUS + i));
            }

            g2.setComposite(AlphaComposite.SrcOver);
            g2.setColor(palette.card);
            g2.fill(new RoundRectangle2D.Double(x, y, w, h, RADIUS, RADIUS));

            g2.setColor(palette.border);
            g2.setStroke(new BasicStroke(1f));
            g2.draw(new RoundRectangle2D.Double(x + 0.5, y + 0.5, w - 1, h - 1, RADIUS, RADIUS));

            if (countdown) {
                double barY = y + h - 9;
                double barX = x + 20;
                double barW = w - 40;
                g2.setColor(palette.track);
                g2.fill(new RoundRectangle2D.Double(barX, barY, barW, 3, 3, 3));
                if (fraction > 0) {
                    g2.setColor(palette.accent);
                    g2.fill(new RoundRectangle2D.Double(barX, barY, Math.max(3, barW * fraction), 3, 3, 3));
                }
            }
            g2.dispose();
        }

        void startCountdown(Window window, long totalMillis) {
            if (!countdown) {
                return;
            }
            deadlineAt = System.currentTimeMillis() + totalMillis;
            Timer timer = new Timer(40, e -> {
                if (hovering) {
                    return;
                }
                long left = deadlineAt - System.currentTimeMillis();
                fraction = Math.max(0, left / (double) totalMillis);
                repaint(0, getHeight() - SHADOW - 12, getWidth(), SHADOW + 12);
                if (left <= 0) {
                    ((Timer) e.getSource()).stop();
                    window.dispose();
                }
            });
            timer.setInitialDelay(0);
            timer.start();
        }
    }

    /** 头部状态徽标：渐变圆形 + 勾/叹号/叉。 */
    private static final class Badge extends JComponent {

        private final Palette palette;
        private final Severity severity;
        private final int size = 38;

        Badge(Palette palette, Severity severity) {
            this.palette = palette;
            this.severity = severity;
            setOpaque(false);
            setPreferredSize(new Dimension(size, size));
            setMinimumSize(new Dimension(size, size));
            setMaximumSize(new Dimension(size, size));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color tint = severity.accent(palette);
            double inset = 1.5;
            g2.setPaint(new GradientPaint(0, 0, tint.brighter(),
                    0, size, tint.darker()));
            g2.fill(new Ellipse2D.Double(inset, inset, size - inset * 2, size - inset * 2));

            g2.setColor(new Color(255, 255, 255, 245));
            g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            double cx = size / 2.0;
            double cy = size / 2.0;
            if (severity == Severity.OK) {
                Path2D check = new Path2D.Double();
                check.moveTo(cx - 7, cy + 0.5);
                check.lineTo(cx - 2, cy + 5.5);
                check.lineTo(cx + 7.5, cy - 5.5);
                g2.draw(check);
            } else if (severity == Severity.WARN) {
                g2.draw(new java.awt.geom.Line2D.Double(cx, cy - 7, cx, cy + 2));
                g2.fill(new Ellipse2D.Double(cx - 1.5, cy + 5, 3, 3));
            } else {
                g2.draw(new java.awt.geom.Line2D.Double(cx - 6, cy - 6, cx + 6, cy + 6));
                g2.draw(new java.awt.geom.Line2D.Double(cx + 6, cy - 6, cx - 6, cy + 6));
            }
            g2.dispose();
        }
    }

    /** 扁平圆角按钮：自绘背景与边框，文字交给父类绘制。 */
    private static class FlatButton extends JButton {

        private final Color background;
        private final Color hoverBackground;
        private Color borderColor;
        private final int radius;
        private Insets padding = new Insets(6, 12, 6, 12);

        FlatButton(String text, Color foreground, Color background, Color hoverBackground, int radius) {
            super(text);
            this.background = background;
            this.hoverBackground = hoverBackground == null ? background : hoverBackground;
            this.radius = radius;
            setForeground(foreground);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        }

        void setBorderColor(Color color) {
            this.borderColor = color;
        }

        void setPadding(Insets padding) {
            this.padding = padding;
            setBorder(BorderFactory.createEmptyBorder(
                    padding.top, padding.left, padding.bottom, padding.right));
        }

        @Override
        public Insets getInsets() {
            return padding;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color fill = background;
            if (hoverBackground != null && (getModel().isRollover() || getModel().isPressed())) {
                fill = hoverBackground;
            }
            if (fill != null && fill.getAlpha() > 0) {
                int arc = radius < 0 ? getHeight() : radius * 2;
                g2.setColor(fill);
                g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), arc, arc));
            }
            if (borderColor != null) {
                int arc = radius < 0 ? getHeight() : radius * 2;
                g2.setColor(borderColor);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, arc, arc));
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ------------------------------------------------------------------ 配色

    /** 结果状态：决定标题、徽标与配色。 */
    private enum Severity {
        OK("已转换为 PDF", ColorToken.SUCCESS),
        WARN("部分文件转换失败", ColorToken.WARNING),
        FAIL("转换未完成", ColorToken.DANGER);

        private final String title;
        private final ColorToken token;

        Severity(String title, ColorToken token) {
            this.title = title;
            this.token = token;
        }

        Color titleColor(Palette palette) {
            return token.pick(palette);
        }

        Color accent(Palette palette) {
            return token.pick(palette);
        }

        String describe(List<PdfCommand.Outcome> outcomes, int success, int failed, String message) {
            if (message != null && !message.isBlank()) {
                return message;
            }
            if (outcomes.isEmpty()) {
                return "没有收到要转换的文件";
            }
            int total = outcomes.size();
            return switch (this) {
                case OK -> total == 1
                        ? "已保存到源文件所在目录"
                        : total + " 个文件已保存到各自源目录";
                case WARN -> success + " 个成功 · " + failed + " 个失败";
                case FAIL -> failed == 1 ? "1 个文件未能转换" : failed + " 个文件均未能转换";
            };
        }
    }

    /** 语义颜色在浅色/深色两套配置里的取值。 */
    private enum ColorToken {
        SUCCESS, WARNING, DANGER;

        Color pick(Palette palette) {
            return switch (this) {
                case SUCCESS -> palette.success;
                case WARNING -> palette.warning;
                case DANGER -> palette.danger;
            };
        }
    }

    /** 一整套配色，深色与浅色各一份，与主界面的主题变量对应。 */
    private record Palette(Color card, Color border, Color text, Color text2, Color muted, Color faint,
                           Color track, Color hover, Color accent, Color accentHover, Color onAccent,
                           Color success, Color warning, Color danger, Color shadow) {

        private static final Palette LIGHT = new Palette(
                new Color(0xFFFFFF), new Color(0xE4E7ED), new Color(0x1A1D24), new Color(0x545C6A),
                new Color(0x6B7280), new Color(0x9AA1AD), new Color(0xEDF0F5), new Color(0xF1F3F7),
                new Color(0x2F6FED), new Color(0x2359CC), Color.WHITE,
                new Color(0x15803D), new Color(0xB45309), new Color(0xD92D20),
                new Color(0x0F172A));

        private static final Palette DARK = new Palette(
                new Color(0x23242C), new Color(0x353742), new Color(0xE7E9EF), new Color(0xB3B8C4),
                new Color(0x9BA2B0), new Color(0x767D8B), new Color(0x2E3038), new Color(0x2B2D36),
                new Color(0x5B8DEF), new Color(0x7AA3F5), Color.WHITE,
                new Color(0x45C46E), new Color(0xE0A93C), new Color(0xF07167),
                new Color(0x000000));

        /**
         * 按程序里的主题设置挑配色。
         *
         * <p>「跟随系统」时不在这里起进程探测：主界面启动时已经把实际配色写在
         * {@code ~/.daliwan/theme.cache} 里，读文件即可，弹窗不用晚半秒才出现。
         */
        static Palette current() {
            String preference = ToolSettings.load().theme();
            if (ToolSettings.THEME_DARK.equals(preference)) {
                return DARK;
            }
            if (ToolSettings.THEME_LIGHT.equals(preference)) {
                return LIGHT;
            }
            return ToolSettings.THEME_DARK.equals(SystemTheme.cached()) ? DARK : LIGHT;
        }
    }

    // ------------------------------------------------------------------ 动画

    /** 淡入 + 上浮 8px，结束后回调（用来接上倒计时）。 */
    private static void fadeIn(JDialog dialog, Runnable done) {
        final int distance = 8;
        final int frames = 10;
        Rectangle target = dialog.getBounds();
        int[] tick = {0};
        Timer timer = new Timer(16, null);
        timer.addActionListener(e -> {
            tick[0]++;
            float t = Math.min(1f, tick[0] / (float) frames);
            // ease-out：开头快、结尾缓
            double eased = 1 - Math.pow(1 - t, 3);
            try {
                dialog.setOpacity((float) eased);
            } catch (Throwable ignored) {
                // 不支持透明度时直接跳过动画
                ((Timer) e.getSource()).stop();
                dialog.setLocation(target.x, target.y);
                done.run();
                return;
            }
            dialog.setLocation(target.x, target.y + (int) Math.round(distance * (1 - eased)));
            if (t >= 1f) {
                ((Timer) e.getSource()).stop();
                dialog.setLocation(target.x, target.y);
                done.run();
            }
        });
        timer.start();
    }

    // ------------------------------------------------------------------ 杂项

    private static void placeBottomRight(JDialog dialog) {
        Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        Dimension size = dialog.getSize();
        dialog.setLocation(bounds.x + bounds.width - size.width - 22,
                bounds.y + bounds.height - size.height - 22);
    }

    private static void openFolder(List<PdfCommand.Outcome> outcomes) {
        outcomes.stream()
                .filter(PdfCommand.Outcome::ok)
                .map(PdfCommand.Outcome::target)
                .filter(Objects::nonNull)
                .findFirst()
                .ifPresent(target -> {
                    try {
                        Path folder = target.getParent();
                        if (folder != null) {
                            Desktop.getDesktop().open(folder.toFile());
                        }
                    } catch (Exception ignored) {
                        // 系统没有关联的资源管理器，忽略即可
                    }
                });
    }

    private static Font font(int style, float size) {
        Font base = UIManager.getFont("Label.font");
        if (base == null) {
            base = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        }
        return base.deriveFont(style, size);
    }

    /** 按显示宽度估算截断：中日韩字符算两格，避免长文件名把卡片撑破。 */
    static String truncate(String text, int columns) {
        if (text == null) {
            return "";
        }
        if (visualWidth(text) <= columns) {
            return text;
        }
        StringBuilder builder = new StringBuilder();
        int used = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int width = c > 0x2E80 ? 2 : 1;
            if (used + width > columns - 1) {
                break;
            }
            builder.append(c);
            used += width;
        }
        return builder.append('…').toString();
    }

    private static int visualWidth(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            width += text.charAt(i) > 0x2E80 ? 2 : 1;
        }
        return width;
    }

    private static String oneLine(String text) {
        if (text == null) {
            return "";
        }
        String collapsed = text.replace('\r', ' ').replace('\n', ' ').trim();
        if (collapsed.length() > 200) {
            collapsed = collapsed.substring(0, 200) + "…";
        }
        return collapsed;
    }

    private static Image loadIcon() {
        try (InputStream in = PdfResultDialog.class.getResourceAsStream("/icons/icon-64.png")) {
            if (in != null) {
                return ImageIO.read(in);
            }
        } catch (Exception ignored) {
            // 图标缺失不影响使用
        }
        return null;
    }
}
