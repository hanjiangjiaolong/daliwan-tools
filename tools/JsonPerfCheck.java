import com.daliwan.tool.core.JsonFormatter;
import com.daliwan.tool.core.IndentStyle;
import com.daliwan.tool.ui.JsonPane;
import com.daliwan.tool.ui.MainView;
import com.daliwan.tool.ui.ThemeManager;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 临时复现：约 150 KB 的 JSON 在 JSON 页各个动作上的耗时。
 *
 * <p>每个动作都量两件事：触发后界面线程被占住多久（runLater 从排队到执行的时间），
 * 以及动作做完后的状态（长度、行数、节点数、状态栏）。哪个数字最大，卡的就是它。
 */
public final class JsonPerfCheck {

    private static final int TARGET = 151_517;

    private static String FLAT = "";

    public static void main(String[] args) throws Exception {
        Files.createDirectories(Path.of("build/verify"));
        String flat = flat(TARGET);
        FLAT = flat;
        String pretty = JsonFormatter.format(flat, IndentStyle.TWO_SPACES);
        System.out.printf("[sample] 单行 %d 字节 / 1 行 ｜ 格式化后 %d 字节 / %d 行%n",
                flat.length(), pretty.length(), pretty.lines().count());

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
                    List<String[]> steps = new ArrayList<>();
                    steps.add(new String[]{"setContent 单行", "content", flat});
                    steps.add(new String[]{"文本视图末尾改一个字", "edit", null});
                    steps.add(new String[]{"格式化", "format", null});
                    steps.add(new String[]{"切树视图", "tree", null});
                    steps.add(new String[]{"切表格视图", "table", null});
                    steps.add(new String[]{"setContent 格式化后", "content", pretty});
                    run(scene, pane, steps, 0, done);
                });
                second.play();
            });
            first.play();
        });
        done.await();
    }

    private static void run(Scene scene, JsonPane pane, List<String[]> steps, int index,
                            java.util.concurrent.CountDownLatch done) {
        if (index >= steps.size()) {
            done.countDown();
            Platform.exit();
            return;
        }
        String name = steps.get(index)[0];
        String kind = steps.get(index)[1];
        String payload = steps.get(index)[2];

        long t0 = System.nanoTime();
        switch (kind) {
            case "content" -> pane.setContent(payload);
            case "format" -> pane.formatNow();
            case "tree" -> pane.buildNow();
            case "table" -> pane.tableNow();
            case "edit" -> {
                org.fxmisc.richtext.CodeArea area = codeAreaOf(pane);
                area.moveTo(area.getLength());
                area.insertText(area.getLength(), " ");
            }
            default -> { }
        }
        long call = (System.nanoTime() - t0) / 1_000_000;
        // 界面线程被这个动作占住多久：runLater 排在它后面，执行时间差就是阻塞时长
        Platform.runLater(() -> {
            long blocked = (System.nanoTime() - t0) / 1_000_000;
            System.out.printf("[act] %-22s 调用=%d ms，界面线程被占=%d ms%n", name, call, blocked);
        });

        PauseTransition settle = new PauseTransition(Duration.millis(1500));
        settle.setOnFinished(event -> {
            System.out.printf("[after] %-22s %s%n", name, stateOf(pane));
            shot(pane, "build/verify/json-perf-" + name.replace(' ', '_') + ".png");
            gapAfter(scene, pane, steps, index, done);
        });
        settle.play();
    }

    private static void gapAfter(Scene scene, JsonPane pane, List<String[]> steps, int index,
                                 java.util.concurrent.CountDownLatch done) {
        PauseTransition gap = new PauseTransition(Duration.millis(300));
        gap.setOnFinished(event -> run(scene, pane, steps, index + 1, done));
        gap.play();
    }

    private static void shot(JsonPane pane, String target) {
        try {
            javafx.scene.image.WritableImage image = pane.getScene().snapshot(null);
            java.awt.image.BufferedImage buffered = new java.awt.image.BufferedImage(
                    (int) image.getWidth(), (int) image.getHeight(),
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < buffered.getHeight(); y++) {
                for (int x = 0; x < buffered.getWidth(); x++) {
                    buffered.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                }
            }
            javax.imageio.ImageIO.write(buffered, "png", new java.io.File(target));
            System.out.println("[shot] " + target);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static org.fxmisc.richtext.CodeArea codeAreaOf(JsonPane pane) {
        try {
            Field field = JsonPane.class.getDeclaredField("editor");
            field.setAccessible(true);
            Object area = field.get(pane);
            return (org.fxmisc.richtext.CodeArea) area.getClass().getMethod("codeArea").invoke(area);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String stateOf(JsonPane pane) {
        org.fxmisc.richtext.CodeArea area = codeAreaOf(pane);
        int length = area.getLength();
        int from = Math.max(0, length - 300);
        int spans = 0;
        try {
            spans = area.getStyleSpans(from, length).getSpanCount();
        } catch (RuntimeException ignored) {
            spans = -1;
        }
        return "length=" + length
                + " paragraphs=" + area.getParagraphs().size()
                + " 末尾300字符样式段=" + spans
                + " 提示=" + noteOf(pane);
    }

    private static String noteOf(JsonPane pane) {
        try {
            Field field = JsonPane.class.getDeclaredField("highlightNote");
            field.setAccessible(true);
            javafx.scene.control.Label note = (javafx.scene.control.Label) field.get(pane);
            return note.isVisible() ? note.getText() : "（隐藏）";
        } catch (Exception e) {
            return "?";
        }
    }

    private static String statusOf(JsonPane pane) {
        try {
            Field field = JsonPane.class.getDeclaredField("statusLabel");
            field.setAccessible(true);
            return ((javafx.scene.control.Label) field.get(pane)).getText();
        } catch (Exception e) {
            return "?";
        }
    }

    /** 单行、无空格的压缩 JSON：接口原始数据最常见就是这个样子。 */
    private static String flat(int target) {
        StringBuilder out = new StringBuilder("{\"code\":0,\"data\":{\"list\":[");
        int i = 0;
        while (out.length() < target - 200) {
            if (i > 0) {
                out.append(',');
            }
            out.append("{\"id\":").append(1000 + i)
                    .append(",\"name\":\"用户").append(i)
                    .append("\",\"tags\":[\"admin\",\"dev\"],\"active\":").append(i % 2 == 0)
                    .append(",\"score\":").append(60 + i % 40)
                    .append(",\"remark\":\"备注 ").append(i).append(" 号\"}");
            i++;
        }
        out.append("]}}");
        return out.toString();
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
}
