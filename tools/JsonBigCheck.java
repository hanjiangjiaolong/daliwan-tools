import com.daliwan.tool.ui.JsonPane;
import com.daliwan.tool.ui.MainView;
import com.daliwan.tool.ui.ThemeManager;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
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

/** 临时复现：大 JSON 载入后，文本视图是否还有滚动条、是否还有语法着色。 */
public final class JsonBigCheck {

    public static void main(String[] args) throws Exception {
        Path out = Path.of("build/verify");
        Files.createDirectories(out);
        String json = sample(4000);
        System.out.println("[json] length=" + json.length() + " lines=" + json.lines().count());

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
                    long start = System.nanoTime();
                    pane.setContent(json);
                    System.out.printf("[load] setContent %d ms%n",
                            (System.nanoTime() - start) / 1_000_000);

                    PauseTransition third = new PauseTransition(Duration.millis(1500));
                    third.setOnFinished(last -> {
                        report(pane);
                        shot(scene, out.resolve("json-big.png"));
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

    private static void report(JsonPane pane) {
        try {
            Field field = JsonPane.class.getDeclaredField("editor");
            field.setAccessible(true);
            Object area = field.get(pane);
            javafx.scene.control.ScrollBar vertical = null;
            javafx.scene.control.ScrollBar horizontal = null;
            for (Node node : ((Node) area).lookupAll(".scroll-bar")) {
                if (node instanceof ScrollBar bar) {
                    System.out.printf("[bar] %s visible=%s width=%.1f height=%.1f%n",
                            bar.getOrientation(), bar.isVisible(), bar.getWidth(), bar.getHeight());
                    if (bar.getOrientation() == javafx.geometry.Orientation.VERTICAL) {
                        vertical = bar;
                    } else {
                        horizontal = bar;
                    }
                }
            }
            if (vertical == null) {
                System.out.println("[bar] 没有找到垂直滚动条节点");
            }
            // 看第一段的样式：有样式类说明着色生效
            org.fxmisc.richtext.CodeArea codeArea =
                    (org.fxmisc.richtext.CodeArea) area.getClass().getMethod("codeArea").invoke(area);
            System.out.println("[text] paragraphs=" + codeArea.getParagraphs().size()
                    + " length=" + codeArea.getLength()
                    + " visibleParas=" + codeArea.getVisibleParagraphs().size());
            var styles = codeArea.getStyleSpans(0, Math.min(120, codeArea.getLength()));
            System.out.println("[style] first spans=" + styles.getSpanCount()
                    + " styles=" + styles);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String sample(int rows) {
        StringBuilder out = new StringBuilder(rows * 160);
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
