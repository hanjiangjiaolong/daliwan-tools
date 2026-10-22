import com.daliwan.tool.ui.SqlPane;
import com.daliwan.tool.ui.ThemeManager;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

/**
 * 临时探针：分别用深色 / 浅色主题渲染 MyBatis SQL 页，确认着色在两套配色下都可读。
 * 用法：java SqlThemeShot <输出目录>
 */
public class SqlThemeShot {

    private static final String LOG = """
            [2020-01-10 15:00:00] [DEBUG] getUserList.debug(159) - ==> Preparing: \
            select u.user_id, u.user_name, count(o.id) as order_count \
            from t_user u left join t_order o on o.user_id = u.user_id \
            where u.delete_flag = ? and u.status = ? and u.created_date > ? \
            group by u.user_id, u.user_name order by order_count desc limit ?
            [2020-01-10 15:00:00] [DEBUG] getUserList.debug(159) - ==> Parameters: \
            N(String), 01(String), 2020-01-01 00:00:00(Timestamp), 20(Integer)""";

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "build/verify");
        out.toFile().mkdirs();

        CountDownLatch latch = new CountDownLatch(1);
        Platform.startup(() -> render(out, "dark", latch, () -> render(out, "light", latch, latch::countDown)));
        latch.await();
        Platform.exit();
    }

    private static void render(Path out, String theme, CountDownLatch latch, Runnable next) {
        try {
            SqlPane pane = new SqlPane();
            Scene scene = new Scene(pane, 1000, 660);
            ThemeManager.install(scene, theme);
            Stage stage = new Stage();
            stage.setScene(scene);
            stage.show();

            pane.setLog(LOG);

            PauseTransition pause = new PauseTransition(Duration.millis(900));
            pause.setOnFinished(event -> {
                shot(scene, out.resolve("sql-" + theme + ".png"));
                stage.close();
                next.run();
            });
            pause.play();
        } catch (Exception error) {
            error.printStackTrace();
            latch.countDown();
        }
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
            System.out.println("[shot] " + target);
        } catch (Exception error) {
            error.printStackTrace();
        }
    }
}
