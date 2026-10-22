import com.daliwan.tool.core.win.WindowsUpdateGuard;
import com.daliwan.tool.ui.ThemeManager;
import com.daliwan.tool.ui.UpdateGuardPane;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

/**
 * Windows 更新开关页的渲染自检（临时验证工具，不参与打包）。
 *
 * <p>把页面渲染出来截图，确认风险说明、状态卡片、三个按钮没有溢出或错位；
 * 顺带打印一次真实查询的耗时——这个查询要起一个外部进程，冷启动不便宜。
 *
 * <p>用法：{@code java UpdateShot <输出目录> [dark|light]}。
 */
public final class UpdateShot {

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "build/verify");
        String theme = args.length > 1 ? args[1] : "dark";
        Files.createDirectories(out);

        System.out.println("[guard] supported=" + WindowsUpdateGuard.isSupported());
        long mark = System.currentTimeMillis();
        WindowsUpdateGuard.State state = WindowsUpdateGuard.state();
        System.out.println("[guard] mode=" + state.mode() + " " + state.summary()
                + " (cost " + (System.currentTimeMillis() - mark) + " ms)");
        System.out.println("[guard] services=" + state.services());
        System.out.println("[guard] policy=" + state.noAutoUpdate() + " tasks=" + state.tasks());

        CountDownLatch done = new CountDownLatch(1);
        Platform.startup(() -> {
            try {
                Stage stage = new Stage();
                UpdateGuardPane pane = new UpdateGuardPane();
                Scene scene = new Scene(pane, 960, 620);
                ThemeManager.install(scene, theme);
                stage.setScene(scene);
                stage.show();
                // 不能在 FX 线程上等：会把排在后面的状态渲染任务压住，截出来的永远是「读取中」。
                // 用后台线程等状态查询跑完，再回 FX 线程截图。
                Thread waiter = new Thread(() -> {
                    try {
                        Thread.sleep(8000);
                    } catch (InterruptedException ignored) {
                    }
                    Platform.runLater(() -> {
                        try {
                            WritableImage image = scene.snapshot(null);
                            int width = (int) image.getWidth();
                            int height = (int) image.getHeight();
                            javafx.scene.image.PixelReader reader = image.getPixelReader();
                            BufferedImage buffered =
                                    new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                            for (int y = 0; y < height; y++) {
                                for (int x = 0; x < width; x++) {
                                    buffered.setRGB(x, y, reader.getArgb(x, y));
                                }
                            }
                            File file = out.resolve("update-guard-" + theme + ".png").toFile();
                            ImageIO.write(buffered, "png", file);
                            System.out.println("[shot] " + file.getAbsolutePath());
                        } catch (Exception e) {
                            System.out.println("[shot] failed: " + e.getMessage());
                        } finally {
                            done.countDown();
                        }
                    });
                }, "update-shot-waiter");
                waiter.setDaemon(true);
                waiter.start();
            } catch (Exception e) {
                e.printStackTrace();
                done.countDown();
            }
        });
        done.await();
        Platform.exit();
        System.exit(0);
    }
}
