import com.daliwan.tool.ui.MainView;
import com.daliwan.tool.ui.ThemeManager;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * 截图 + 图标挂载自检（临时验证工具，不参与打包）。
 * 用法：java UiShot <输出目录>
 */
public class UiShot {

    public static void main(String[] args) throws Exception {
        String outDir = args.length > 0 ? args[0] : "build/verify";
        new File(outDir).mkdirs();

        // 1) 资源自检：窗口图标与 exe 图标源文件都必须存在
        int found = 0;
        for (int size : new int[]{16, 24, 32, 48, 64, 128, 256}) {
            try (InputStream in = UiShot.class.getResourceAsStream("/icons/icon-" + size + ".png")) {
                if (in != null) {
                    found++;
                }
            }
        }
        System.out.println("[check] icons/png loaded = " + found + "/7");
        // icon.ico 供 jpackage 用，在 src/windows/assets/ 下，不进 classpath
        java.nio.file.Path ico = java.nio.file.Path.of("src/windows/assets/icon.ico");
        System.out.println("[check] windows assets icon.ico = " + (java.nio.file.Files.exists(ico) ? "OK" : "MISSING"));

        CountDownLatch latch = new CountDownLatch(1);
        Platform.startup(() -> {
            try {
                Stage stage = new Stage();
                List<Image> icons = new java.util.ArrayList<>();
                for (int size : new int[]{16, 24, 32, 48, 64, 128, 256}) {
                    try (InputStream in = UiShot.class.getResourceAsStream("/icons/icon-" + size + ".png")) {
                        if (in != null) {
                            icons.add(new Image(in));
                        }
                    }
                }
                stage.getIcons().addAll(icons);
                System.out.println("[check] stage icons = " + stage.getIcons().size());

                MainView root = new MainView();
                Scene scene = new Scene(root, 1200, 800);
                ThemeManager.install(scene, root.theme());
                stage.setScene(scene);
                stage.show();
                root.selectFirst();

                // 等一帧让布局完成
                javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.millis(600));
                pause.setOnFinished(e -> {
                    try {
                        WritableImage snap = scene.snapshot(null);
                        PixelReader reader = snap.getPixelReader();
                        int w = (int) snap.getWidth();
                        int h = (int) snap.getHeight();
                        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                        for (int y = 0; y < h; y++) {
                            for (int x = 0; x < w; x++) {
                                out.setRGB(x, y, reader.getArgb(x, y));
                            }
                        }
                        ImageIO.write(out, "png", new File(outDir + "/main-view.png"));
                        System.out.println("[shot] " + outDir + "/main-view.png");
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    } finally {
                        Platform.exit();
                        latch.countDown();
                    }
                });
                pause.play();
            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.exit();
                latch.countDown();
            }
        });
        latch.await();
        System.out.println("[done]");
        System.exit(0);
    }
}
