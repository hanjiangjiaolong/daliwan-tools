import com.daliwan.tool.core.ImageMetaService;
import com.daliwan.tool.core.ImageMetaStripper;
import com.daliwan.tool.ui.ImgMetaPane;
import com.daliwan.tool.ui.MainView;
import com.daliwan.tool.ui.StripMetaDialog;
import com.daliwan.tool.ui.ThemeManager;
import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.zip.CRC32;

/**
 * 去除元信息功能的冒烟验证（临时工具，不参与打包）。
 *
 * <p>先跑一遍真实文件的剥离并打印报告，再把「图片信息页」和「去除元信息对话框」
 * 在深色 / 浅色两套配色下截图，用来确认样式没有跑偏。
 *
 * <p>用法：{@code java StripCheck [输出目录]}
 */
public class StripCheck {

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "build/verify/strip");
        Files.createDirectories(out);

        Path jpg = out.resolve("photo.jpg");
        Path png = out.resolve("shot.png");
        Files.write(jpg, jpegWithExif());
        Files.write(png, pngWithText());

        System.out.println("== 剥离前后（JPEG） ==");
        ImageMetaStripper.Report report = ImageMetaStripper.strip(
                jpg, out.resolve("photo_clean.jpg"), ImageMetaStripper.Options.DEFAULT);
        System.out.println("格式      : " + report.format() + "  重编码=" + report.reencoded());
        System.out.println("体积      : " + ImageMetaService.formatSize(report.originalBytes())
                + " → " + ImageMetaService.formatSize(report.targetBytes())
                + "（省 " + report.savedBytes() + " 字节）");
        System.out.println("移除      : " + report.removed());
        System.out.println("提示      : " + report.warnings());
        System.out.println("--- 清理后的元数据 ---");
        System.out.println(ImageMetaService.toText(ImageMetaService.read(report.target())));
        System.out.println("--- 原始元数据（前 20 行） ---");
        String before = ImageMetaService.toText(ImageMetaService.read(jpg));
        System.out.println(String.join("\n",
                before.lines().limit(20).toArray(String[]::new)));

        ImageMetaStripper.Report pngReport = ImageMetaStripper.strip(
                png, out.resolve("shot_clean.png"), ImageMetaStripper.Options.DEFAULT);
        System.out.println("PNG 移除  : " + pngReport.removed());

        CountDownLatch latch = new CountDownLatch(1);
        Platform.startup(() -> runUi(out, jpg, png, latch));
        latch.await();
        System.out.println("[done]");
        System.exit(0);
    }

    // ---------------------------------------------------------------- 界面

    private static void runUi(Path out, Path jpg, Path png, CountDownLatch latch) {
        try {
            Stage stage = new Stage();
            MainView root = new MainView();
            Scene scene = new Scene(root, 1092, 700);
            ThemeManager.install(scene, "dark");
            stage.setScene(scene);
            stage.show();
            root.showPage("图片信息");

            // 页面是懒加载的，切过去之后从缓存里把实例取出来喂一张样张
            java.lang.reflect.Field cacheField = MainView.class.getDeclaredField("pageCache");
            cacheField.setAccessible(true);
            Object cache = cacheField.get(root);
            Object page = ((java.util.Map<?, ?>) cache).get("img-meta");
            if (page instanceof ImgMetaPane pane) {
                Method load = ImgMetaPane.class.getDeclaredMethod("load", Path.class);
                load.setAccessible(true);
                load.invoke(pane, jpg);
            } else {
                System.out.println("[warn] 没拿到图片信息页实例：" + page);
            }

            after(1500, () -> {
                shot(scene, out.resolve("img-meta-dark.png"));
                shot(scene, Path.of("docs/screenshot-imgmeta.png"));
                StripMetaDialog.show(stage, List.of(jpg, png), outputs -> {
                });
                after(900, () -> {
                    Stage dialog = findDialog();
                    if (dialog == null) {
                        System.out.println("[warn] 未找到对话框窗口");
                        Platform.exit();
                        latch.countDown();
                        return;
                    }
                    shot(dialog.getScene(), out.resolve("strip-dialog-dark.png"));
                    shot(dialog.getScene(), Path.of("docs/screenshot-strip.png"));
                    dumpHints(dialog.getScene());

                    // 直接点「开始处理」，验证结果行的渲染
                    javafx.scene.Node start = dialog.getScene().lookup(".primary-button");
                    if (start instanceof javafx.scene.control.Button button) {
                        button.fire();
                    }
                    after(1600, () -> {
                        shot(dialog.getScene(), out.resolve("strip-dialog-result.png"));
                        dialog.close();

                        ThemeManager.install(scene, "light");
                        StripMetaDialog.show(stage, List.of(jpg, png), null);
                        after(900, () -> {
                            Stage light = findDialog();
                            if (light != null) {
                                shot(light.getScene(), out.resolve("strip-dialog-light.png"));
                                light.close();
                            }
                            Platform.exit();
                            latch.countDown();
                        });
                    });
                });
            });
        } catch (Exception error) {
            error.printStackTrace();
            Platform.exit();
            latch.countDown();
        }
    }

    /** 打印说明文字的实测尺寸，确认换行是否真的生效。 */
    private static void dumpHints(Scene scene) {
        for (javafx.scene.Node node : scene.getRoot().lookupAll(".hint")) {
            if (node instanceof javafx.scene.control.Label label) {
                String text = label.getText() == null ? "" : label.getText();
                System.out.println("[hint] " + Math.round(label.getWidth()) + "x"
                        + Math.round(label.getHeight()) + " wrap=" + label.isWrapText()
                        + " : " + text.substring(0, Math.min(12, text.length())));
            }
        }
    }

    private static Stage findDialog() {        for (Window window : Window.getWindows()) {
            if (window instanceof Stage stage && "去除图片元信息".equals(stage.getTitle())) {
                return stage;
            }
        }
        return null;
    }

    private static void after(long millis, Runnable action) {
        PauseTransition pause = new PauseTransition(Duration.millis(millis));
        pause.setOnFinished(event -> {
            try {
                action.run();
            } catch (Exception error) {
                error.printStackTrace();
            }
        });
        pause.play();
    }

    private static void shot(Scene scene, Path target) {
        try {
            WritableImage image = scene.snapshot(null);
            int width = (int) image.getWidth();
            int height = (int) image.getHeight();
            PixelReader reader = image.getPixelReader();
            BufferedImage buffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    buffer.setRGB(x, y, reader.getArgb(x, y));
                }
            }
            ImageIO.write(buffer, "png", target.toFile());
            System.out.println("[shot] " + target + "  " + width + "x" + height);
        } catch (Exception error) {
            error.printStackTrace();
        }
    }

    // ---------------------------------------------------------------- 样本

    /** 带相机、机型、软件、拍摄时间、方向五个标签的 JPEG。 */
    private static byte[] jpegWithExif() throws Exception {
        BufferedImage image = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = image.createGraphics();
        g.setColor(new java.awt.Color(80, 130, 200));
        g.fillRect(0, 0, 320, 200);
        g.setColor(java.awt.Color.WHITE);
        g.setFont(new java.awt.Font("Microsoft YaHei", java.awt.Font.BOLD, 28));
        g.drawString("大力丸 · 样张", 70, 110);
        g.dispose();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", buffer);
        byte[] jpeg = buffer.toByteArray();

        byte[] make = bytes("Canon");
        byte[] model = bytes("EOS R5");
        byte[] software = bytes("Adobe Photoshop 25.0");
        byte[] dateTime = bytes("2026:10:21 20:15:30");
        int makeOffset = 74;
        int modelOffset = makeOffset + make.length;
        int softwareOffset = modelOffset + model.length;
        int dateOffset = softwareOffset + software.length;
        int total = dateOffset + dateTime.length;

        byte[] tiff = new byte[total];
        tiff[0] = 'I';
        tiff[1] = 'I';
        tiff[2] = 42;
        tiff[4] = 8;
        tiff[8] = 5;
        entry(tiff, 10, 0x010F, 2, make.length, makeOffset);
        entry(tiff, 22, 0x0110, 2, model.length, modelOffset);
        entry(tiff, 34, 0x0131, 2, software.length, softwareOffset);
        entry(tiff, 46, 0x0132, 2, dateTime.length, dateOffset);
        entry(tiff, 58, 0x0112, 3, 1, 6);       // 方向：顺时针 90
        System.arraycopy(make, 0, tiff, makeOffset, make.length);
        System.arraycopy(model, 0, tiff, modelOffset, model.length);
        System.arraycopy(software, 0, tiff, softwareOffset, software.length);
        System.arraycopy(dateTime, 0, tiff, dateOffset, dateTime.length);

        byte[] payload = new byte[6 + tiff.length];
        System.arraycopy(bytes("Exif\0\0"), 0, payload, 0, 6);
        System.arraycopy(tiff, 0, payload, 6, tiff.length);
        int length = payload.length + 2;

        byte[] result = new byte[2 + 4 + payload.length + (jpeg.length - 2)];
        result[0] = (byte) 0xFF;
        result[1] = (byte) 0xD8;
        result[2] = (byte) 0xFF;
        result[3] = (byte) 0xE1;
        result[4] = (byte) (length >> 8);
        result[5] = (byte) length;
        System.arraycopy(payload, 0, result, 6, payload.length);
        System.arraycopy(jpeg, 2, result, 6 + payload.length, jpeg.length - 2);
        return result;
    }

    private static byte[] pngWithText() throws Exception {
        BufferedImage image = new BufferedImage(160, 100, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = image.createGraphics();
        g.setColor(new java.awt.Color(90, 180, 140));
        g.fillRect(0, 0, 160, 100);
        g.dispose();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ImageIO.write(image, "png", buffer);
        byte[] png = buffer.toByteArray();

        byte[] text = bytes("Software\0Adobe Photoshop 25.0");
        byte[] name = bytes("tEXt");
        ByteArrayOutputStream chunk = new ByteArrayOutputStream();
        writeInt(chunk, text.length);
        chunk.writeBytes(name);
        chunk.writeBytes(text);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(text);
        writeInt(chunk, (int) crc.getValue());
        byte[] block = chunk.toByteArray();

        byte[] result = new byte[png.length + block.length];
        System.arraycopy(png, 0, result, 0, 33);
        System.arraycopy(block, 0, result, 33, block.length);
        System.arraycopy(png, 33, result, 33 + block.length, png.length - 33);
        return result;
    }

    private static void entry(byte[] tiff, int offset, int tag, int type, int count, int value) {
        tiff[offset] = (byte) tag;
        tiff[offset + 1] = (byte) (tag >> 8);
        tiff[offset + 2] = (byte) type;
        tiff[offset + 4] = (byte) count;
        if (type == 3) {
            tiff[offset + 8] = (byte) value;
        } else {
            tiff[offset + 8] = (byte) value;
            tiff[offset + 9] = (byte) (value >> 8);
            tiff[offset + 10] = (byte) (value >> 16);
            tiff[offset + 11] = (byte) (value >> 24);
        }
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }
}
