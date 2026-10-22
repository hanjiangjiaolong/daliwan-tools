package com.daliwan.tool.ui;

import com.daliwan.tool.core.doc.StirlingEngine;
import com.daliwan.tool.core.doc.ConversionEngine;
import com.daliwan.tool.core.doc.EngineResolver;
import com.daliwan.tool.util.FileNames;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Office 转 PDF 页：批量把 Word / Excel / PPT 文档转换为 PDF。
 *
 * <p>转换由内置 Stirling 开源引擎（MIT）完成，本页只负责收集文件、
 * 逐个调用并回显结果。右键菜单把文件递进来时也走同一条路径，
 * 这样用户能看到进度，而不是只有一个「已生成」的提示。
 */
public class DocConvertPane extends VBox {

    /** 可转换的扩展名，与内置引擎保持一致。 */
    private static final Set<String> SUPPORTED = Set.copyOf(StirlingEngine.extensions());

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
    private final Label emptyHint = UiKit.hint("把 Word / Excel / PPT 文件拖进来，或点击右上角「添加文件」");
    private final Label statusLabel = new Label();
    private final Button convertButton = new Button("开始转换");

    /** 是否有一批转换正在跑。 */
    private boolean converting;

    public DocConvertPane() {
        setSpacing(12);
        setPadding(new Insets(18));

        list.setPlaceholder(emptyHint);
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

        Label tip = new Label("PDF 输出到源文件所在目录，同名文件不覆盖；"
                + "设置里可以把「转 PDF」加到资源管理器右键菜单");
        tip.getStyleClass().add("footer");

        getChildren().addAll(buildToolbar(), list, tip, buildFooter());
        configureDragAndDrop();

        // 引擎能力说明（含兜底状态），转换前就让用户知道当前用的是什么引擎
        String capability = EngineResolver.describeCapability();
        if (capability != null) {
            setStatus(capability, false);
        }
    }

    private HBox buildToolbar() {
        Button addButton = new Button("添加文件");
        addButton.getStyleClass().add("ghost-button");
        addButton.setOnAction(e -> chooseFiles());

        Button clearButton = new Button("清空列表");
        clearButton.getStyleClass().add("ghost-button");
        clearButton.setOnAction(e -> {
            items.clear();
            setStatus("", false);
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        String engine = EngineResolver.describeAvailable();
        Label engineLabel = new Label(engine == null ? "未检测到转换引擎" : "引擎：" + engine);
        engineLabel.getStyleClass().add(engine == null ? "status-error" : "status");

        HBox bar = new HBox(8, UiKit.hint("支持 Word / Excel / PPT / CSV，可直接把文件拖进列表"),
                spacer, addButton, clearButton, engineLabel);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private HBox buildFooter() {
        convertButton.getStyleClass().add("primary-button");
        convertButton.setOnAction(e -> convertAll());
        convertButton.setDisable(true);
        items.addListener((javafx.collections.ListChangeListener<Item>) change ->
                convertButton.setDisable(items.isEmpty() || converting));

        statusLabel.getStyleClass().add("status");
        statusLabel.setWrapText(true);
        HBox.setHgrow(statusLabel, Priority.SOMETIMES);

        HBox bar = new HBox(8, convertButton, statusLabel);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private void configureDragAndDrop() {
        UiKit.installFileDrop(this, files -> files.forEach(file -> addFile(file)), null);
    }

    private void chooseFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择要转换的 Office 文档");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Office 文档",
                StirlingEngine.extensions().stream().map(extension -> "*" + extension).toArray(String[]::new)));
        List<File> files = chooser.showOpenMultipleDialog(
                getScene() == null ? null : getScene().getWindow());
        if (files != null) {
            files.forEach(file -> addFile(file.toPath()));
        }
    }

    private void addFile(Path file) {
        enqueue(file);
    }

    /** 加入列表；返回是否真的加进去了（重复或类型不支持时为 false）。 */
    private boolean enqueue(Path file) {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        String extension = dot > 0 ? name.substring(dot) : "";
        if (!SUPPORTED.contains(extension)) {
            setStatus("跳过不支持的文件：" + file.getFileName(), true);
            return false;
        }
        if (items.stream().anyMatch(item -> item.file.equals(file))) {
            return false;
        }
        items.add(new Item(file));
        setStatus("已添加 " + items.size() + " 个文件", false);
        return true;
    }

    private void convertAll() {
        if (items.isEmpty() || converting) {
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
                        list.refresh();
                    });
                    try {
                        // 每个文件单独挑引擎：Word / Excel / PPT 都走内置 Stirling
                        ConversionEngine engine = EngineResolver.resolve(item.file);
                        Path target = naming(item.file);
                        engine.convert(item.file, target);
                        success++;
                        Platform.runLater(() -> {
                            item.status = "✓ " + target.getFileName();
                            item.failed = false;
                            item.converted = true;
                            list.refresh();
                        });
                    } catch (Exception e) {
                        failed++;
                        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                        Platform.runLater(() -> {
                            item.status = "✗ " + message;
                            item.failed = true;
                            list.refresh();
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

        Thread worker = new Thread(task, "office-convert-worker");
        worker.setDaemon(true);
        worker.start();
    }

    /** 一批结束后的收尾：恢复按钮。 */
    private void finishing() {
        converting = false;
        convertButton.setDisable(items.isEmpty());
    }

    /** 目标路径：同目录同名 .pdf，重名追加序号。 */
    private Path naming(Path source) {
        String base = FileNames.baseName(source.getFileName().toString());
        Path dir = source.toAbsolutePath().getParent();
        return FileNames.uniqueTarget(dir, base + ".pdf");
    }

    private void setStatus(String message, boolean error) {
        UiKit.setStatus(statusLabel, message, error);
    }
}
