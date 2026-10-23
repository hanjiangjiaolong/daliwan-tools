package com.daliwan.tool.ui;

import com.daliwan.tool.core.ToolSettings;
import com.daliwan.tool.util.ClipboardUtil;
import javafx.animation.PauseTransition;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** 页面内复用的简单构件，避免各页面重复拼装。 */
public final class UiKit {

    private UiKit() {
    }

    /** 「小节标题 + 内容」的纵向容器，内容自动撑满高度。 */
    public static VBox section(String title, Node content) {
        Label label = new Label(title);
        label.getStyleClass().add("section-label");
        VBox box = new VBox(6, label, content);
        VBox.setVgrow(content, Priority.ALWAYS);
        return box;
    }

    /** 卡片内的小标题，样式在各主题里统一定义。 */
    public static Label cardTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("card-title");
        return label;
    }

    /** 等宽字体文本域，默认不折行（长行横向滚动，便于看对齐的结构化文本）。 */
    public static TextArea monoArea(String prompt, boolean readOnly) {
        return monoArea(prompt, readOnly, false);
    }

    /**
     * 等宽字体文本域，可指定是否按宽度折行。
     *
     * <p>折行只影响显示：{@code getText()} 与复制拿到的仍是没有软换行的原文，
     * 所以长 Base64 这类「一整串、没有空格」的内容折行后更好读，粘贴出去也不会被
     * 多出来的换行污染。JavaFX 的折行对无空格长串会按字符断开，实测 20 万字符
     * 折行布局约 40 ms，可以放心开。
     */
    public static TextArea monoArea(String prompt, boolean readOnly, boolean wrap) {
        TextArea area = new TextArea();
        area.setPromptText(prompt);
        area.setWrapText(wrap);
        area.setEditable(!readOnly);
        area.getStyleClass().add("json-area");
        if (readOnly) {
            area.getStyleClass().add("json-output");
        }
        return area;
    }

    /** 灰色说明文字。 */
    public static Label hint(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("hint");
        label.setPadding(new Insets(0));
        return label;
    }

    /** 撑满剩余宽度的空白，用于把按钮推到行尾。 */
    public static Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    /**
     * 设置状态文字；{@code error} 为真时挂上错误样式。
     *
     * <p>各页面的状态条长得都一样，统一在这里改样式，避免十几个页面各写一遍。
     */
    public static void setStatus(Label target, String message, boolean error) {
        target.setText(message);
        target.getStyleClass().setAll("status");
        if (error) {
            target.getStyleClass().add("status-error");
        }
    }

    /**
     * 后台执行一个 {@link Task}。
     *
     * <p>文件读写、PDF 解析、图片解码这类活都不能放在界面线程上，
     * 否则几百毫秒到几秒的等待里界面是完全冻住的。统一用守护线程起，
     * 退出时不会拖住 JVM。
     */
    public static void runInBackground(Task<?> task, String threadName) {
        Thread worker = new Thread(task, threadName);
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * 复制按钮：点击时取一次当前值写入剪贴板，按钮文字短暂变成「已复制」。
     *
     * <p>值用 {@link Supplier} 延迟到点击那一刻再取，这样结果区刷新后复制到的也是新内容。
     *
     * @param label 按钮常态文字，如「复制结果」
     * @param value 点击时要求值的文本来源
     */
    public static Button copyButton(String label, Supplier<String> value) {
        Button button = new Button(label);
        button.getStyleClass().add("copy-button");
        button.setOnAction(event -> {
            if (!ClipboardUtil.copy(value.get())) {
                return;
            }
            button.setText("已复制");
            PauseTransition reset = new PauseTransition(Duration.millis(1200));
            reset.setOnFinished(finished -> button.setText(label));
            reset.play();
        });
        return button;
    }

    /**
     * 选择单个文件；用户取消时返回 {@code null}。
     *
     * @param owner  用于定位对话框的节点，取其所属窗口
     * @param title  对话框标题
     * @param filter 扩展名，如 {@code "*.pdf"}；为空则不过滤
     */
    public static File chooseFile(Node owner, String title, String... filter) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        addFilter(chooser, filter);
        return chooser.showOpenDialog(ownerWindow(owner));
    }

    /** 选择多个文件；用户取消时返回 {@code null}。 */
    public static List<File> chooseFiles(Node owner, String title, String... filter) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        addFilter(chooser, filter);
        return chooser.showOpenMultipleDialog(ownerWindow(owner));
    }

    private static void addFilter(FileChooser chooser, String... filter) {
        if (filter != null && filter.length > 0) {
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("文件", filter));
        }
    }

    /** 取节点所属窗口，用于给文件对话框定位；节点还没挂到场景上时返回 {@code null}。 */
    public static Window ownerWindow(Node owner) {
        Scene scene = owner == null ? null : owner.getScene();
        return scene == null ? null : scene.getWindow();
    }

    /**
     * 给节点装上「拖入文件」的支持。
     *
     * <p>这里的唯一原则是：<b>拖拽过程中一次都不碰 {@code getDragboard()}</b>，
     * 只在真正放下（DRAG_DROPPED）时读取一次文件列表。
     *
     * <p>原因是 {@code Dragboard#hasFiles()} 看着像读个布尔值，实际上要穿过
     * JNI 调 Windows 的 {@code IDataObject::EnumFormatEtc}：拖拽源程序（资源管理器、
     * 浏览器、聊天工具等）必须把数据对象里挂的每一种格式都准备好才能枚举完，
     * 拖的是图片时还会临时拉起缩略图/属性处理器这类 Shell 扩展——一次几百毫秒到
     * 几秒不等，而且是<b>同步阻塞在界面线程上</b>的。
     *
     * <p>DRAG_OVER 每秒会来几十次，DRAG_ENTERED 也会随着指针穿过子节点反复触发，
     * 只要在其中任何一个里问一句 {@code hasFiles()}，界面就会一卡一卡甚至彻底冻住——
     * 表现就是「文件刚进窗口就死了」。所以这里改成：悬停阶段只做「接受」这一件
     * 不耗时的事，内容到底是不是文件，等放下那一刻再看。
     *
     * @param target  接收拖放的节点
     * @param onFiles 拖入文件时回调，参数是拖拽里的全部文件
     * @param onHover 可选：拖拽悬停状态变化（参数是「正在悬停」），用于高亮投放区
     */
    public static void installFileDrop(Node target, Consumer<List<Path>> onFiles,
                                       Consumer<Boolean> onHover) {
        final boolean[] hovering = {false};

        target.addEventHandler(DragEvent.DRAG_ENTERED, event -> {
            setHover(onHover, hovering, true);
            DropStats.enter();
            event.consume();
        });

        target.addEventHandler(DragEvent.DRAG_OVER, event -> {
            // 只接受，不查询内容：这一句不跨进程、不触碰拖拽源。
            event.acceptTransferModes(TransferMode.COPY, TransferMode.LINK);
            DropStats.over();
            event.consume();
        });

        target.addEventHandler(DragEvent.DRAG_EXITED, event -> {
            // 在子节点之间移动时也会触发 EXITED，用坐标判断是否真的离开了投放区，
            // 否则高亮会一路闪烁，样式反复重算同样拖慢界面。
            if (!pointerInside(target, event)) {
                setHover(onHover, hovering, false);
                DropStats.exit();
            }
            event.consume();
        });

        target.addEventHandler(DragEvent.DRAG_DROPPED, event -> {
            setHover(onHover, hovering, false);
            boolean handled = false;
            long start = System.nanoTime();
            try {
                if (event.getDragboard() != null && onFiles != null) {
                    List<File> files = event.getDragboard().getFiles();
                    if (files != null && !files.isEmpty()) {
                        onFiles.accept(files.stream().map(File::toPath).toList());
                        handled = true;
                    }
                }
            } finally {
                DropStats.dropped(System.nanoTime() - start, handled);
            }
            event.setDropCompleted(handled);
            event.consume();
        });
    }

    /** 只在状态真的变化时通知，避免重复触发样式重算。 */
    private static void setHover(Consumer<Boolean> onHover, boolean[] hovering, boolean next) {
        if (onHover == null || hovering[0] == next) {
            return;
        }
        hovering[0] = next;
        onHover.accept(next);
    }

    /** 指针（场景坐标）是否还落在节点范围内。 */
    private static boolean pointerInside(Node target, DragEvent event) {
        try {
            Point2D local = target.sceneToLocal(event.getSceneX(), event.getSceneY());
            return local != null && target.getLayoutBounds().contains(local);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /**
     * 拖拽过程的计时统计：只在放下时往日志文件追加一行，用于排查界面卡顿。
     *
     * <p>{@code maxGapMillis} 是相邻两次 DRAG_OVER 的最大间隔，它直接反映界面线程
     * 被堵了多久：正常应该是个位数毫秒，出现几百毫秒就说明有活压在界面线程上了。
     */
    private static final class DropStats {

        private static long startMillis;
        private static long lastOverNanos;
        private static long maxGapMillis;
        private static int overCount;

        private DropStats() {
        }

        static synchronized void enter() {
            startMillis = System.currentTimeMillis();
            lastOverNanos = 0L;
            maxGapMillis = 0L;
            overCount = 0;
        }

        static synchronized void over() {
            overCount++;
            long now = System.nanoTime();
            if (lastOverNanos != 0L) {
                long gap = (now - lastOverNanos) / 1_000_000L;
                if (gap > maxGapMillis) {
                    maxGapMillis = gap;
                }
            }
            lastOverNanos = now;
        }

        static synchronized void exit() {
            lastOverNanos = 0L;
        }

        static synchronized void dropped(long readNanos, boolean handled) {
            try {
                long total = System.currentTimeMillis() - startMillis;
                // 手写时间戳而不是 SimpleDateFormat：少一层对本地化数据的依赖
                String line = java.time.LocalDateTime.now()
                        .truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
                        + "  拖拽结束 handled=" + handled
                        + "  悬停事件=" + overCount
                        + "  界面最大间隔=" + maxGapMillis + "ms"
                        + "  读取文件=" + (readNanos / 1_000_000L) + "ms"
                        + "  总时长=" + total + "ms";
                writeLog(line);
            } catch (Exception ignored) {
                // 统计写不进去不影响功能
            } finally {
                lastOverNanos = 0L;
            }
        }

        private static void writeLog(String line) {
            try {
                Path dir = ToolSettings.DEFAULT_FILE.getParent();
                if (dir == null) {
                    return;
                }
                Files.createDirectories(dir);
                Files.write(dir.resolve("dnd.log"),
                        (line + System.lineSeparator()).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND);
            } catch (Exception ignored) {
                // 统计写不进去不影响功能
            }
        }
    }
}
