package com.daliwan.tool.ui;

import com.daliwan.tool.util.FileNames;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 批量转换页面的共同骨架：文件列表、逐行状态、后台逐个转换、结束后的统计。
 *
 * <p>「Office 转 PDF」与「PDF 转 Office」两页的交互完全一样，只有两点不同——
 * 什么样的文件能进列表，以及单个文件怎么转。这两点由子类回答
 * （{@link #rejection(Path)} 与 {@link #convert(Path)}），其余全部在这里。
 *
 * <p>转换一定放在后台线程：一个 Office 文档几百毫秒到几秒，界面线程上跑会把窗口冻住。
 * 每个文件的状态变更再回到界面线程，逐行更新。
 */
public abstract class BatchConvertPane extends VBox {

    /** 列表项：文件与其处理状态。 */
    private static final class Item {
        private final Path file;
        private String status = "待转换";
        private boolean failed;
        private boolean converted;

        Item(Path file) {
            this.file = file;
        }
    }

    private final ObservableList<Item> items = FXCollections.observableArrayList();
    private final ListView<Item> list = new ListView<>(items);
    private final Label statusLabel = new Label();
    private final Button convertButton = new Button("开始转换");

    /** 是否有一批转换正在跑。 */
    private boolean converting;

    /**
     * @param emptyHint 列表为空时显示的提示，如「把 PDF 拖进来…」
     */
    protected BatchConvertPane(String emptyHint) {
        setSpacing(12);
        setPadding(new Insets(18));

        list.setPlaceholder(UiKit.hint(emptyHint));
        list.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(Item item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label name = new Label(item.file.getFileName().toString());
                name.getStyleClass().add("settings-row-title");
                Label path = new Label(item.file.getParent().toString());
                path.getStyleClass().add("settings-row-hint");
                VBox text = new VBox(1, name, path);
                HBox.setHgrow(text, Priority.ALWAYS);

                Label state = new Label(item.status);
                state.getStyleClass().add(item.failed ? "status-error" : "status");
                HBox row = new HBox(12, text, state);
                row.setAlignment(Pos.CENTER_LEFT);
                setGraphic(row);
                setText(null);
            }
        });
        VBox.setVgrow(list, Priority.ALWAYS);

        convertButton.getStyleClass().add("primary-button");
        convertButton.setOnAction(e -> convertAll());
        convertButton.setDisable(true);
        items.addListener((ListChangeListener<Item>) change ->
                convertButton.setDisable(items.isEmpty() || converting));

        statusLabel.getStyleClass().add("status");
        statusLabel.setWrapText(true);
        HBox.setHgrow(statusLabel, Priority.SOMETIMES);
    }

    /** 文件列表，由子类决定它在页面里的位置。 */
    protected final ListView<Item> listView() {
        return list;
    }

    /** 底部操作栏：转换按钮 + 状态文字，再拼上子类自己的提示。 */
    protected final HBox buildFooter(Node... extra) {
        HBox bar = new HBox(8, convertButton, statusLabel);
        bar.getChildren().addAll(extra);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    /** 清空列表与状态。 */
    protected final void clearItems() {
        items.clear();
        setStatus("", false);
    }

    /** 加入一个文件：类型由 {@link #rejection(Path)} 把关，重复的静默跳过。 */
    protected final void addFile(Path file) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        String reason = rejection(file);
        if (reason != null) {
            setStatus(reason, true);
            return;
        }
        if (items.stream().anyMatch(item -> item.file.equals(file))) {
            return;
        }
        items.add(new Item(file));
        setStatus("已添加 " + items.size() + " 个文件", false);
    }

    /**
     * 这个文件为什么不能进列表；返回 {@code null} 表示可以转换。
     *
     * <p>返回的字符串会直接显示在状态栏，所以要把文件名带上，
     * 用户才知道一个也没加进来是为什么。
     */
    protected abstract String rejection(Path file);

    /**
     * 转换单个文件，返回产出路径（用于行上回显「✓ 文件名」）。
     *
     * @throws Exception 转换失败，消息会显示在对应行与状态栏
     */
    protected abstract Path convert(Path file) throws Exception;

    /** 后台线程名，便于在线程转储里区分是哪一页在转。 */
    protected abstract String workerName();

    /** 开跑前的一次性检查：目标格式没选时直接返回 false，不启动线程。 */
    protected boolean readyToConvert() {
        return true;
    }

    private void convertAll() {
        if (items.isEmpty() || converting || !readyToConvert()) {
            return;
        }
        List<Item> todo = items.stream().filter(item -> !item.converted).toList();
        if (todo.isEmpty()) {
            setStatus("列表里的文件都转完了，可点「清空列表」后重新添加", false);
            return;
        }
        converting = true;
        convertButton.setDisable(true);
        long startedAt = System.currentTimeMillis();

        Task<int[]> task = new Task<>() {
            @Override
            protected int[] call() {
                int success = 0;
                int failed = 0;
                for (Item item : todo) {
                    Platform.runLater(() -> {
                        item.status = "转换中…";
                        refreshRow(item);
                    });
                    try {
                        Path output = convert(item.file);
                        success++;
                        Platform.runLater(() -> {
                            item.status = "✓ " + output.getFileName();
                            item.failed = false;
                            item.converted = true;
                            refreshRow(item);
                        });
                    } catch (Exception e) {
                        failed++;
                        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                        Platform.runLater(() -> {
                            item.status = "✗ " + message;
                            item.failed = true;
                            refreshRow(item);
                        });
                    }
                }
                return new int[]{success, failed};
            }
        };
        task.setOnSucceeded(e -> {
            int[] counts = task.getValue();
            long cost = System.currentTimeMillis() - startedAt;
            setStatus("完成：成功 " + counts[0] + " 个，失败 " + counts[1] + " 个，耗时 " + cost + " ms",
                    counts[1] > 0);
            finishing();
        });
        task.setOnFailed(e -> {
            Throwable error = task.getException();
            setStatus("✗ " + (error == null ? "转换失败" : error.getMessage()), true);
            finishing();
        });

        UiKit.runInBackground(task, workerName());
    }

    /**
     * 只刷新这一行。
     *
     * <p>{@code list.refresh()} 会让 ListView 重建所有单元格——每个文件转完都刷一次，
     * 上百个文件时等于几千次全表重绘。这里改成替换列表里的同一项，
     * 只有这一行会重新布局。
     */
    private void refreshRow(Item item) {
        int index = items.indexOf(item);
        if (index >= 0) {
            items.set(index, item);
        }
    }

    /** 一批结束后的收尾：恢复按钮。 */
    private void finishing() {
        converting = false;
        convertButton.setDisable(items.isEmpty());
    }

    /** 目标路径：同目录同名换扩展名，重名追加序号。 */
    protected final Path sibling(Path source, String extension) {
        String base = FileNames.baseName(source.getFileName().toString());
        return FileNames.uniqueTarget(source.toAbsolutePath().getParent(), base + extension);
    }

    protected final void setStatus(String message, boolean error) {
        UiKit.setStatus(statusLabel, message, error);
    }
}
