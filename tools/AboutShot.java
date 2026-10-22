import com.daliwan.tool.ui.MainView;
import com.daliwan.tool.ui.ThemeManager;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.stage.Window;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

/**
 * 渲染「关于」弹窗并截图，顺带截一张主窗口确认「关于」按钮的位置。
 *
 * <p>不继承 {@link javafx.application.Application}：fat jar 在 classpath 上时会被 JDK 判成
 * 「缺少 JavaFX 运行时组件」，必须走 {@link Platform#startup}。
 *
 * <p>用法：{@code java AboutShot <输出目录> <light|dark>}。
 */
public final class AboutShot {

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "build/verify");
        String theme = args.length > 1 ? args[1] : "light";
        Files.createDirectories(out);
        CountDownLatch closed = new CountDownLatch(1);

        Platform.startup(() -> {
            try {
                Stage stage = new Stage();
                MainView root = new MainView();
                Scene main = new Scene(root, 1180, 760);
                ThemeManager.install(main, theme);
                stage.setScene(main);
                stage.show();

                Method open = MainView.class.getDeclaredMethod("openAbout");
                open.setAccessible(true);
                open.invoke(root);

                new Thread(() -> {
                    sleep(1600);
                    Platform.runLater(() -> {
                        try {
                            save(main, out.resolve("main-header-" + theme + ".png"));
                            for (Window window : Window.getWindows()) {
                                if (window == stage || window.getScene() == null) {
                                    continue;
                                }
                                save(window.getScene(), out.resolve("about-" + theme + ".png"));
                                break;
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        closed.countDown();
                        Platform.exit();
                    });
                }, "shot").start();
            } catch (Exception e) {
                e.printStackTrace();
                closed.countDown();
                Platform.exit();
            }
        });
        closed.await();
    }

    private static void save(Scene scene, Path path) throws Exception {
        WritableImage image = scene.snapshot(null);
        BufferedImage buffered = new BufferedImage((int) image.getWidth(), (int) image.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < buffered.getHeight(); y++) {
            for (int x = 0; x < buffered.getWidth(); x++) {
                buffered.setRGB(x, y, image.getPixelReader().getArgb(x, y));
            }
        }
        ImageIO.write(buffered, "png", path.toFile());
        System.out.println("[shot] " + path);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            // 直接去截图
        }
    }
}
