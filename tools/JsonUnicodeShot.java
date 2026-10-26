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
 * 二次转义还原的截图自检（临时工具）：粘贴带 \\uXXXX 的接口数据，格式化后应显示中文。
 *
 * <p>用法：{@code JsonUnicodeShot [dark|light]}，产物写在 build/verify/。
 */
public class JsonUnicodeShot {

    private static final String PAYLOAD = "{\"list\":["
            + "{\"vid\":814,\"store_name\":\"\\\\u4f70\\\\u8d62\\\\u63a7\\\\u80a1\\\\u96c6\\\\u56e2\","
            + "\"class_1_name\":\"\\\\u5de5\\\\u5668\\\\u5177\\\\/\\\\u52a8\\\\u529b\","
            + "\"class_3_name\":\"\\\\u76f4\\\\u67c4\\\\u9ebb\\\\u82b1\\\\u94bb\"},"
            + "{\"vid\":814,\"store_name\":\"\\\\u4f70\\\\u8d62\\\\u63a7\\\\u80a1\\\\u96c6\\\\u56e2\","
            + "\"class_3_name\":\"\\\\u76f4\\\\u67c4\\\\u957f\\\\u9ebb\\\\u82b1\\\\u94bb\"}],"
            + "\"pagination\":{\"currentPage\":1,\"totalPage\":17,\"total\":1649},\"success\":true}";

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
                    pane.formatNow();
                    sleep(600);
                    save(scene.getRoot(), "build/verify/json-unicode-" + theme + ".png");
                    pane.tableNow();
                    // 表格是后台线程解析的，等行数出来再截
                    for (int i = 0; i < 60 && pane.tableRowCount() == 0; i++) {
                        sleep(100);
                    }
                    sleep(800);
                    save(scene.getRoot(), "build/verify/json-unicode-table-" + theme + ".png");
                    try {
                        var auto = com.daliwan.tool.core.JsonTable.buildAuto(pane.content());
                        System.out.println("[表格CSV]\n" + com.daliwan.tool.core.JsonTable.toCsv(auto.table()));
                    } catch (Exception e) {
                        System.out.println("[表格CSV] 失败：" + e);
                    }
                    System.out.println("[内容] " + pane.content().replace("\n", " "));
                    synchronized (LOCK) {
                        LOCK.notifyAll();
                    }
                });
            }, "unicode-shot");
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
