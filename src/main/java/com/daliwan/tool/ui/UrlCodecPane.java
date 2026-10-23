package com.daliwan.tool.ui;

import com.daliwan.tool.core.UrlCodec;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * URL 编码解码页。
 *
 * <p>区分表单编码（空格 → +）与 RFC 3986（空格 → %20）两套规则，
 * 二者在中文与特殊字符场景下的结果常被混用，故显式提供选择。
 */
public class UrlCodecPane extends VBox {

    private static final List<Charset> CHARSETS = List.of(
            StandardCharsets.UTF_8,
            Charset.forName("GBK"),
            Charset.forName("GB18030")
    );

    private final TextArea input = UiKit.monoArea("在此粘贴原始文本或已编码的 URL 片段…", false);
    private final TextArea output = UiKit.monoArea("结果显示在这里", true);
    private final Label statusLabel = new Label();
    private final ComboBox<UrlCodec.Rule> ruleBox = new ComboBox<>();
    private final ComboBox<Charset> charsetBox = new ComboBox<>();

    public UrlCodecPane() {
        setSpacing(12);
        setPadding(new Insets(18));

        SplitPane split = new SplitPane(
                UiKit.section("输入", input),
                UiKit.section("输出", output));
        split.setDividerPositions(0.5);
        split.getStyleClass().add("json-split");
        VBox.setVgrow(split, Priority.ALWAYS);

        getChildren().addAll(buildToolbar(), split, buildFooter());
    }

    private HBox buildToolbar() {
        Label ruleLabel = new Label("规则");
        ruleLabel.getStyleClass().add("field-label");

        ruleBox.getItems().addAll(UrlCodec.Rule.values());
        ruleBox.setValue(UrlCodec.Rule.RFC3986);
        ruleBox.getStyleClass().add("indent-box");

        Label charsetLabel = new Label("字符集");
        charsetLabel.getStyleClass().add("field-label");

        charsetBox.getItems().addAll(CHARSETS);
        charsetBox.setValue(StandardCharsets.UTF_8);
        charsetBox.getStyleClass().add("indent-box");

        Region spacer = UiKit.spacer();

        Button clearButton = new Button("清空");
        clearButton.getStyleClass().add("ghost-button");
        clearButton.setOnAction(e -> {
            input.clear();
            output.clear();
            setStatus("", false);
        });

        HBox bar = new HBox(8, UiKit.hint("百分号编码互转"), spacer,
                ruleLabel, ruleBox, charsetLabel, charsetBox, clearButton);
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

        Region spacer = UiKit.spacer();

        Button copyButton = UiKit.copyButton("复制结果", () -> output.getText());

        HBox bar = new HBox(8, encodeButton, decodeButton, statusLabel, spacer, copyButton);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private void encode() {
        String text = input.getText();
        if (text.isEmpty()) {
            setStatus("请先输入内容", true);
            return;
        }
        String result = UrlCodec.encode(text, ruleBox.getValue(), charsetBox.getValue());
        output.setText(result);
        setStatus("✓ 编码完成，" + result.length() + " 字符", false);
    }

    private void decode() {
        String text = input.getText();
        if (text.isEmpty()) {
            setStatus("请先输入已编码内容", true);
            return;
        }
        try {
            String result = UrlCodec.decode(text.trim(), ruleBox.getValue(), charsetBox.getValue());
            output.setText(result);
            setStatus("✓ 解码完成，" + result.length() + " 字符", false);
        } catch (IllegalArgumentException e) {
            output.clear();
            setStatus("✗ 解码失败：存在非法的百分号转义序列（如 %ZZ）", true);
        }
    }

    private void setStatus(String message, boolean error) {
        UiKit.setStatus(statusLabel, message, error);
    }
}
