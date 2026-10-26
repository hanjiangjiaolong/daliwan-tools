import com.daliwan.tool.ui.JsonPane;
import com.daliwan.tool.ui.ThemeManager;
import com.daliwan.tool.util.PngEncoder;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;

import java.io.File;

/**
 * 去转义的截图自检（临时工具）：粘贴带反斜杠引号的接口文本，点「去转义」应变成真 JSON。
 *
 * <p>用法：{@code JsonUnescapeShot [dark|light]}，产物写在 build/verify/。
 */
public class JsonUnescapeShot {

    /** 接口日志里最常见的样子：整个数组是字符串字段，引号全带着反斜杠。 */
    private static final String PAYLOAD = "[{\\\"id\\\": \\\"654222728013787136\\\", "
            + "\\\"typeId\\\": \\\"STD_ATTR_D_IPS\\\", \\\"typeName\\\": \\\"标准规格属性D\\\", "
            + "\\\"attributeName\\\": \\\"材质\\\"}, {\\\"id\\\": \\\"654360668362768384\\\", "
            + "\\\"typeId\\\": \\\"TYDKIPS\\\", \\\"typeName\\\": \\\"标准规格属性T\\\", "
            + "\\\"attributeName\\\": \\\"规格\\\"}]";

    private static final Object LOCK = new Object();

    public static void main(String[] args) {
        String theme = args.length > 0 ? args[0] : "light";
        Platform.startup(() -> {
            JsonPane pane = new JsonPane();
            pane.setContent(PAYLOAD);

            Stage stage = new Stage();
            Scene scene = new Scene(pane, 900, 520);
            ThemeManager.install(scene, theme);
            stage.setScene(scene);
            stage.show();

            Thread waiter = new Thread(() -> {
                sleep(800);
                Platform.runLater(() -> {
                    System.out.println("[去转义前] " + pane.status());
                    pane.unescapeNow();
                    sleep(400);
                    System.out.println("[去转义后] " + pane.status());
                    pane.formatNow();
                    sleep(600);
                    System.out.println("[格式化后] " + pane.status());
                    System.out.println("[内容] " + pane.content().replace("\n", " "));
                    save(scene.getRoot(), "build/verify/json-unescape-" + theme + ".png");
                    synchronized (LOCK) {
                        LOCK.notifyAll();
                    }
                });
            }, "unescape-shot");
            waiter.setDaemon(true);
            waiter.start();
        });

        synchronized (LOCK) {
            try {
                LOCK.wait(30000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        System.exit(0);
    }

    private static void save(Node node, String out) {
        WritableImage image = node.snapshot(new SnapshotParameters(), null);
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        try {
            File file = new File(out);
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            PixelReader reader = image.getPixelReader();
            int[] argb = new int[width * height];
            reader.getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), argb, 0, width);
            PngEncoder.write(file.toPath(), argb, width, height);
            System.out.println("[shot] " + out + " " + width + "x" + height);
        } catch (Exception e) {
            System.out.println("[shot] 失败 " + out + "：" + e);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
