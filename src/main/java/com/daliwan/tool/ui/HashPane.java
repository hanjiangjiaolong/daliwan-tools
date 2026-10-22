package com.daliwan.tool.ui;

import com.daliwan.tool.core.DigestService;
import com.daliwan.tool.core.HashAlgorithm;
import com.daliwan.tool.core.HashResult;
import javafx.animation.PauseTransition;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 哈希摘要页：算法可选（MD5 / SHA-1 / SHA-256 / SHA-384 / SHA-512），
 * 输入可选文本或文件。
 */
public class HashPane extends VBox {

    private final ComboBox<HashAlgorithm> algorithmBox = new ComboBox<>();
    private final ToggleButton textMode = new ToggleButton("文本");
    private final ToggleButton fileMode = new ToggleButton("文件");

    private final TextArea input = new TextArea();
    private final Label counter = new Label("0 字符");
    private final VBox textBox;

    private final Label fileNameLabel = new Label("把文件拖到这里");
    private final Label fileMetaLabel = new Label("或点击下方按钮选择，支持任意大小文件");
    private final Label statusLabel = new Label();
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Button chooseButton = new Button("选择文件…");
    private final VBox dropZone = new VBox(10);
    private final VBox fileBox;

    private final StackPane content = new StackPane();
    private final ResultPanel resultPanel = new ResultPanel();
    private final PauseTransition debounce = new PauseTransition(Duration.millis(180));

    private Path currentFile;
    private Task<HashResult> currentTask;

    public HashPane() {
        setSpacing(12);
        setPadding(new Insets(18));

        textBox = buildTextBox();
        fileBox = buildFileBox();
        fileBox.setVisible(false);
        fileBox.setManaged(false);
        content.getChildren().addAll(textBox, fileBox);
        VBox.setVgrow(content, Priority.ALWAYS);

        getChildren().addAll(buildToolbar(), content, resultPanel);

        debounce.setOnFinished(e -> computeText());
        algorithmBox.valueProperty().addListener((obs, oldValue, newValue) -> refresh());
        refresh();
    }

    // ---------------------------------------------------------------- 布局

    private HBox buildToolbar() {
        Label algorithmLabel = new Label("算法");
        algorithmLabel.getStyleClass().add("field-label");

        algorithmBox.getItems().addAll(HashAlgorithm.values());
        algorithmBox.setValue(HashAlgorithm.MD5);
        algorithmBox.getStyleClass().add("indent-box");

        ToggleGroup modeGroup = new ToggleGroup();
        textMode.setToggleGroup(modeGroup);
        fileMode.setToggleGroup(modeGroup);
        textMode.setSelected(true);
        for (ToggleButton button : new ToggleButton[]{textMode, fileMode}) {
            button.getStyleClass().add("mode-toggle");
            button.setFocusTraversable(false);
        }

        modeGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null) {
                modeGroup.selectToggle(oldToggle);
                return;
            }
            boolean isText = newToggle == textMode;
            textBox.setVisible(isText);
            textBox.setManaged(isText);
            fileBox.setVisible(!isText);
            fileBox.setManaged(!isText);
            refresh();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button clearButton = new Button("清空");
        clearButton.getStyleClass().add("ghost-button");
        clearButton.setOnAction(e -> {
            input.clear();
            currentFile = null;
            fileNameLabel.setText("把文件拖到这里");
            fileMetaLabel.setText("或点击下方按钮选择，支持任意大小文件");
            statusLabel.setText("");
            progressBar.setVisible(false);
            progressBar.setManaged(false);
            resultPanel.clear();
        });

        HBox bar = new HBox(8, algorithmLabel, algorithmBox, textMode, fileMode, spacer, clearButton);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private VBox buildTextBox() {
        Label hint = new Label("输入文本，结果实时更新 · 字符按 UTF-8 编码");
        hint.getStyleClass().add("hint");

        input.setPromptText("在此输入需要计算摘要的文本…");
        input.setWrapText(true);
        input.getStyleClass().add("input-area");
        VBox.setVgrow(input, Priority.ALWAYS);

        counter.getStyleClass().add("counter");
        input.textProperty().addListener((obs, oldText, newText) -> {
            counter.setText(newText.length() + " 字符");
            debounce.playFromStart();
        });

        HBox bar = new HBox(counter);
        bar.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(8, hint, input, bar);
        VBox.setVgrow(box, Priority.ALWAYS);
        return box;
    }

    private VBox buildFileBox() {
        Label badge = new Label("HASH");
        badge.getStyleClass().add("drop-badge");
        fileNameLabel.getStyleClass().add("drop-title");
        fileMetaLabel.getStyleClass().add("drop-subtitle");

        chooseButton.getStyleClass().add("primary-button");
        chooseButton.setOnAction(e -> openFileChooser());

        dropZone.setAlignment(Pos.CENTER);
        dropZone.getStyleClass().add("drop-zone");
        dropZone.setPadding(new Insets(20));
        dropZone.getChildren().addAll(badge, fileNameLabel, fileMetaLabel, chooseButton);
        VBox.setVgrow(dropZone, Priority.ALWAYS);

        UiKit.installFileDrop(dropZone,
                files -> handleFile(files.get(0)),
                active -> {
                    if (active) {
                        if (!dropZone.getStyleClass().contains("drop-zone-active")) {
                            dropZone.getStyleClass().add("drop-zone-active");
                        }
                    } else {
                        dropZone.getStyleClass().remove("drop-zone-active");
                    }
                });

        statusLabel.getStyleClass().add("status");
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setVisible(false);
        progressBar.setManaged(false);

        return new VBox(10, dropZone, progressBar, statusLabel);
    }

    /** 载入一段待计算文本（供外部调用或自动化验证使用）。 */
    public void setContent(String text) {
        input.setText(text == null ? "" : text);
    }

    // ---------------------------------------------------------------- 动作

    private void refresh() {
        if (textMode.isSelected()) {
            computeText();
        } else if (currentFile != null) {
            handleFile(currentFile);
        }
    }

    private void computeText() {
        String text = input.getText();
        if (text.isEmpty()) {
            resultPanel.clear();
            return;
        }
        resultPanel.setResult(DigestService.hashText(text, algorithmBox.getValue()));
    }

    private void openFileChooser() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择要计算摘要的文件");
        File selected = chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
        if (selected != null) {
            handleFile(selected.toPath());
        }
    }

    private void handleFile(Path file) {
        if (currentTask != null && currentTask.isRunning()) {
            return;
        }
        long size;
        try {
            size = Files.size(file);
        } catch (IOException e) {
            showError("无法读取文件：" + e.getMessage());
            return;
        }

        currentFile = file;
        fileNameLabel.setText(file.getFileName().toString());
        Path parent = file.toAbsolutePath().getParent();
        fileMetaLabel.setText(formatSize(size) + (parent == null ? "" : " · " + parent));
        resultPanel.clear();
        setStatus("正在计算…", false);

        progressBar.setVisible(true);
        progressBar.setManaged(true);
        progressBar.setProgress(0);

        HashAlgorithm algorithm = algorithmBox.getValue();
        long startedAt = System.nanoTime();
        Task<HashResult> task = new Task<>() {
            @Override
            protected HashResult call() throws Exception {
                return DigestService.hashFile(file, algorithm, bytesRead -> {
                    if (size > 0) {
                        updateProgress(bytesRead, size);
                    }
                });
            }
        };
        currentTask = task;
        progressBar.progressProperty().bind(task.progressProperty());

        task.setOnSucceeded(event -> {
            progressBar.progressProperty().unbind();
            progressBar.setProgress(1.0);
            resultPanel.setResult(task.getValue());
            long millis = (System.nanoTime() - startedAt) / 1_000_000L;
            setStatus("计算完成，耗时 " + millis + " ms", false);
            chooseButton.setDisable(false);
        });

        task.setOnFailed(event -> {
            progressBar.progressProperty().unbind();
            progressBar.setProgress(0);
            Throwable error = task.getException();
            showError("计算失败：" + (error == null ? "未知错误" : error.getMessage()));
            chooseButton.setDisable(false);
        });

        chooseButton.setDisable(true);
        Thread worker = new Thread(task, "hash-file-worker");
        worker.setDaemon(true);
        worker.start();
    }

    private void showError(String message) {
        setStatus(message, true);
        progressBar.setVisible(false);
        progressBar.setManaged(false);
    }

    private void setStatus(String message, boolean error) {
        UiKit.setStatus(statusLabel, message, error);
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        String[] units = {"KB", "MB", "GB", "TB"};
        double value = bytes;
        int index = -1;
        while (value >= 1024 && index < units.length - 1) {
            value /= 1024;
            index++;
        }
        return String.format("%.1f %s", value, units[index]);
    }
}
