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
 * 行号栏的截图自检（临时工具，不参与打包）：确认行号字形正常、个位与十位右对齐。
 *
 * <p>用法：{@code GutterShot [dark|light]}，产物写在 build/verify/。
 */
public class GutterShot {

    private static final String JSON = """
            {"code":0,"msg":"ok","data":{"list":[
            {"id":1001,"name":"张三","team":"平台组","level":7},
            {"id":1002,"name":"李四","team":"数据组","level":3},
            {"id":1003,"name":"王五","team":"运维组","level":5},
            {"id":1004,"name":"赵六","team":"客户端","level":6},
            {"id":1005,"name":"孙七","team":"测试组","level":4},
            {"id":1006,"name":"周八","team":"平台组","level":8},
            {"id":1007,"name":"吴九","team":"数据组","level":2},
            {"id":1008,"name":"郑十","team":"运维组","level":9}]}}""";

    private static final Object LOCK = new Object();

    public static void main(String[] args) {
        String theme = args.length > 0 ? args[0] : "light";
        Platform.startup(() -> {
            JsonPane pane = new JsonPane();
            pane.setContent(JSON);

            Stage stage = new Stage();
            Scene scene = new Scene(pane, 900, 460);
            ThemeManager.install(scene, theme);
            stage.setScene(scene);
            stage.show();

            Thread waiter = new Thread(() -> {
                sleep(1500);
                Platform.runLater(() -> {
                    save(scene.getRoot(), "build/verify/gutter-" + theme + ".png");
                    synchronized (LOCK) {
                        LOCK.notifyAll();
                    }
                });
            }, "gutter-shot");
            waiter.setDaemon(true);
            waiter.start();
        });

        synchronized (LOCK) {
            try {
                LOCK.wait(20000);
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
