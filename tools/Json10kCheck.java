import com.daliwan.tool.ui.JsonPane;
import com.daliwan.tool.ui.MainView;
import com.daliwan.tool.ui.ThemeManager;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ScrollBar;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

/** 临时复现：1 万行 JSON 在文本/表格视图下右侧是否还有滚动条；导出按钮与邻按钮是否等高。 */
public final class Json10kCheck {

    private static Scene scene;

    public static void main(String[] args) throws Exception {
        Path out = Path.of("build/verify");
        Files.createDirectories(out);
        String json = sample(1000);
        System.out.println("[json] length=" + json.length() + " lines=" + json.lines().count());

        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        Platform.startup(() -> {
            Stage stage = new Stage();
            MainView root = new MainView();
            scene = new Scene(root, 1180, 760);
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
                    pane.setContent(json);
                    PauseTransition third = new PauseTransition(Duration.millis(1800));
                    third.setOnFinished(last -> {
                        System.out.println("== 文本视图 ==");
                        reportArea(pane, "editor");
                        shot(out.resolve("json10k-text.png"));
                        pane.tableNow();
                        PauseTransition fourth = new PauseTransition(Duration.millis(2500));
                        fourth.setOnFinished(last2 -> {
                            System.out.println("== 表格视图 ==");
                            System.out.println("[rows] " + pane.tableRowCount()
                                    + " cols=" + pane.tableColumns().size()
                                    + " source=" + pane.tableSource());
                            reportArea(pane, "tableView");
                            buttons(pane);
                            shot(out.resolve("json10k-table.png"));
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

    private static void reportArea(JsonPane pane, String fieldName) {
        try {
            Field field = JsonPane.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            Node host = (Node) field.get(pane);
            Bounds hostBounds = host.getLayoutBounds();
            Bounds screen = host.localToScene(hostBounds);
            System.out.printf("[host] %s h=%.1f sceneRight=%.1f%n",
                    fieldName, hostBounds.getHeight(), screen.getMaxX());
            for (Node node : host.lookupAll(".scroll-bar")) {
                if (node instanceof ScrollBar bar) {
                    Bounds b = bar.getLayoutBounds();
                    Bounds s = bar.localToScene(b);
                    System.out.printf("[bar] %s visible=%s managed=%s opacity=%.2f "
                                    + "w=%.1f h=%.1f right=%.1f min=%s max=%s%n",
                            bar.getOrientation(), bar.isVisible(), bar.isManaged(),
                            bar.getOpacity(), b.getWidth(), b.getHeight(), s.getMaxX(),
                            fmt(bar.getMin()), fmt(bar.getMax()));
                    if (bar.getOrientation() == javafx.geometry.Orientation.VERTICAL) {
                        Node thumb = bar.lookup(".thumb");
                        System.out.println("[thumb] " + (thumb == null ? "无" : thumb.getStyleClass()
                                + " h=" + String.format("%.1f", thumb.getLayoutBounds().getHeight())));
                        if (bar.getMax() > 1000) {
                            try {
                                Field codeAreaField = JsonPane.class.getDeclaredField("editor");
                                codeAreaField.setAccessible(true);
                                Object area = codeAreaField.get(pane);
                                org.fxmisc.richtext.CodeArea code =
                                        (org.fxmisc.richtext.CodeArea) area.getClass()
                                                .getMethod("codeArea").invoke(area);
                                bar.setValue(bar.getMax() * 0.5);
                                bar.layout();
                                System.out.println("[scroll] value=" + bar.getValue()
                                        + " firstVisiblePara="
                                        + code.visibleParToAllParIndex(0)
                                        + " estimatedScrollY=" + code.getEstimatedScrollY());
                                bar.setValue(0);
                                bar.layout();
                                System.out.println("[scroll] back firstVisiblePara="
                                        + code.visibleParToAllParIndex(0));
                            } catch (Exception ignored) {
                                // 仅用于本地验证
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String fmt(double value) {
        return String.format("%.2f", value);
    }

    private static void buttons(JsonPane pane) {
        for (String name : new String[]{"copyButton", "exportButton"}) {
            try {
                Field field = JsonPane.class.getDeclaredField(name);
                field.setAccessible(true);
                ButtonBase button = (ButtonBase) field.get(pane);
                System.out.printf("[button] %s text=%s class=%s type=%s h=%.1f w=%.1f min=%.1f%n",
                        name, button.getText(), button.getStyleClass(),
                        button.getClass().getSimpleName(), button.getHeight(),
                        button.getWidth(), button.minHeight(-1));
                javafx.scene.text.Text text =
                        (javafx.scene.text.Text) button.lookup(".text");
                System.out.printf("[text] %s render=%s font=%.1f w=%.1f h=%.1f "
                                + "baseline=%.1f insets=%s%n",
                        name, text == null ? "无" : text.getText(),
                        text == null ? -1 : text.getFont().getSize(),
                        text == null ? -1 : text.getLayoutBounds().getWidth(),
                        text == null ? -1 : text.getLayoutBounds().getHeight(),
                        text == null ? -1 : text.getBaselineOffset(),
                        button.getPadding());
                if (name.equals("exportButton")) {
                    button.fire();
                    Field f = JsonPane.class.getDeclaredField("exportMenu");
                    f.setAccessible(true);
                    javafx.scene.control.ContextMenu menu =
                            (javafx.scene.control.ContextMenu) f.get(pane);
                    System.out.println("[menu] showing=" + menu.isShowing() + " items="
                            + menu.getItems().stream()
                            .map(m -> ((javafx.scene.control.MenuItem) m).getText()).toList()
                            + " y=" + String.format("%.1f", menu.getY())
                            + " bottom=" + String.format("%.1f", menu.getY() + menu.getHeight())
                            + " anchorTop=" + String.format("%.1f",
                            button.localToScreen(0, 0).getY())
                            + " anchorBottom=" + String.format("%.1f",
                            button.localToScreen(0, button.getHeight()).getY())
                            + " screenH=" + String.format("%.1f",
                            javafx.stage.Screen.getPrimary().getVisualBounds().getMaxY()));
                    menu.hide();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static String sample(int rows) {
        StringBuilder out = new StringBuilder(rows * 200);
        out.append("{\"code\":0,\"message\":\"ok\",\"data\":{\"total\":").append(rows)
                .append(",\"page\":1,\"list\":[");
        for (int i = 0; i < rows; i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append("{\"id\":").append(1000 + i)
                    .append(",\"name\":\"用户").append(i)
                    .append("\",\"tags\":[\"admin\",\"dev\"],\"active\":").append(i % 2 == 0)
                    .append(",\"score\":").append(60 + i % 40)
                    .append(",\"remark\":\"备注 ").append(i).append(" 号\"}");
        }
        out.append("]}}");
        try {
            return com.daliwan.tool.core.JsonFormatter.format(out.toString(),
                    com.daliwan.tool.core.IndentStyle.TWO_SPACES);
        } catch (com.daliwan.tool.core.JsonFormatException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void clickNav(MainView root, String id) {
        try {
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

    private static void shot(Path target) {
        try {
            WritableImage image = scene.snapshot(null);
            int w = (int) image.getWidth();
            int h = (int) image.getHeight();
            int cropX = Math.max(0, w - 320);
            BufferedImage buffered = new BufferedImage(w - cropX, h, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < h; y++) {
                for (int x = cropX; x < w; x++) {
                    buffered.setRGB(x - cropX, y, image.getPixelReader().getArgb(x, y));
                }
            }
            ImageIO.write(buffered, "png", new File(target.toString()));
            System.out.println("[shot] " + target.getFileName());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
