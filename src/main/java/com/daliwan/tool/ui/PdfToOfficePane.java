package com.daliwan.tool.ui;

import com.daliwan.tool.core.doc.PdfToOfficeConverter;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
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

/**
 * PDF 转 Office 页：把 PDF 反解成 Word / Excel / PPT 或纯文本。
 *
 * <p>和「Office 转 PDF」是同一个开源库的两个方向（Stirling Office Convert，MIT），
 * 同样不需要装 Office。区别在于这一页的产出是「可编辑」而不是「可打印」：
 * 段落、标题、列表、表格会被还原成真的 Office 元素，但复杂排版只能近似，
 * 界面上先把这句话说在前面，免得用户拿到的 Word 和想象的不一样。
 */
public class PdfToOfficePane extends VBox {

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
    private final Label emptyHint = UiKit.hint("把 PDF 拖进来，或点击右上角「添加文件」");
    private final Label statusLabel = new Label();
    private final Button convertButton = new Button("开始转换");
    private final ComboBox<PdfToOfficeConverter.Target> targetBox =
            new ComboBox<>(FXCollections.observableArrayList(PdfToOfficeConverter.Target.values()));

    /** 是否有一批转换正在跑。 */
    private boolean converting;

    public PdfToOfficePane() {
        setSpacing(12);
        setPadding(new Insets(18));

        targetBox.getSelectionModel().select(PdfToOfficeConverter.Target.WORD);
        targetBox.setCellFactory(view -> targetCell());
        targetBox.setButtonCell(targetCell());

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

        getChildren().addAll(buildToolbar(), list, buildFooter());
        configureDragAndDrop();

        if (!PdfToOfficeConverter.isAvailable()) {
            setStatus("PDF 转 Office 组件不可用（需要 Java 25 及以上运行环境）", true);
        }
    }

    /** 目标格式下拉的单元格：显示「Word（.docx）」这类可读文本。 */
    private ListCell<PdfToOfficeConverter.Target> targetCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(PdfToOfficeConverter.Target target, boolean empty) {
                super.updateItem(target, empty);
                setText(empty || target == null ? null : target.label() + "（." + target.extension() + "）");
            }
        };
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

        HBox bar = new HBox(8, UiKit.hint("转换为"), targetBox,
                spacer, addButton, clearButton);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private HBox buildFooter() {
        convertButton.getStyleClass().add("primary-button");
        convertButton.setOnAction(e -> convertAll());
        convertButton.setDisable(true);
        items.addListener((javafx.collections.ListChangeListener<Item>) change ->
                convertButton.setDisable(items.isEmpty() || converting));

        Label tip = new Label("输出到源文件所在目录，同名文件不覆盖；"
                + "PDF 转的是可编辑内容，版式只能近似还原");
        tip.getStyleClass().add("footer");
        tip.setWrapText(true);
        HBox.setHgrow(tip, Priority.ALWAYS);

        statusLabel.getStyleClass().add("status");
        statusLabel.setWrapText(true);
        HBox.setHgrow(statusLabel, Priority.SOMETIMES);

        HBox bar = new HBox(8, convertButton, statusLabel, tip);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private void configureDragAndDrop() {
        UiKit.installFileDrop(this, files -> files.forEach(this::addFile), null);
    }

    private void chooseFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择要转换的 PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF 文件", "*.pdf"));
        List<File> files = chooser.showOpenMultipleDialog(
                getScene() == null ? null : getScene().getWindow());
        if (files != null) {
            files.forEach(file -> addFile(file.toPath()));
        }
    }

    private void addFile(Path file) {
        enqueue(file);
    }

    /** 加入列表；返回是否真的加进去了（重复或不是 PDF 时为 false）。 */
    private boolean enqueue(Path file) {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        if (!PdfToOfficeConverter.isPdf(file)) {
            setStatus("只支持 PDF 文件：" + file.getFileName(), true);
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
        PdfToOfficeConverter.Target target = targetBox.getValue();
        if (target == null) {
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
                        Path output = PdfToOfficeConverter.targetOf(item.file, target);
                        PdfToOfficeConverter.convert(item.file, output, target);
                        success++;
                        Platform.runLater(() -> {
                            item.status = "✓ " + output.getFileName();
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

        Thread worker = new Thread(task, "pdf-to-office-worker");
        worker.setDaemon(true);
        worker.start();
    }

    /** 一批结束后的收尾：恢复按钮。 */
    private void finishing() {
        converting = false;
        convertButton.setDisable(items.isEmpty());
    }

    private void setStatus(String message, boolean error) {
        UiKit.setStatus(statusLabel, message, error);
    }
}
