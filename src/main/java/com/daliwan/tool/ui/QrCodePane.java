package com.daliwan.tool.ui;

import com.daliwan.tool.core.QrCodeException;
import com.daliwan.tool.core.QrCodeService;
import com.daliwan.tool.util.PngEncoder;
import com.google.zxing.common.BitMatrix;
import javafx.animation.PauseTransition;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 二维码页：文本生成二维码、从图片识别二维码。
 */
public class QrCodePane extends VBox {

    /** 识别时图片的最长边：二维码不需要高分辨率，限制尺寸能显著加快大图的解码。 */
    private static final int MAX_IMAGE_SIDE = 2000;

    private final TextArea input = new TextArea();
    private final ImageView preview = new ImageView();
    private final Label statusLabel = new Label();
    private final ComboBox<Integer> sizeBox = new ComboBox<>();
    private final ComboBox<QrCodeService.Level> levelBox = new ComboBox<>();
    private final ColorPicker foregroundPicker = new ColorPicker(Color.web("#111827"));
    private final ColorPicker backgroundPicker = new ColorPicker(Color.WHITE);
    private final PauseTransition debounce = new PauseTransition(Duration.millis(250));
    private final Button saveButton = new Button("保存图片");
    private final Button copyButton = new Button("复制图片");

    private WritableImage currentImage;

    public QrCodePane() {
        setSpacing(12);
        setPadding(new Insets(18));

        SplitPane split = new SplitPane(buildContentBox(), buildPreviewBox());
        split.setDividerPositions(0.42);
        split.getStyleClass().add("json-split");
        VBox.setVgrow(split, Priority.ALWAYS);

        getChildren().addAll(buildToolbar(), split, buildFooter());

        debounce.setOnFinished(e -> regenerate());
        input.textProperty().addListener((obs, oldText, newText) -> debounce.playFromStart());
        regenerate();
    }

    // ---------------------------------------------------------------- 布局

    private HBox buildToolbar() {
        Label sizeLabel = new Label("尺寸");
        sizeLabel.getStyleClass().add("field-label");
        sizeBox.getItems().addAll(256, 384, 512, 768, 1024);
        sizeBox.setValue(512);
        sizeBox.getStyleClass().add("indent-box");
        sizeBox.valueProperty().addListener((obs, oldValue, newValue) -> regenerate());

        Label levelLabel = new Label("容错");
        levelLabel.getStyleClass().add("field-label");
        levelBox.getItems().addAll(QrCodeService.Level.values());
        levelBox.setValue(QrCodeService.Level.MEDIUM);
        levelBox.getStyleClass().add("indent-box");
        levelBox.valueProperty().addListener((obs, oldValue, newValue) -> regenerate());

        Label fgLabel = new Label("前景");
        fgLabel.getStyleClass().add("field-label");
        foregroundPicker.getStyleClass().add("color-picker-small");
        foregroundPicker.setOnAction(e -> regenerate());

        Label bgLabel = new Label("背景");
        bgLabel.getStyleClass().add("field-label");
        backgroundPicker.getStyleClass().add("color-picker-small");
        backgroundPicker.setOnAction(e -> regenerate());

        Region spacer = UiKit.spacer();

        Button clearButton = new Button("清空");
        clearButton.getStyleClass().add("ghost-button");
        clearButton.setOnAction(e -> {
            input.clear();
            regenerate();
        });

        HBox bar = new HBox(8, UiKit.hint("文本与二维码互转"), spacer,
                sizeLabel, sizeBox, levelLabel, levelBox, fgLabel, foregroundPicker, bgLabel, backgroundPicker,
                clearButton);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private VBox buildContentBox() {
        input.setPromptText("输入要生成二维码的文本或链接，支持中文…");
        input.setWrapText(true);
        input.getStyleClass().add("input-area");
        VBox.setVgrow(input, Priority.ALWAYS);

        Label tip = new Label("内容越多，点阵越密；超过约 2900 字节将无法编码");
        tip.getStyleClass().add("counter");
        tip.setWrapText(true);

        Label title = new Label("内容");
        title.getStyleClass().add("section-label");

        VBox box = new VBox(6, title, input, tip);
        VBox.setVgrow(input, Priority.ALWAYS);
        return box;
    }

    private StackPane buildPreviewBox() {
        preview.setPreserveRatio(true);
        // 二维码必须保持锐利，平滑缩放会让边缘模糊影响识别
        preview.setSmooth(false);

        StackPane box = new StackPane(preview);
        box.getStyleClass().add("qr-preview");
        preview.fitWidthProperty().bind(box.widthProperty().subtract(48));
        preview.fitHeightProperty().bind(box.heightProperty().subtract(48));
        return box;
    }

    private HBox buildFooter() {
        Button recognizeButton = new Button("识别图片");
        recognizeButton.getStyleClass().add("ghost-button");
        recognizeButton.setOnAction(e -> recognize());

        saveButton.getStyleClass().add("primary-button");
        saveButton.setOnAction(e -> saveImage());
        saveButton.setDisable(true);

        copyButton.getStyleClass().add("copy-button");
        copyButton.setOnAction(e -> copyImage());
        copyButton.setDisable(true);

        statusLabel.getStyleClass().add("status");

        HBox bar = new HBox(8, recognizeButton, saveButton, copyButton, statusLabel);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    // ---------------------------------------------------------------- 生成

    private void regenerate() {
        String text = input.getText();
        if (text == null || text.isBlank()) {
            preview.setImage(null);
            currentImage = null;
            saveButton.setDisable(true);
            copyButton.setDisable(true);
            UiKit.setStatus(statusLabel, "输入内容后自动生成", false);
            return;
        }
        try {
            BitMatrix matrix = QrCodeService.encode(text, sizeBox.getValue(), levelBox.getValue(), 2);
            currentImage = render(matrix, foregroundPicker.getValue(), backgroundPicker.getValue());
            preview.setImage(currentImage);
            saveButton.setDisable(false);
            copyButton.setDisable(false);
            UiKit.setStatus(statusLabel, "✓ 已生成 " + matrix.getWidth() + "×" + matrix.getHeight() + " 点阵，"
                    + text.length() + " 字符", false);
        } catch (QrCodeException e) {
            preview.setImage(null);
            currentImage = null;
            saveButton.setDisable(true);
            copyButton.setDisable(true);
            UiKit.setStatus(statusLabel, "✗ " + e.getMessage(), true);
        }
    }

    /** 把点阵按给定颜色画成图片。 */
    private static WritableImage render(BitMatrix matrix, Color foreground, Color background) {
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        int fg = toArgb(foreground);
        int bg = toArgb(background);
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            int offset = y * width;
            for (int x = 0; x < width; x++) {
                pixels[offset + x] = matrix.get(x, y) ? fg : bg;
            }
        }
        WritableImage image = new WritableImage(width, height);
        image.getPixelWriter().setPixels(0, 0, width, height,
                PixelFormat.getIntArgbInstance(), pixels, 0, width);
        return image;
    }

    private static int toArgb(Color color) {
        int a = (int) Math.round(color.getOpacity() * 255);
        int r = (int) Math.round(color.getRed() * 255);
        int g = (int) Math.round(color.getGreen() * 255);
        int b = (int) Math.round(color.getBlue() * 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ---------------------------------------------------------------- 保存与识别

    private void saveImage() {
        if (currentImage == null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("保存二维码");
        chooser.setInitialFileName("qrcode.png");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG 图片", "*.png"));
        try {
            File chosen = chooser.showSaveDialog(UiKit.ownerWindow(this));
            if (chosen == null) {
                return;
            }
            // 部分系统下用户手输文件名不会自动补扩展名，这里兜一下
            Path target = chosen.toPath();
            if (!target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")) {
                target = target.resolveSibling(target.getFileName() + ".png");
            }

            int width = (int) Math.round(currentImage.getWidth());
            int height = (int) Math.round(currentImage.getHeight());
            PngEncoder.write(target, readPixels(currentImage), width, height);
            UiKit.setStatus(statusLabel, "✓ 已保存到 " + target.toAbsolutePath(), false);
        } catch (Throwable t) {
            // 保存失败必须让用户看到原因，否则点了按钮「没反应」最难排查
            UiKit.setStatus(statusLabel, "✗ 保存失败：" + t, true);
        }
    }

    private void copyImage() {
        if (currentImage == null) {
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putImage(currentImage);
        if (javafx.scene.input.Clipboard.getSystemClipboard().setContent(content)) {
            copyButton.setText("已复制");
            PauseTransition reset = new PauseTransition(Duration.millis(1200));
            reset.setOnFinished(e -> copyButton.setText("复制图片"));
            reset.play();
        }
    }

    private void recognize() {
        File file = UiKit.chooseFile(this, "选择包含二维码的图片",
                "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif", "*.webp");
        if (file == null) {
            return;
        }
        String name = file.getName();
        UiKit.setStatus(statusLabel, "正在识别…", false);
        // 后台加载 + 限制最长边：一张几十 MB 的照片完整解码进内存要几秒且没必要，
        // 二维码识别对分辨率要求不高，等比压到 2000px 以内更快也更稳
        Image image = new Image(file.toURI().toString(), MAX_IMAGE_SIDE, MAX_IMAGE_SIDE,
                true, true, true);
        image.errorProperty().addListener((obs, oldValue, failed) -> {
            if (Boolean.TRUE.equals(failed)) {
                UiKit.setStatus(statusLabel, "✗ 图片无法读取：" + name, true);
            }
        });
        image.progressProperty().addListener((obs, oldValue, progress) -> {
            if (progress.doubleValue() < 1.0 || image.isError() || image.getWidth() <= 0) {
                return;
            }
            Task<String> task = new Task<>() {
                @Override
                protected String call() throws Exception {
                    return QrCodeService.decode(toBufferedImage(image));
                }
            };
            task.setOnSucceeded(event -> {
                String text = task.getValue();
                input.setText(text);
                UiKit.setStatus(statusLabel, "✓ 已识别，" + text.length() + " 字符", false);
            });
            task.setOnFailed(event -> {
                Throwable error = task.getException();
                String reason = error == null ? "未知错误" : error.getMessage();
                if (reason == null || reason.isBlank()) {
                    reason = "图片里没有找到二维码";
                }
                UiKit.setStatus(statusLabel, "✗ " + reason, true);
            });
            UiKit.runInBackground(task, "qr-decode");
        });
    }

    /** 取出非预乘的 {@code 0xAARRGGBB} 像素，PNG 要求的正是这个约定。 */
    private static int[] readPixels(Image image) {
        int width = (int) Math.round(image.getWidth());
        int height = (int) Math.round(image.getHeight());
        int[] pixels = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height,
                PixelFormat.getIntArgbInstance(), pixels, 0, width);
        return pixels;
    }

    private static BufferedImage toBufferedImage(Image image) {
        int width = (int) Math.round(image.getWidth());
        int height = (int) Math.round(image.getHeight());
        int[] pixels = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height,
                PixelFormat.getIntArgbInstance(), pixels, 0, width);
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        buffered.setRGB(0, 0, width, height, pixels, 0, width);
        return buffered;
    }

}
