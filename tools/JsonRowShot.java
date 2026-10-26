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
import javafx.stage.Window;

import java.io.File;

/**
 * 表格行号弹窗的截图自检（临时工具）：点行号应弹出整行 JSON，内容是原始数据而非单元格文本。
 *
 * <p>用法：{@code JsonRowShot [dark|light]}，产物写在 build/verify/。
 */
public class JsonRowShot {

    /** 接口常见的包裹结构：数组藏在 data.list 里，第 3 行还带一个嵌套对象。 */
    private static final String PAYLOAD = "{\"code\":0,\"data\":{\"list\":["
            + "{\"id\":1001,\"name\":\"张三\",\"tags\":[\"admin\",\"dev\"],\"active\":true,\"score\":98.5},"
            + "{\"id\":1002,\"name\":\"李四\",\"tags\":[],\"active\":false,\"score\":76,\"remark\":\"试用期\"},"
            + "{\"id\":1003,\"name\":\"王五\",\"active\":1,\"owner\":{\"team\":\"平台组\",\"size\":12}}]}}";

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
                sleep(700);
                Platform.runLater(() -> {
                    pane.tableNow();
                    sleep(400);
                    // 探针环境里 JavaFX 后台 Task 不执行（toolkit 启动就报 event thread 错），
                    // 这里直接把解析结果喂给界面，其余走的都是真实代码路径
                    try {
                        var auto = com.daliwan.tool.core.JsonTable.buildAuto(pane.content());
                        var install = JsonPane.class.getDeclaredMethod(
                                "installTable", com.daliwan.tool.core.JsonTable.AutoResult.class);
                        install.setAccessible(true);
                        install.invoke(pane, auto);
                        System.out.println("[core] 候选 " + auto.candidates().size()
                                + " 路径 " + auto.sourcePath()
                                + " 第3行 " + com.daliwan.tool.core.JsonDocument.compact(
                                com.daliwan.tool.core.JsonTable.rowNode(
                                        auto.document(), auto.selected(), 2)));
                    } catch (Exception e) {
                        System.out.println("[core] 失败 " + e);
                    }
                    sleep(700);
                    save(scene.getRoot(), "build/verify/json-row-table-" + theme + ".png");
                    System.out.println("[状态] 行数=" + pane.tableRowCount() + " " + pane.status());
                    try {
                        pane.openRowJsonNow("3");
                    } catch (Throwable error) {
                        System.out.println("[openRow] 异常 " + error);
                    }
                    sleep(900);
                    System.out.println("[状态-点击后] " + pane.status());
                    for (Window window : Window.getWindows()) {
                        System.out.println("[window] " + (window instanceof Stage s ? s.getTitle() : window));
                        if (window instanceof Stage popup && "整行 JSON".equals(popup.getTitle())
                                && popup.getScene() != null) {
                            save(popup.getScene().getRoot(), "build/verify/json-row-detail-" + theme + ".png");
                            System.out.println("[弹窗标题] " + popup.getTitle()
                                    + " · 宽 " + popup.getWidth() + " 高 " + popup.getHeight());
                        }
                    }
                    synchronized (LOCK) {
                        LOCK.notifyAll();
                    }
                });
            }, "row-shot");
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
