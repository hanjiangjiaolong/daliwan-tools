import com.daliwan.tool.ui.JsonPane;
import com.daliwan.tool.ui.MainView;
import com.daliwan.tool.ui.ThemeManager;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Node;
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

/** 临时验证：表格视图的数据源切换、xlsx 导出、删除节点（无确认框）。 */
public final class JsonTableCheck {

    private static final String SAMPLE = """
            {"code":0,"message":"ok","data":{"total":3,"page":1,
            "list":[{"id":1001,"name":"张三","tags":["admin","dev"],"active":true,"score":98.5},
            {"id":1002,"name":"李四","active":false,"score":76,"remark":"试用期"},
            {"id":1003,"name":"王五","active":true,"owner":{"team":"平台组"}}],
            "dict":[{"code":"A","name":"启用"},{"code":"B","name":"停用"}]},
            "tags":["alpha","beta","gamma"]}""";

    public static void main(String[] args) throws Exception {
        Path out = Path.of("build/verify");
        Files.createDirectories(out);

        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        Platform.startup(() -> {
            Stage stage = new Stage();
            MainView root = new MainView();
            Scene scene = new Scene(root, 1180, 760);
            ThemeManager.install(scene, "light");
            stage.setScene(scene);
            stage.show();
            root.selectFirst();

            PauseTransition first = new PauseTransition(Duration.millis(700));
            first.setOnFinished(event -> {
                clickNav(root, "json");
                PauseTransition second = new PauseTransition(Duration.millis(600));
                second.setOnFinished(inner -> {
                    JsonPane pane = find(root, JsonPane.class);
                    if (pane == null) {
                        System.out.println("[fail] no JsonPane");
                        done.countDown();
                        Platform.exit();
                        return;
                    }
                    pane.setContent(SAMPLE);
                    pane.tableNow();

                    PauseTransition third = new PauseTransition(Duration.millis(1200));
                    third.setOnFinished(last -> {
                        System.out.println("[table] source=" + pane.tableSource()
                                + " rows=" + pane.tableRowCount()
                                + " cols=" + pane.tableColumns()
                                + " candidates=" + pane.candidateCount());
                        reportSourceBar(pane);
                        shot(scene, out.resolve("json-table-source.png"));

                        // 切到第二个候选（data.dict）
                        pane.selectCandidateNow(1);
                        PauseTransition fourth = new PauseTransition(Duration.millis(1000));
                        fourth.setOnFinished(switched -> {
                            System.out.println("[switch] source=" + pane.tableSource()
                                    + " rows=" + pane.tableRowCount()
                                    + " cols=" + pane.tableColumns());
                            try {
                                Path xlsx = out.resolve("json-table.xlsx");
                                pane.exportXlsxNow(xlsx);
                                System.out.println("[xlsx] size=" + Files.size(xlsx)
                                        + " bytes -> " + xlsx.getFileName());
                            } catch (Exception e) {
                                System.out.println("[xlsx] FAILED " + e);
                            }
                            shot(scene, out.resolve("json-table-switched.png"));

                            // 删除节点：不再弹确认框
                            boolean deleted = pane.deletePathNow(java.util.List.of(2, 0));
                            System.out.println("[delete] ok=" + deleted);
                            done.countDown();
                            Platform.exit();
                        });
                        fourth.play();
                    });
                    third.play();
                });
                second.play();
            });
            first.play();
        });
        done.await();
    }

    /** 量数据源下拉：是否真的显示出来、能选几项、尺寸多少。 */
    private static void reportSourceBar(JsonPane pane) {
        try {
            Field barField = JsonPane.class.getDeclaredField("sourceBar");
            barField.setAccessible(true);
            javafx.scene.layout.HBox bar = (javafx.scene.layout.HBox) barField.get(pane);
            Field boxField = JsonPane.class.getDeclaredField("sourceBox");
            boxField.setAccessible(true);
            javafx.scene.control.ComboBox<?> box =
                    (javafx.scene.control.ComboBox<?>) boxField.get(pane);
            System.out.printf("[sourcebar] visible=%s managed=%s h=%.1f | box items=%d value=%s"
                            + " w=%.1f h=%.1f shown=%s%n",
                    bar.isVisible(), bar.isManaged(), bar.getHeight(),
                    box.getItems().size(), box.getValue(),
                    box.getWidth(), box.getHeight(), box.isVisible());
            System.out.println("[sourcebar] options=" + box.getItems());
        } catch (Exception e) {
            System.out.println("[sourcebar] FAILED " + e);
        }
    }

    private static void clickNav(MainView root, String id) {        try {
            Field field = MainView.class.getDeclaredField("navBox");
            field.setAccessible(true);
            javafx.scene.layout.VBox box = (javafx.scene.layout.VBox) field.get(root);
            for (Node node : box.getChildren()) {
                if (node instanceof javafx.scene.control.ToggleButton button
                        && String.valueOf(button.getUserData()).contains("id=" + id + ",")) {
                    button.fire();
                    return;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static <T> T find(Node node, Class<T> type) {
        if (type.isInstance(node)) {
            return type.cast(node);
        }
        if (node instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                T found = find(child, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void shot(Scene scene, Path target) {
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
