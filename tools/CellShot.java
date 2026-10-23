import com.daliwan.tool.ui.CellDetailDialog;
import com.daliwan.tool.ui.JsonPane;
import com.daliwan.tool.ui.ThemeManager;
import com.daliwan.tool.util.PngEncoder;
import com.daliwan.tool.util.SystemTheme;
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
 * 表格单元格详情的截图自检（临时工具，不参与打包）。
 *
 * <p>用法：{@code CellShot [dark|light]}，产物写在 build/verify/ 下：
 * 一张表格视图、一张双击单元格弹出的详情窗口。
 */
public class CellShot {

    private static final String JSON = """
            {"code":0,"data":{"list":[
            {"id":1001,"name":"张三","tags":["admin","dev"],"profile":{"team":"平台组","city":"上海","roles":["owner","reviewer"],"ext":{"level":7,"note":"长期负责发布流程"}}},
            {"id":1002,"name":"李四","tags":[],"profile":{"team":"数据组","city":"北京","roles":[],"ext":{"level":3,"note":"试用期，暂不开通生产权限"}}},
            {"id":1003,"name":"王五","tags":["ops"],"profile":{"team":"运维组","city":"深圳","roles":["oncall"],"ext":{"level":5,"note":"值班手机号已登记"}}}]}}""";

    /** 第 2 行 profile 这一格的原文，与表格里的紧凑 JSON 一致。 */
    private static final String CELL = "{\"team\":\"数据组\",\"city\":\"北京\",\"roles\":[],"
            + "\"ext\":{\"level\":3,\"note\":\"试用期，暂不开通生产权限\"}}";

    /** 超长单行且语法不完整：验证「着色暂停」的提示分支。 */
    private static final String CELL_LONG =
            "{\"note\":\"" + "很长的一行。".repeat(4000) + "\"";

    private static final Object LOCK = new Object();

    public static void main(String[] args) {
        String theme = args.length > 0 ? args[0] : "dark";
        String mode = args.length > 1 ? args[1] : "normal";
        String cell = "long".equals(mode) ? CELL_LONG : CELL;
        SystemTheme.remember(theme);
        // 不走 Application.launch：fat jar / classpath 模式下它会检查模块路径并报
        // 「缺少 JavaFX 运行时组件」。Platform.startup 直接起工具包即可。
        Platform.startup(() -> {
            JsonPane pane = new JsonPane();
            pane.setContent(JSON);

            Stage stage = new Stage();
            Scene scene = new Scene(pane, 1180, 760);
            ThemeManager.install(scene, theme);
            stage.setScene(scene);
            stage.setTitle("大力丸");
            stage.show();
            pane.tableNow();

            // 后台等：表格是后台线程解析的，界面也要跑几帧；在 FX 线程上等会压住渲染
            Thread waiter = new Thread(() -> {
                for (int i = 0; i < 60 && pane.tableRowCount() == 0; i++) {
                    sleep(100);
                }
                sleep(900);
                Platform.runLater(() -> {
                    save(scene.getRoot(), "build/verify/json-table-" + theme + ".png");
                    CellDetailDialog.show(stage, "profile", "2", cell);
                });
                sleep(1400);
                Platform.runLater(() -> {
                    for (Window window : Window.getWindows()) {
                        if (window instanceof Stage dialog && "单元格内容".equals(dialog.getTitle())) {
                            save(dialog.getScene().getRoot(),
                                    "build/verify/cell-detail-" + theme + "-" + mode + ".png");
                        }
                    }
                    synchronized (LOCK) {
                        LOCK.notifyAll();
                    }
                });
            }, "cell-shot");
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
