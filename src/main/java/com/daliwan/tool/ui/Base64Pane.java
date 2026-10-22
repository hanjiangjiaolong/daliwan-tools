package com.daliwan.tool.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Base64 编解码页：文本与图片两种模式共用一页。
 *
 * <p>两者本质是同一件事——把字节按 64 个字符的字母表重新编码，只是「字节从哪来」
 * 不同：文本模式取字符集编码后的字节，图片模式取文件字节并额外识别 mime。
 * 分成两个导航项会让人在两个页面间来回找，所以合成一页、顶部切换。
 *
 * <p>图片模式的内容由 {@link ImageBase64Pane} 提供，这里只负责切换与共用外框。
 */
public class Base64Pane extends VBox {

    private static final List<Charset> CHARSETS = List.of(
            StandardCharsets.UTF_8,
            Charset.forName("GBK"),
            Charset.forName("GB18030"),
            StandardCharsets.ISO_8859_1
    );

    /** 拖入时认作图片的扩展名，与图片模式的过滤器保持一致。 */
    private static final List<String> IMAGE_SUFFIXES = List.of(
            ".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp",
            ".svg", ".ico", ".tif", ".tiff");

    private final TextArea input = UiKit.monoArea("在此粘贴要处理的文本或 Base64 串…", false, true);
    private final TextArea output = UiKit.monoArea("结果显示在这里", true, true);
    private final Label statusLabel = new Label();
    private final ComboBox<Charset> charsetBox = new ComboBox<>();
    private final CheckBox urlSafeBox = new CheckBox("URL-safe（-_ 且无填充）");
    private final Label charsetLabel = new Label("字符集");

    private final ToggleGroup modeGroup = new ToggleGroup();
    private final ToggleButton textToggle = new ToggleButton("文本");
    private final ToggleButton imageToggle = new ToggleButton("图片");
    private final ImageBase64Pane imagePane = new ImageBase64Pane(true);

    private final VBox textBody;
    private final Button clearButton = new Button("清空");

    /** 当前是否为图片模式。 */
    private boolean imageMode;

    public Base64Pane() {
        setSpacing(12);
        setPadding(new Insets(18));

        SplitPane split = new SplitPane(
                UiKit.section("输入", input),
                UiKit.section("输出", output));
        split.setDividerPositions(0.5);
        split.getStyleClass().add("json-split");
        VBox.setVgrow(split, Priority.ALWAYS);

        textBody = new VBox(12, split, buildFooter());
        VBox.setVgrow(split, Priority.ALWAYS);

        StackPane body = new StackPane(textBody, imagePane);
        VBox.setVgrow(body, Priority.ALWAYS);
        VBox.setVgrow(imagePane, Priority.ALWAYS);

        configureModes();
        configureDrop();

        getChildren().addAll(buildToolbar(), body);
        applyMode(false);
    }

    // ---------------------------------------------------------------- 布局

    private HBox buildToolbar() {
        for (ToggleButton button : new ToggleButton[]{textToggle, imageToggle}) {
            button.setToggleGroup(modeGroup);
            button.getStyleClass().add("mode-toggle");
            button.setFocusTraversable(false);
        }

        charsetLabel.getStyleClass().add("field-label");

        charsetBox.getItems().addAll(CHARSETS);
        charsetBox.setValue(StandardCharsets.UTF_8);
        charsetBox.getStyleClass().add("indent-box");

        urlSafeBox.getStyleClass().add("option-check");

        clearButton.getStyleClass().add("ghost-button");
        clearButton.setOnAction(e -> {
            if (imageMode) {
                imagePane.clear();
            } else {
                clearText();
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // 模式切换固定在最左侧：图片模式下字符集那组控件会隐藏，若把切换放在它们右边，
        // 位置会跟着往右跳。
        HBox bar = new HBox(8, UiKit.hint("文本 / 图片 与 Base64 互转"),
                textToggle, imageToggle, spacer,
                charsetLabel, charsetBox, urlSafeBox, clearButton);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private HBox buildFooter() {
        Button encodeButton = new Button("编码 →");
        encodeButton.getStyleClass().add("primary-button");
        encodeButton.setOnAction(e -> encode());

        Button decodeButton = new Button("← 解码");
        decodeButton.getStyleClass().add("ghost-button");
        decodeButton.setOnAction(e -> decode());

        statusLabel.getStyleClass().add("status");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button copyButton = UiKit.copyButton("复制结果", () -> output.getText());

        HBox bar = new HBox(8, encodeButton, decodeButton, statusLabel, spacer, copyButton);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    // ---------------------------------------------------------------- 模式切换

    private void configureModes() {
        modeGroup.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
            if (newToggle == null) {
                // 再次点击已选中的按钮会取消选择，这里恢复回去
                if (oldToggle != null) {
                    oldToggle.setSelected(true);
                }
                return;
            }
            applyMode(newToggle == imageToggle);
        });
        textToggle.setSelected(true);
    }

    /** 切换可见区域，以及只属于文本模式的字符集 / URL-safe 选项。 */
    private void applyMode(boolean image) {
        imageMode = image;

        textBody.setVisible(!image);
        textBody.setManaged(!image);
        imagePane.setVisible(image);
        imagePane.setManaged(image);

        for (javafx.scene.Node node : new javafx.scene.Node[]{charsetLabel, charsetBox, urlSafeBox}) {
            node.setVisible(!image);
            node.setManaged(!image);
        }

        if (!image) {
            setStatus("", false);
            input.requestFocus();
        }
    }

    /** 图片模式下让整页接住拖入；文本模式下拖入图片则自动切过去。 */
    private void configureDrop() {
        UiKit.installFileDrop(this, files -> {
            if (files.isEmpty()) {
                return;
            }
            Path file = files.get(0);
            String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
            boolean picture = IMAGE_SUFFIXES.stream().anyMatch(name::endsWith);
            if (!picture) {
                return;
            }
            imageToggle.setSelected(true);
            imagePane.acceptFile(file);
        }, null);
    }

    // ---------------------------------------------------------------- 文本编解码

    private void encode() {
        String text = input.getText();
        if (text.isEmpty()) {
            setStatus("请先输入内容", true);
            return;
        }
        byte[] bytes = text.getBytes(charsetBox.getValue());
        Base64.Encoder encoder = urlSafeBox.isSelected()
                ? Base64.getUrlEncoder().withoutPadding()
                : Base64.getEncoder();
        String result = encoder.encodeToString(bytes);
        output.setText(result);
        setStatus("✓ 编码完成，" + bytes.length + " 字节 → " + result.length() + " 字符", false);
    }

    private void decode() {
        String text = input.getText();
        if (text.isBlank()) {
            setStatus("请先输入 Base64 内容", true);
            return;
        }
        // 允许带换行的 Base64（常见于 MIME 与 PEM 片段）
        String cleaned = text.replaceAll("\\s+", "");
        try {
            byte[] decoded = decodeLenient(cleaned);
            output.setText(new String(decoded, charsetBox.getValue()));
            setStatus("✓ 解码完成，" + cleaned.length() + " 字符 → " + decoded.length + " 字节", false);
        } catch (IllegalArgumentException e) {
            output.clear();
            setStatus("✗ 不是合法的 Base64：检查是否含非法字符或填充错误", true);
        }
    }

    /** 先按所选字母表解码，失败再尝试另一种，兼容混用场景。 */
    private byte[] decodeLenient(String cleaned) {
        if (urlSafeBox.isSelected()) {
            return Base64.getUrlDecoder().decode(cleaned);
        }
        try {
            return Base64.getDecoder().decode(cleaned);
        } catch (IllegalArgumentException first) {
            return Base64.getUrlDecoder().decode(cleaned);
        }
    }

    private void clearText() {
        input.clear();
        output.clear();
        setStatus("", false);
    }

    private void setStatus(String message, boolean error) {
        UiKit.setStatus(statusLabel, message, error);
    }

    // ---------------------------------------------------------------- 自检接口

    /** 供自动化自检：切到图片模式。 */
    public void showImageModeNow() {
        imageToggle.setSelected(true);
    }

    /** 供自动化自检：切回文本模式。 */
    public void showTextModeNow() {
        textToggle.setSelected(true);
    }

    /** 供自动化自检：填入文本并直接编码，返回结果。 */
    public String encodeNow(String text) {
        showTextModeNow();
        input.setText(text);
        encode();
        return output.getText();
    }

    /** 当前模式，便于自检断言。 */
    public boolean imageMode() {
        return imageMode;
    }

    /** 图片模式的内容面板。 */
    public ImageBase64Pane imagePane() {
        return imagePane;
    }
}
