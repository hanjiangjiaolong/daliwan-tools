package com.daliwan.tool.ui;

import com.daliwan.tool.core.doc.ConversionException;
import com.daliwan.tool.core.doc.PdfMerger;
import com.daliwan.tool.core.doc.PdfSplitter;
import com.daliwan.tool.util.FileNames;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 工具页：按页拆分、多文件合并。
 *
 * <p>两项都是 PDF 结构操作，与排版无关，因此不依赖 LibreOffice。
 */
public class PdfToolsPane extends VBox {

    private final PdfSplitter splitter = new PdfSplitter();
    private final PdfMerger merger = new PdfMerger();

    // 拆分区
    private Path splitSource;
    private final Label splitFileLabel = new Label("尚未选择 PDF");
    private final TextField rangesField = new TextField();
    private final Label splitStatus = new Label();
    private final Button splitButton = new Button("开始拆分");

    // 合并区
    private final ObservableList<Path> mergeFiles = FXCollections.observableArrayList();
    private final ListView<Path> mergeList = new ListView<>(mergeFiles);
    private final Label mergeHint = UiKit.hint("添加要合并的 PDF，顺序即页面顺序");
    private final TextField mergeNameField = new TextField("合并结果.pdf");
    private final Label mergeStatus = new Label();
    private final Button mergeButton = new Button("合并");

    public PdfToolsPane() {
        setSpacing(12);
        setPadding(new Insets(18));

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Tab splitTab = new Tab("按页拆分", buildSplitPane());
        Tab mergeTab = new Tab("多文件合并", buildMergePane());
        tabs.getTabs().addAll(splitTab, mergeTab);
        VBox.setVgrow(tabs, Priority.ALWAYS);

        getChildren().add(tabs);
    }

    // ---------------------------------------------------------------- 拆分

    private VBox buildSplitPane() {
        splitFileLabel.getStyleClass().add("settings-row-title");

        Button chooseButton = new Button("选择 PDF");
        chooseButton.getStyleClass().add("ghost-button");
        chooseButton.setOnAction(e -> {
            File file = UiKit.chooseFile(this, "选择 PDF 文件", "*.pdf");
            if (file != null) {
                splitSource = file.toPath();
                showSplitSource(file.getName(), splitSource);
                UiKit.setStatus(splitStatus, "", false);
            }
        });

        HBox fileRow = new HBox(12, splitFileLabel, chooseButton);
        fileRow.setAlignment(Pos.CENTER_LEFT);

        rangesField.setPromptText("例如 1-3,5,7-9；留空则每页拆成一个文件");
        rangesField.getStyleClass().add("json-input-field");
        HBox.setHgrow(rangesField, Priority.ALWAYS);

        splitButton.getStyleClass().add("primary-button");
        splitButton.setOnAction(e -> doSplit());

        Label rangeLabel = new Label("页码范围");
        rangeLabel.getStyleClass().add("field-label");
        HBox rangeRow = new HBox(8, rangeLabel, rangesField, splitButton);
        rangeRow.setAlignment(Pos.CENTER_LEFT);

        splitStatus.getStyleClass().add("status");
        splitStatus.setWrapText(true);

        Label tip = new Label("输出到原文件同目录，命名如「合同_p1-3.pdf」；同名文件自动追加序号");
        tip.getStyleClass().add("footer");

        VBox box = new VBox(12, fileRow, rangeRow, splitStatus, tip);
        box.setPadding(new Insets(16));
        return box;
    }

    private void doSplit() {
        if (splitSource == null) {
            UiKit.setStatus(splitStatus, "请先选择 PDF 文件", true);
            return;
        }
        Path source = splitSource;
        String expression = rangesField.getText();
        splitButton.setDisable(true);
        UiKit.setStatus(splitStatus, "正在拆分…", false);

        Task<List<Path>> task = new Task<>() {
            @Override
            protected List<Path> call() throws Exception {
                return splitter.split(source, expression);
            }
        };
        task.setOnSucceeded(event -> {
            splitButton.setDisable(false);
            List<Path> outputs = task.getValue();
            String sample = outputs.isEmpty() ? "" : outputs.get(0).getFileName().toString();
            UiKit.setStatus(splitStatus, "✓ 生成 " + outputs.size() + " 个文件，例如 " + sample, false);
        });
        task.setOnFailed(event -> {
            splitButton.setDisable(false);
            UiKit.setStatus(splitStatus, "✗ " + message(task.getException()), true);
        });
        UiKit.runInBackground(task, "pdf-split");
    }

    /** 显示文件名，页数另起后台线程读——大文件解析要几秒，不能堵住界面。 */
    private void showSplitSource(String fileName, Path file) {
        splitFileLabel.setText(fileName + "　（读取页数…）");
        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                return PdfSplitter.pageCount(file);
            }
        };
        task.setOnSucceeded(event ->
                splitFileLabel.setText(fileName + "　（共 " + task.getValue() + " 页）"));
        task.setOnFailed(event -> splitFileLabel.setText(fileName + "　（无法读取页数）"));
        UiKit.runInBackground(task, "pdf-pagecount");
    }

    // ---------------------------------------------------------------- 合并

    private VBox buildMergePane() {
        mergeList.setPlaceholder(mergeHint);
        mergeList.setCellFactory(view -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : (getIndex() + 1) + ". " + item.getFileName());
            }
        });
        VBox.setVgrow(mergeList, Priority.ALWAYS);

        Button addButton = new Button("添加 PDF");
        addButton.getStyleClass().add("ghost-button");
        addButton.setOnAction(e -> chooseMergeFiles());

        Button removeButton = new Button("移除选中");
        removeButton.getStyleClass().add("ghost-button");
        removeButton.setOnAction(e -> {
            Path selected = mergeList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                mergeFiles.remove(selected);
            }
        });

        Button upButton = new Button("上移");
        upButton.getStyleClass().add("ghost-button");
        upButton.setOnAction(e -> moveSelected(-1));

        Button downButton = new Button("下移");
        downButton.getStyleClass().add("ghost-button");
        downButton.setOnAction(e -> moveSelected(1));

        HBox tools = new HBox(8, addButton, removeButton, upButton, downButton);
        tools.setAlignment(Pos.CENTER_LEFT);

        mergeNameField.getStyleClass().add("json-input-field");
        HBox.setHgrow(mergeNameField, Priority.ALWAYS);

        mergeButton.getStyleClass().add("primary-button");
        mergeButton.setOnAction(e -> doMerge());

        Label nameLabel = new Label("输出文件名");
        nameLabel.getStyleClass().add("field-label");
        HBox nameRow = new HBox(8, nameLabel, mergeNameField, mergeButton);
        nameRow.setAlignment(Pos.CENTER_LEFT);

        mergeStatus.getStyleClass().add("status");
        mergeStatus.setWrapText(true);

        Label tip = new Label("合并顺序即列表顺序；输出到第一个文件所在目录");
        tip.getStyleClass().add("footer");

        VBox box = new VBox(12, tools, mergeList, nameRow, mergeStatus, tip);
        box.setPadding(new Insets(16));
        return box;
    }

    private void moveSelected(int offset) {
        int index = mergeList.getSelectionModel().getSelectedIndex();
        int target = index + offset;
        if (index < 0 || target < 0 || target >= mergeFiles.size()) {
            return;
        }
        Path item = mergeFiles.remove(index);
        mergeFiles.add(target, item);
        mergeList.getSelectionModel().select(target);
    }

    private void chooseMergeFiles() {
        List<File> files = UiKit.chooseFiles(this, "选择要合并的 PDF", "*.pdf");
        if (files != null) {
            for (File file : files) {
                if (mergeFiles.stream().noneMatch(path -> path.equals(file.toPath()))) {
                    mergeFiles.add(file.toPath());
                }
            }
            UiKit.setStatus(mergeStatus, "已添加 " + mergeFiles.size() + " 个文件", false);
        }
    }

    private void doMerge() {
        if (mergeFiles.size() < 2) {
            UiKit.setStatus(mergeStatus, "至少需要两个 PDF 才能合并", true);
            return;
        }
        Path first = mergeFiles.get(0);
        Path directory = first.toAbsolutePath().getParent();
        String name = mergeNameField.getText().isBlank() ? "合并结果" : mergeNameField.getText().strip();
        if (!name.toLowerCase().endsWith(".pdf")) {
            name = name + ".pdf";
        }
        // 走公共的取重名方法：手写 toLowerCase 判断 + replace 拼接会因大小写不一致而死循环
        Path output = FileNames.uniqueTarget(directory, name);
        List<Path> inputs = new ArrayList<>(mergeFiles);

        mergeButton.setDisable(true);
        UiKit.setStatus(mergeStatus, "正在合并 " + inputs.size() + " 个文件…", false);

        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                merger.merge(inputs, output);
                // 页数从合并结果里读一次即可，不必把每个源文件再单独解析一遍
                return PdfSplitter.pageCount(output);
            }
        };
        task.setOnSucceeded(event -> {
            mergeButton.setDisable(false);
            UiKit.setStatus(mergeStatus, "✓ 已合并 " + inputs.size() + " 个文件（共 "
                    + task.getValue() + " 页）→ " + output.getFileName(), false);
        });
        task.setOnFailed(event -> {
            mergeButton.setDisable(false);
            UiKit.setStatus(mergeStatus, "✗ " + message(task.getException()), true);
        });
        UiKit.runInBackground(task, "pdf-merge");
    }

    /** 异常的展示文本：ConversionException 的 message 已是用户可读的中文。 */
    private static String message(Throwable error) {
        if (error == null) {
            return "未知错误";
        }
        String text = error.getMessage();
        return text == null || text.isBlank() ? error.toString() : text;
    }
}
