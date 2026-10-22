import com.daliwan.tool.ui.JsonPane;
import com.daliwan.tool.ui.MainView;
import com.daliwan.tool.ui.ThemeManager;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeView;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.concurrent.CountDownLatch;

/**
 * 真窗口验证（临时工具）：在树视图里对某个节点弹右键菜单并截屏。
 *
 * <p>菜单是弹出窗口，不属于 Scene，{@code scene.snapshot()} 截不到，只能用
 * {@link Robot} 抓屏幕。用法：{@code java JsonMenuShot build/verify/json-menu.png}
 */
public class JsonMenuShot {

    private static final String SAMPLE = """
            {"code":0,"message":"ok","data":{"total":2,"page":1,"list":[\
            {"id":1001,"name":"张三","tags":["admin","dev"],"active":true,"score":98.5,"remark":null},\
            {"id":1002,"name":"李四","tags":[],"active":false,"score":76,"remark":"试用期"}]}}""";

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "build/verify/json-menu.png";
        File target = new File(out);
        if (target.getParentFile() != null) {
            target.getParentFile().mkdirs();
        }
        CountDownLatch done = new CountDownLatch(1);

        Platform.startup(() -> {
            try {
                Stage stage = new Stage();
                MainView root = new MainView();
                // 屏幕不够大时窗口会被裁到屏幕外，Robot 的坐标就点偏了，先按可视区域收一下
                javafx.geometry.Rectangle2D screen = javafx.stage.Screen.getPrimary().getVisualBounds();
                Scene scene = new Scene(root, Math.min(1180, screen.getWidth() - 40),
                        Math.min(760, screen.getHeight() - 40));
                ThemeManager.install(scene, root.theme());
                stage.setScene(scene);
                stage.setX(screen.getMinX() + 20);
                stage.setY(screen.getMinY() + 20);
                stage.setAlwaysOnTop(true);
                stage.show();
                stage.toFront();
                stage.requestFocus();
                root.showPage("JSON 工具");

                JsonPane pane = find(scene.getRoot(), JsonPane.class);
                if (pane != null) {
                    pane.setContent(SAMPLE);
                    pane.buildNow();
                }

                PauseTransition first = new PauseTransition(Duration.millis(1400));
                first.setOnFinished(event -> {
                    try {
                        TreeView<?> tree = find(scene.getRoot(), TreeView.class);
                        TreeCell<?> row = null;
                        for (Node node : tree.lookupAll(".tree-cell")) {
                            if (node instanceof TreeCell<?> cell && cell.getItem() != null
                                    && String.valueOf(cell.getItem()).startsWith("list")) {
                                row = cell;
                                break;
                            }
                        }
                        if (row == null) {
                            System.out.println("[menu] 没找到 list 行");
                            done.countDown();
                            Platform.exit();
                            return;
                        }
                        Bounds bounds = row.localToScreen(row.getBoundsInLocal());
                        Robot robot = new Robot();
                        robot.mouseMove((int) bounds.getCenterX(), (int) bounds.getCenterY());
                        robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
                        robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
                        System.out.println("[menu] 右键 list @" + (int) bounds.getCenterX()
                                + "," + (int) bounds.getCenterY());

                        PauseTransition second = new PauseTransition(Duration.millis(700));
                        second.setOnFinished(inner -> {
                            try {
                                Rectangle area = new Rectangle((int) stage.getX(), (int) stage.getY(),
                                        (int) stage.getWidth(), (int) stage.getHeight());
                                ImageIO.write(new Robot().createScreenCapture(area), "png", target);
                                System.out.println("[shot] " + out);
                                Robot esc = new Robot();
                                esc.keyPress(KeyEvent.VK_ESCAPE);
                                esc.keyRelease(KeyEvent.VK_ESCAPE);
                            } catch (Exception error) {
                                error.printStackTrace();
                            }
                            done.countDown();
                            Platform.exit();
                        });
                        second.play();
                    } catch (Exception error) {
                        error.printStackTrace();
                        done.countDown();
                        Platform.exit();
                    }
                });
                first.play();
            } catch (Exception error) {
                error.printStackTrace();
                done.countDown();
            }
        });

        done.await();
        System.exit(0);
    }

    private static <T extends Node> T find(Node node, Class<T> type) {
        if (type.isInstance(node)) {
            return type.cast(node);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                T found = find(child, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
