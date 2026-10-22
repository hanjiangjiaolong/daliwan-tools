import com.daliwan.tool.ui.MainView;
import com.daliwan.tool.ui.SettingsPane;
import com.daliwan.tool.ui.ThemeManager;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * 渲染设置页并打印系统集成开关的实际取值，用于核对 Win11 经典菜单项的接线。
 *
 * <p>不继承 {@link javafx.application.Application}：打包后的 fat jar 里 JavaFX 在
 * classpath 上，继承 Application 会被 JDK 判成「缺少 JavaFX 运行时组件」，
 * 用 {@link Platform#startup} 才能直接从 classpath 跑起来。
 *
 * <p>用法：{@code java SettingsShot.java <输出目录>}。
 */
public final class SettingsShot {

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "build/verify");
        String theme = args.length > 1 ? args[1] : "dark";
        String pos = args.length > 2 ? args[2] : "bottom";
        Files.createDirectories(out);
        CountDownLatch closed = new CountDownLatch(1);

        Platform.startup(() -> {
            try {
                Stage stage = new Stage();
                MainView root = new MainView();
                Scene scene = new Scene(root, 1180, 760);
                ThemeManager.install(scene, theme);
                stage.setScene(scene);
                stage.show();

                Method options = MainView.class.getDeclaredMethod("settingOptions");
                options.setAccessible(true);
                @SuppressWarnings("unchecked")
                List<SettingsPane.Option> list = (List<SettingsPane.Option>) options.invoke(root);
                for (SettingsPane.Option option : list) {
                    System.out.println("[option] id=" + option.id()
                            + " title=" + option.title()
                            + " selected=" + option.selected()
                            + " available=" + option.available()
                            + " note=" + option.note());
                }

                Method open = MainView.class.getDeclaredMethod("openSettings");
                open.setAccessible(true);
                open.invoke(root);

                // 系统集成在页面底部，默认滚到底；传 top 则保持顶部（看说明文字用）
                if (!"top".equalsIgnoreCase(pos)) {
                    scrollToBottom(root);
                }

                new Thread(() -> {
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException ignored) {
                        // 直接截图
                    }
                    Platform.runLater(() -> {
                        try {
                            WritableImage image = scene.snapshot(null);
                            BufferedImage buffered = new BufferedImage((int) image.getWidth(),
                                    (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
                            for (int y = 0; y < buffered.getHeight(); y++) {
                                for (int x = 0; x < buffered.getWidth(); x++) {
                                    buffered.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                                }
                            }
                            ImageIO.write(buffered, "png", new File(out.toFile(), "settings-" + theme + ".png"));
                            System.out.println("[shot] settings-" + theme + ".png");
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

    /** 找到设置页里的滚动容器并滚到底部。 */
    private static void scrollToBottom(javafx.scene.Node node) {
        if (node instanceof javafx.scene.control.ScrollPane scroll) {
            scroll.setVvalue(1.0);
            return;
        }
        if (node instanceof javafx.scene.Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                scrollToBottom(child);
            }
        }
    }
}
