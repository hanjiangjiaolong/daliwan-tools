import com.daliwan.tool.core.ToolSettings;
import com.daliwan.tool.ui.MainView;
import com.daliwan.tool.ui.ThemeManager;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * 临时验证工具：把几项页面设为隐藏，截「更多」收起与展开两张图，最后还原配置。
 *
 * <p>用法：{@code java MoreShot.java build/verify}。
 *
 * <p>注意：它会临时改写 {@code ~/.daliwan/settings.properties}（先备份到
 * {@code build/verify/settings-backup.properties}），结束时还原。若进程被强杀，
 * 配置可能停留在「多隐藏了几项」的状态，把备份复制回去即可。
 */
public final class MoreShot {

    /** 截图用的隐藏项，刚好跨三个分组。 */
    private static final List<String> HIDE = List.of("ocr", "rsa", "img2pdf");

    private static final Path CONFIG = ToolSettings.DEFAULT_FILE;
    private static final Path BACKUP = Path.of("build/verify/settings-backup.properties");

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "build/verify");
        Files.createDirectories(out);

        boolean hadConfig = Files.exists(CONFIG);
        if (hadConfig) {
            Files.copy(CONFIG, BACKUP, StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            ToolSettings settings = ToolSettings.load();
            for (String id : HIDE) {
                settings.setVisible(id, false);
            }
            settings.save();
            shots(out);
        } finally {
            if (hadConfig) {
                Files.copy(BACKUP, CONFIG, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.deleteIfExists(CONFIG);
            }
            System.out.println("[restore] 配置已还原，hidden=" + ToolSettings.load().hiddenIds());
        }
    }

    private static void shots(Path out) throws Exception {
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        Platform.startup(() -> {
            Stage stage = new Stage();
            MainView root = new MainView();
            Scene scene = new Scene(root, 1180, 760);
            ThemeManager.install(scene, "light");
            stage.setScene(scene);
            stage.show();
            root.selectFirst();

            PauseTransition first = new PauseTransition(Duration.millis(900));
            first.setOnFinished(event -> {
                snapshot(scene, out.resolve("main-view-more-collapsed.png"));
                try {
                    Field field = MainView.class.getDeclaredField("moreButton");
                    field.setAccessible(true);
                    javafx.scene.control.Button button =
                            (javafx.scene.control.Button) field.get(root);
                    System.out.println("[more] visible=" + button.isVisible()
                            + " text=" + button.getText());
                    button.fire();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                PauseTransition second = new PauseTransition(Duration.millis(600));
                second.setOnFinished(inner -> {
                    snapshot(scene, out.resolve("main-view-more-expanded.png"));
                    clickHidden(root, "img-meta");

                    PauseTransition third = new PauseTransition(Duration.millis(600));
                    third.setOnFinished(last -> {
                        snapshot(scene, out.resolve("main-view-more-clicked.png"));
                        System.out.println("[page] currentId=" + currentId(root));
                        done.countDown();
                        Platform.exit();
                    });
                    third.play();
                });
                second.play();
            });
            first.play();
        });
        done.await();
    }

    /** 在导航里按 id 找到按钮并触发一次点击（模拟用户点「已隐藏」里的项）。 */
    private static void clickHidden(MainView root, String id) {
        try {
            Field field = MainView.class.getDeclaredField("navBox");
            field.setAccessible(true);
            javafx.scene.layout.VBox box = (javafx.scene.layout.VBox) field.get(root);
            for (javafx.scene.Node node : box.getChildren()) {
                if (node instanceof javafx.scene.control.ToggleButton button) {
                    Object data = button.getUserData();
                    String entryId = String.valueOf(data);
                    if (entryId.contains("id=" + id + ",") || entryId.contains("id=" + id + " ")) {
                        System.out.println("[click] " + entryId);
                        button.fire();
                        return;
                    }
                }
            }
            System.out.println("[click] 没找到 " + id);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** 读当前页面 id，确认点击真的切了页。 */
    private static String currentId(MainView root) {
        try {
            Field field = MainView.class.getDeclaredField("currentId");
            field.setAccessible(true);
            return String.valueOf(field.get(root));
        } catch (Exception e) {
            return "<unknown>";
        }
    }

    private static void snapshot(Scene scene, Path target) {
        try {
            WritableImage image = scene.snapshot(null);
            BufferedImage buffered = new BufferedImage((int) image.getWidth(),
                    (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < buffered.getHeight(); y++) {
                for (int x = 0; x < buffered.getWidth(); x++) {
                    buffered.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                }
            }
            ImageIO.write(buffered, "png", new File(target.toString()));
            System.out.println("[shot] " + target.getFileName());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
