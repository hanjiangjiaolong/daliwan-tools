package com.daliwan.tool.ui;

import com.daliwan.tool.core.OcrService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.nio.file.Path;

/**
 * OCR 页：选择或拖入图片，用内置的 RapidOCR（PP-OCRv4）识别文字。
 *
 * <p>引擎与模型随程序分发，不需要另行安装；打开页面时后台预热，
 * 首次识别要把模型解压到临时目录，会比之后慢几百毫秒。
 */
public class OcrPane extends VBox {

    /** 预览图的解码上限：只用于展示，识别仍读原图，避免大图解码拖慢界面。 */
    private static final int PREVIEW_LIMIT = 2000;

    private final ImageView preview = new ImageView();
    private final TextArea output = UiKit.monoArea("识别结果将显示在这里，可直接编辑", false);
    private final Label statusLabel = new Label();
    private final CheckBox angleBox = new CheckBox("自动纠正方向");
    private final Button recognizeButton = new Button("开始识别");
    private final Button chooseButton = new Button("选择图片");
    private final ProgressIndicator busy = new ProgressIndicator();

    private Path currentImage;

    public OcrPane() {
        setSpacing(12);
        setPadding(new Insets(18));

        SplitPane split = new SplitPane(buildPreviewBox(), UiKit.section("识别结果", output));
        split.setDividerPositions(0.5);
        split.getStyleClass().add("json-split");
        VBox.setVgrow(split, Priority.ALWAYS);

        getChildren().addAll(buildToolbar(), split, buildFooter());
        configureDragAndDrop();

        busy.setPrefSize(18, 18);
        busy.setVisible(false);
        busy.setManaged(false);

        Platform.runLater(this::warmUpEngine);
    }

    // ---------------------------------------------------------------- 布局

    private HBox buildToolbar() {
        Label hint = new Label("内置 PP-OCRv4，中英文混排效果好，支持 PNG / JPG / BMP");
        hint.getStyleClass().add("hint");

        angleBox.getStyleClass().add("check-box");
        angleBox.setSelected(false);
        // 方向检测要额外过一遍分类模型，倒置的图才用得上，默认关以省时间
        angleBox.setTooltip(new javafx.scene.control.Tooltip(
                "图片旋转了 90°/180°/270° 时勾选，会先判定方向再识别（慢一些）"));

        HBox bar = new HBox(8, hint, UiKit.spacer(), angleBox);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private StackPane buildPreviewBox() {
        preview.setPreserveRatio(true);
        preview.setSmooth(true);

        Label placeholder = new Label("拖入图片，或点击下方「选择图片」");
        placeholder.getStyleClass().add("hint");

        StackPane box = new StackPane(placeholder, preview);
        box.getStyleClass().add("image-preview");
        preview.fitWidthProperty().bind(box.widthProperty().subtract(40));
        preview.fitHeightProperty().bind(box.heightProperty().subtract(40));
        preview.imageProperty().addListener((obs, oldImage, newImage) ->
                placeholder.setVisible(newImage == null));
        return box;
    }

    private HBox buildFooter() {
        chooseButton.getStyleClass().add("ghost-button");
        chooseButton.setOnAction(e -> chooseImage());

        recognizeButton.getStyleClass().add("primary-button");
        recognizeButton.setOnAction(e -> recognize());
        recognizeButton.setDisable(true);

        Button copyButton = UiKit.copyButton("复制结果", () -> output.getText());

        statusLabel.getStyleClass().add("status");
        statusLabel.setWrapText(true);
        HBox.setHgrow(statusLabel, Priority.SOMETIMES);

        HBox bar = new HBox(8, chooseButton, recognizeButton, busy, copyButton, statusLabel);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    // ---------------------------------------------------------------- 引擎

    /**
     * 后台预热引擎：加载动态库、准备模型目录。
     *
     * <p>失败只是提示，不阻断使用——真正的报错留到识别时再给用户看，
     * 免得打开页面就弹一个看不懂的错误。
     */
    private void warmUpEngine() {
        setStatus("正在准备识别引擎…", false);
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                OcrService.warmUp();
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            setStatus("引擎就绪：" + OcrService.describeEngine(), false);
            recognizeButton.setDisable(currentImage == null);
        });
        task.setOnFailed(e -> {
            Throwable error = task.getException();
            String reason = error == null ? "未知原因" : error.getMessage();
            setStatus("引擎准备失败：" + reason, true);
            recognizeButton.setDisable(true);
        });
        UiKit.runInBackground(task, "ocr-warmup");
    }

    // ---------------------------------------------------------------- 动作

    private void chooseImage() {
        java.io.File file = UiKit.chooseFile(this, "选择要识别的图片",
                "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.tif", "*.tiff", "*.gif");
        if (file != null) {
            loadImage(file.toPath());
        }
    }

    private void configureDragAndDrop() {
        UiKit.installFileDrop(this, files -> loadImage(files.get(0)), null);
    }

    /**
     * 载入图片。
     *
     * <p>图片解码走后台加载：大图（几千像素的扫描件）同步解码要一两秒，
     * 拖进来的瞬间界面会整个卡住。这里先显示「正在载入…」，解码完成后再刷新预览。
     */
    private void loadImage(Path file) {
        currentImage = file;
        output.clear();
        setStatus("正在载入 " + file.getFileName() + " …", false);
        recognizeButton.setDisable(true);

        Image image = new Image(file.toUri().toString(), PREVIEW_LIMIT, PREVIEW_LIMIT, true, true, true);
        Runnable ready = () -> {
            if (image.isError()) {
                return;
            }
            preview.setImage(image);
            setStatus("已载入 " + file.getFileName() + "（" + (int) image.getWidth()
                    + "×" + (int) image.getHeight() + "）", false);
            recognizeButton.setDisable(false);
        };
        Runnable failed = () -> {
            currentImage = null;
            preview.setImage(null);
            recognizeButton.setDisable(true);
            setStatus("无法读取图片：" + file.getFileName(), true);
        };
        image.progressProperty().addListener((obs, oldValue, value) -> {
            if (value.doubleValue() >= 1.0) {
                ready.run();
            }
        });
        image.errorProperty().addListener((obs, oldError, isError) -> {
            if (isError) {
                failed.run();
            }
        });
        // 命中缓存时解码可能在注册监听前就已完成，这里补一次
        if (image.getProgress() >= 1.0) {
            ready.run();
        }
        if (image.isError()) {
            failed.run();
        }
    }

    private void recognize() {
        if (currentImage == null) {
            setStatus("请先选择图片", true);
            return;
        }
        Path image = currentImage;
        OcrService.Options options = OcrService.Options.of(angleBox.isSelected());

        recognizeButton.setDisable(true);
        busy.setVisible(true);
        busy.setManaged(true);
        setStatus("正在识别…", false);

        long startedAt = System.nanoTime();
        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                return OcrService.recognize(image, options);
            }
        };
        task.setOnSucceeded(e -> {
            String text = task.getValue();
            output.setText(text);
            long millis = (System.nanoTime() - startedAt) / 1_000_000L;
            setStatus("✓ 识别完成，" + text.length() + " 字符，耗时 " + millis + " ms", false);
            finishBusy();
        });
        task.setOnFailed(e -> {
            Throwable error = task.getException();
            setStatus("✗ " + (error == null ? "识别失败" : error.getMessage()), true);
            finishBusy();
        });
        UiKit.runInBackground(task, "ocr-worker");
    }

    private void finishBusy() {
        busy.setVisible(false);
        busy.setManaged(false);
        recognizeButton.setDisable(currentImage == null);
    }

    private void setStatus(String message, boolean error) {
        UiKit.setStatus(statusLabel, message, error);
    }
}
