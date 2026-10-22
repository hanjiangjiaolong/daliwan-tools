package com.daliwan.tool.ui;

import com.daliwan.tool.core.ImageBase64Codec;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 图片与 Base64 / Data URI 互转页。
 *
 * <p>两个方向共用一页：编码侧选图后立刻出结果，解码侧粘贴即预览并可另存。
 * 输出区只渲染前若干万字符——一张 3 MB 的图编出来是 4 MB 文本，整段塞进
 * {@code TextArea} 会让界面停好几秒，而用户真正需要的是「复制」而不是「看」。
 */
public class ImageBase64Pane extends VBox {

    /** 单个文件上限：再大就该走别的工具了，界面也没必要扛。 */
    private static final long FILE_LIMIT = 16L * 1024 * 1024;

    /** 输出区最多渲染的字符数，超出只提示、不影响复制。 */
    private static final int DISPLAY_LIMIT = 200_000;

    /** 支持的图片扩展名。 */
    private static final String[] IMAGE_FILTER = {
            "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp", "*.bmp",
            "*.svg", "*.ico", "*.tif", "*.tiff"
    };

    private final ToggleGroup directionGroup = new ToggleGroup();
    private final ToggleButton encodeToggle = new ToggleButton("图片 → Base64");
    private final ToggleButton decodeToggle = new ToggleButton("Base64 → 图片");

    private final ImageView encodedPreview = new ImageView();
    private final Label encodedPlaceholder = new Label(
            "拖入或选择图片\n支持 PNG / JPEG / GIF / WebP / BMP / TIFF / SVG / ICO");
    private final Label fileInfo = new Label();
    private final TextArea output = UiKit.monoArea("编码结果会显示在这里", true, true);
    private final CheckBox dataUriBox = new CheckBox("带 data:image/…;base64, 前缀");
    private final Button chooseButton = new Button("选择图片…");
    private final Button copyButton;

    private final TextArea decodeInput = UiKit.monoArea(
            "粘贴 Data URI 或纯 Base64，可带换行…", false, true);
    private final ImageView decodedPreview = new ImageView();
    private final Label decodedPlaceholder = new Label("解码后在这里预览");
    private final Label decodeInfo = new Label();
    private final Button decodeButton = new Button("解码预览");
    private final Button saveButton = new Button("另存为…");

    private final Label statusLabel = new Label("尚未选择图片");

    /** 是否作为「Base64」页的图片模式嵌入。 */
    private final boolean embedded;

    private VBox encodeBox;
    private VBox decodeBox;

    /** 当前编码结果与完整文本（复制用的是它，不是界面上被截断的显示）。 */
    private ImageBase64Codec.Encoded encoded;
    private String fullOutput = "";

    /** 当前解码出的字节与建议扩展名。 */
    private byte[] decodedBytes;
    private String decodedExtension = "png";

    public ImageBase64Pane() {
        this(false);
    }

    /**
     * @param embedded 作为「Base64」页的图片模式嵌入时为 {@code true}：
     *                 外边距与「清空」交给外层统一处理，避免出现两份
     *                 padding 和两个清空按钮。
     */
    public ImageBase64Pane(boolean embedded) {
        this.embedded = embedded;
        setSpacing(12);
        if (!embedded) {
            setPadding(new Insets(18));
        }

        copyButton = UiKit.copyButton("复制 Base64", () -> fullOutput);
        copyButton.setTooltip(new Tooltip("复制连续的完整 Base64（界面上的折行不会带入）"));
        output.setTooltip(new Tooltip("长串按窗口宽度折行显示；复制得到的仍是一整行"));

        configureDirection();
        configureSources();

        encodeBox = buildEncodeBox();
        decodeBox = buildDecodeBox();

        StackPane body = new StackPane(encodeBox, decodeBox);
        VBox.setVgrow(body, Priority.ALWAYS);

        getChildren().addAll(buildToolbar(), body, buildFooter());
        applyDirection(true);
    }

    // ---------------------------------------------------------------- 布局

    private HBox buildToolbar() {
        encodeToggle.getStyleClass().add("mode-toggle");
        decodeToggle.getStyleClass().add("mode-toggle");

        if (embedded) {
            // 提示与清空都在外层工具栏，这里只留方向切换
            HBox bar = new HBox(8, encodeToggle, decodeToggle);
            bar.setAlignment(Pos.CENTER_LEFT);
            return bar;
        }

        HBox bar = new HBox(8, UiKit.hint("图片与 Base64 / Data URI 互转"),
                UiKit.spacer(), encodeToggle, decodeToggle, buildClearButton());
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private Button buildClearButton() {
        Button clear = new Button("清空");
        clear.getStyleClass().add("ghost-button");
        clear.setOnAction(e -> clear());
        return clear;
    }

    private VBox buildEncodeBox() {
        chooseButton.getStyleClass().add("primary-button");
        dataUriBox.setSelected(true);
        dataUriBox.getStyleClass().add("option-check");
        dataUriBox.setOnAction(e -> renderOutput());
        copyButton.setDisable(true);

        HBox bar = new HBox(8, chooseButton, dataUriBox, UiKit.spacer(), copyButton);
        bar.setAlignment(Pos.CENTER_LEFT);

        SplitPane split = new SplitPane(
                UiKit.section("预览", buildPreviewArea()),
                UiKit.section("Base64", output));
        split.setDividerPositions(0.44);
        split.getStyleClass().add("json-split");
        VBox.setVgrow(split, Priority.ALWAYS);

        VBox box = new VBox(10, bar, split);
        VBox.setVgrow(split, Priority.ALWAYS);
        return box;
    }

    private VBox buildPreviewArea() {
        StackPane canvas = buildCanvas(encodedPreview, encodedPlaceholder);

        fileInfo.getStyleClass().add("hint");

        VBox box = new VBox(8, canvas, fileInfo);
        VBox.setVgrow(canvas, Priority.ALWAYS);
        return box;
    }

    private StackPane buildCanvas(ImageView view, Label placeholder) {
        view.setPreserveRatio(true);
        view.setFitWidth(300);
        view.setFitHeight(240);
        placeholder.getStyleClass().add("img-preview-placeholder");
        placeholder.setWrapText(true);

        StackPane canvas = new StackPane(view, placeholder);
        canvas.getStyleClass().add("image-preview");
        canvas.setMinHeight(200);
        canvas.setAlignment(Pos.CENTER);
        return canvas;
    }

    private VBox buildDecodeBox() {
        decodeButton.getStyleClass().add("primary-button");
        decodeButton.setOnAction(e -> decodeText());

        saveButton.getStyleClass().add("ghost-button");
        saveButton.setDisable(true);
        saveButton.setOnAction(e -> saveDecoded());

        HBox bar = new HBox(8, decodeButton, saveButton, UiKit.spacer(),
                UiKit.hint("支持 data:image/png;base64,… 与纯 Base64"));
        bar.setAlignment(Pos.CENTER_LEFT);

        StackPane canvas = buildCanvas(decodedPreview, decodedPlaceholder);
        decodeInfo.getStyleClass().add("hint");
        VBox previewBox = UiKit.section("预览", new VBox(8, canvas, decodeInfo));
        VBox.setVgrow(canvas, Priority.ALWAYS);

        SplitPane split = new SplitPane(UiKit.section("输入", decodeInput), previewBox);
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.42);
        split.getStyleClass().add("json-split");
        VBox.setVgrow(split, Priority.ALWAYS);

        VBox box = new VBox(10, bar, split);
        VBox.setVgrow(split, Priority.ALWAYS);
        return box;
    }

    private HBox buildFooter() {
        statusLabel.getStyleClass().add("status");
        HBox bar = new HBox(8, statusLabel);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    // ---------------------------------------------------------------- 交互

    private void configureDirection() {
        for (ToggleButton button : new ToggleButton[]{encodeToggle, decodeToggle}) {
            button.setToggleGroup(directionGroup);
            button.setFocusTraversable(false);
        }
        directionGroup.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
            if (newToggle == null) {
                if (oldToggle != null) {
                    oldToggle.setSelected(true);
                }
                return;
            }
            applyDirection(newToggle == encodeToggle);
        });
        encodeToggle.setSelected(true);
    }

    private void applyDirection(boolean encode) {
        if (encodeBox != null) {
            encodeBox.setVisible(encode);
            encodeBox.setManaged(encode);
        }
        if (decodeBox != null) {
            decodeBox.setVisible(!encode);
            decodeBox.setManaged(!encode);
        }
        setStatus(encode ? statusForEncode() : statusForDecode(), false);
    }

    private String statusForEncode() {
        return encoded == null ? "尚未选择图片"
                : "✓ 已编码 " + encoded.bytes() + " 字节，类型 " + encoded.mime();
    }

    private String statusForDecode() {
        return decodedBytes == null ? "粘贴 Base64 后点「解码预览」"
                : "✓ 已解码 " + formatSize(decodedBytes.length) + "，" + decodedExtension.toUpperCase();
    }

    private void configureSources() {
        UiKit.installFileDrop(this, this::acceptDrop, null);
        chooseButton.setOnAction(e -> chooseImage());
    }

    private void acceptDrop(List<Path> files) {
        if (files.isEmpty()) {
            return;
        }
        acceptFile(files.get(0));
    }

    /** 接受一个图片文件：切到编码方向并立即编码（外层拖入与自检都走这里）。 */
    public void acceptFile(Path file) {
        encodeToggle.setSelected(true);
        loadImage(file);
    }

    private void chooseImage() {
        File file = UiKit.chooseFile(this, "选择图片", IMAGE_FILTER);
        if (file != null) {
            loadImage(file.toPath());
        }
    }

    /** 读盘 + 编码都在后台做：一张几 MB 的图在界面线程上编码会有明显停顿。 */
    private void loadImage(Path file) {
        setStatus("正在读取 " + file.getFileName() + " …", false);
        Task<byte[]> task = new Task<>() {
            @Override
            protected byte[] call() throws IOException {
                long size = Files.size(file);
                if (size > FILE_LIMIT) {
                    throw new IOException("文件过大（" + formatSize(size) + "），上限 "
                            + formatSize(FILE_LIMIT));
                }
                return Files.readAllBytes(file);
            }
        };
        task.setOnSucceeded(event -> {
            byte[] bytes = task.getValue();
            encoded = ImageBase64Codec.encode(bytes, file.getFileName().toString());
            showImage(encodedPreview, encodedPlaceholder, bytes);
            fileInfo.setText(file.getFileName() + " · " + formatSize(bytes.length)
                    + " · " + encoded.mime());
            copyButton.setDisable(false);
            renderOutput();
        });
        task.setOnFailed(event -> setStatus("✗ 读取失败：" + messageOf(task.getException()), true));
        UiKit.runInBackground(task, "img-base64-read");
    }

    /** 把编码结果写进输出区；超长只显示开头，复制仍是完整内容。 */
    private void renderOutput() {
        if (encoded == null) {
            return;
        }
        fullOutput = dataUriBox.isSelected() ? encoded.dataUri() : encoded.base64();
        if (fullOutput.length() > DISPLAY_LIMIT) {
            output.setText(fullOutput.substring(0, DISPLAY_LIMIT)
                    + "\n\n…（共 " + fullOutput.length() + " 字符，此处只显示前 "
                    + DISPLAY_LIMIT / 10_000 + " 万字符；「复制」得到完整内容）");
        } else {
            output.setText(fullOutput);
        }
        output.positionCaret(0);
        setStatus("✓ 已编码 " + encoded.bytes() + " 字节 → " + fullOutput.length()
                + " 字符（约 " + String.format("%.2f", encoded.ratio()) + " 倍），类型 "
                + encoded.mime(), false);
    }

    private void decodeText() {
        try {
            ImageBase64Codec.Decoded decoded = ImageBase64Codec.decode(decodeInput.getText());
            decodedBytes = decoded.data();
            decodedExtension = decoded.extension();
            showImage(decodedPreview, decodedPlaceholder, decoded.data());
            decodeInfo.setText(formatSize(decodedBytes.length) + " · " + decoded.mime()
                    + " · 建议保存为 ." + decodedExtension);
            saveButton.setDisable(false);
            setStatus("✓ 解码完成，" + formatSize(decodedBytes.length) + "（" + decoded.mime() + "）", false);
        } catch (IllegalArgumentException error) {
            decodedBytes = null;
            saveButton.setDisable(true);
            decodedPreview.setImage(null);
            decodedPlaceholder.setText("解码失败，修改内容后重试");
            decodedPlaceholder.setVisible(true);
            decodeInfo.setText("");
            setStatus("✗ " + error.getMessage(), true);
        }
    }

    /** 预览图片；SVG 这类 JavaFX 渲染不了的格式只显示占位说明，数据本身仍然完整。 */
    private void showImage(ImageView view, Label placeholder, byte[] bytes) {
        Image image = new Image(new ByteArrayInputStream(bytes));
        if (image.isError() || image.getWidth() <= 0) {
            view.setImage(null);
            placeholder.setText("这个格式无法在这里预览，但数据是完整的");
            placeholder.setVisible(true);
            return;
        }
        view.setImage(image);
        placeholder.setVisible(false);
    }

    private void saveDecoded() {
        if (decodedBytes == null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("保存图片");
        chooser.setInitialFileName("image." + decodedExtension);
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("图片", "*." + decodedExtension));
        File target = chooser.showSaveDialog(getScene() == null ? null : getScene().getWindow());
        if (target == null) {
            return;
        }
        byte[] bytes = decodedBytes;
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws IOException {
                Files.write(target.toPath(), bytes);
                return null;
            }
        };
        task.setOnSucceeded(event -> setStatus("✓ 已保存 " + target.getName(), false));
        task.setOnFailed(event -> setStatus("✗ 保存失败：" + messageOf(task.getException()), true));
        UiKit.runInBackground(task, "img-base64-save");
    }

    /** 清空两个方向的输入、预览与状态。 */
    public void clear() {
        encoded = null;
        fullOutput = "";
        decodedBytes = null;
        output.clear();
        decodeInput.clear();
        fileInfo.setText("");
        decodeInfo.setText("");
        encodedPreview.setImage(null);
        decodedPreview.setImage(null);
        encodedPlaceholder.setText("拖入或选择图片\n支持 PNG / JPEG / GIF / WebP / BMP / TIFF / SVG / ICO");
        encodedPlaceholder.setVisible(true);
        decodedPlaceholder.setText("解码后在这里预览");
        decodedPlaceholder.setVisible(true);
        copyButton.setDisable(true);
        saveButton.setDisable(true);
        setStatus(encodeToggle.isSelected() ? "尚未选择图片" : "粘贴 Base64 后点「解码预览」", false);
    }

    private void setStatus(String message, boolean error) {
        UiKit.setStatus(statusLabel, message, error);
    }

    private static String messageOf(Throwable error) {
        if (error == null) {
            return "未知错误";
        }
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.toString() : message;
    }

    /** 人类可读的体积，用于状态行与预览信息。 */
    static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    // ---------------------------------------------------------------- 自检接口

    /** 供自动化自检：直接按字节载入一张图片。 */
    public void encodeBytes(byte[] bytes, String fileName) {
        encoded = ImageBase64Codec.encode(bytes, fileName);
        showImage(encodedPreview, encodedPlaceholder, bytes);
        fileInfo.setText(fileName + " · " + formatSize(bytes.length) + " · " + encoded.mime());
        copyButton.setDisable(false);
        renderOutput();
    }

    /** 供自动化自检：直接解码一段 Base64 / Data URI。 */
    public void decodeTextNow(String text) {
        decodeToggle.setSelected(true);
        decodeInput.setText(text);
        decodeText();
    }

    /** 当前输出（完整内容，不含界面截断提示）。 */
    public String outputText() {
        return fullOutput;
    }

    /** 当前解码出的字节数，未解码时为 0。 */
    public int decodedSize() {
        return decodedBytes == null ? 0 : decodedBytes.length;
    }

    /** 当前解码出的建议扩展名。 */
    public String decodedExtension() {
        return decodedExtension;
    }

    /** 当前状态文字，便于自检断言。 */
    public String statusText() {
        return statusLabel.getText();
    }
}
