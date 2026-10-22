package com.daliwan.tool.ui;

import com.daliwan.tool.core.doc.ImageToPdfConverter;
import com.daliwan.tool.util.FileNames;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 图片转 PDF 页：多张图片按顺序合成一份 PDF，每张图片独占一页。
 */
public class ImageToPdfPane extends VBox {

    private final ObservableList<Path> images = FXCollections.observableArrayList();
    private final ListView<Path> list = new ListView<>(images);
    private final Label emptyHint = UiKit.hint("把多张图片拖进来，按列表顺序合成 PDF");
    private final TextField nameField = new TextField("图片合集.pdf");
    private final Label statusLabel = new Label();
    private final ImageToPdfConverter converter = new ImageToPdfConverter();
    private final Button convertButton = new Button("生成 PDF");

    public ImageToPdfPane() {
        setSpacing(12);
        setPadding(new Insets(18));

        list.setPlaceholder(emptyHint);
        list.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : (getIndex() + 1) + ". " + item.getFileName());
            }
        });
        VBox.setVgrow(list, Priority.ALWAYS);

        getChildren().addAll(buildToolbar(), list, buildFooter());
        configureDragAndDrop();
    }

    private HBox buildToolbar() {
        Button addButton = new Button("添加图片");
        addButton.getStyleClass().add("ghost-button");
        addButton.setOnAction(e -> chooseImages());

        Button removeButton = new Button("移除选中");
        removeButton.getStyleClass().add("ghost-button");
        removeButton.setOnAction(e -> {
            Path selected = list.getSelectionModel().getSelectedItem();
            if (selected != null) {
                images.remove(selected);
            }
        });

        Button upButton = new Button("上移");
        upButton.getStyleClass().add("ghost-button");
        upButton.setOnAction(e -> moveSelected(-1));

        Button downButton = new Button("下移");
        downButton.getStyleClass().add("ghost-button");
        downButton.setOnAction(e -> moveSelected(1));

        HBox bar = new HBox(8, UiKit.hint("每张图片一页，顺序即列表顺序"), UiKit.spacer(),
                addButton, removeButton, upButton, downButton);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private VBox buildFooter() {
        nameField.getStyleClass().add("json-input-field");
        HBox.setHgrow(nameField, Priority.ALWAYS);

        convertButton.getStyleClass().add("primary-button");
        convertButton.setOnAction(e -> convert());

        Label nameLabel = new Label("输出文件名");
        nameLabel.getStyleClass().add("field-label");
        HBox nameRow = new HBox(8, nameLabel, nameField, convertButton);
        nameRow.setAlignment(Pos.CENTER_LEFT);

        statusLabel.getStyleClass().add("status");
        statusLabel.setWrapText(true);

        Label tip = new Label("输出到第一张图片所在目录；支持 " + ImageToPdfConverter.supportedExtensions());
        tip.getStyleClass().add("footer");

        return new VBox(8, nameRow, statusLabel, tip);
    }

    private void configureDragAndDrop() {
        UiKit.installFileDrop(this, files -> files.forEach(this::addImage), null);
    }

    private void chooseImages() {
        List<File> files = UiKit.chooseFiles(this, "选择图片",
                "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif", "*.tif", "*.tiff");
        if (files != null) {
            files.forEach(file -> addImage(file.toPath()));
        }
    }

    private void addImage(Path file) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        if (!ImageToPdfConverter.isSupported(file)) {
            UiKit.setStatus(statusLabel, "不支持的格式：" + file.getFileName(), true);
            return;
        }
        if (images.contains(file)) {
            return;
        }
        images.add(file);
        UiKit.setStatus(statusLabel, "已添加 " + images.size() + " 张图片", false);
    }

    private void moveSelected(int offset) {
        int index = list.getSelectionModel().getSelectedIndex();
        int target = index + offset;
        if (index < 0 || target < 0 || target >= images.size()) {
            return;
        }
        Path item = images.remove(index);
        images.add(target, item);
        list.getSelectionModel().select(target);
    }

    private void convert() {
        if (images.isEmpty()) {
            UiKit.setStatus(statusLabel, "请先添加图片", true);
            return;
        }
        Path first = images.get(0);
        Path directory = first.toAbsolutePath().getParent();
        String name = nameField.getText().isBlank() ? "图片合集" : nameField.getText().strip();
        if (!name.toLowerCase().endsWith(".pdf")) {
            name = name + ".pdf";
        }
        // 走公共的取重名方法：手写 toLowerCase 判断 + replace 拼接会因大小写不一致而死循环
        Path output = FileNames.uniqueTarget(directory, name);
        List<Path> inputs = new ArrayList<>(images);

        convertButton.setDisable(true);
        UiKit.setStatus(statusLabel, "正在生成（" + inputs.size() + " 张图片）…", false);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                converter.convert(inputs, output);
                return null;
            }
        };
        task.setOnSucceeded(event -> {
            convertButton.setDisable(false);
            UiKit.setStatus(statusLabel,
                    "✓ 已生成 " + output.getFileName() + "（" + inputs.size() + " 页）", false);
        });
        task.setOnFailed(event -> {
            convertButton.setDisable(false);
            Throwable error = task.getException();
            String reason = error == null ? "未知错误" : error.getMessage();
            UiKit.setStatus(statusLabel, "✗ " + reason, true);
        });
        UiKit.runInBackground(task, "image-to-pdf");
    }
}
