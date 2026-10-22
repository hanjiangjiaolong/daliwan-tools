package com.daliwan.tool.ui;

import com.daliwan.tool.core.UnicodeCodec;
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

/**
 * Unicode 转义页：中文与 Unicode 转义序列互转。
 */
public class UnicodePane extends VBox {

    private final TextArea input = UiKit.monoArea("输入原文，或粘贴形如 \\u4e2d\\u6587 的转义串…", false);
    private final TextArea output = UiKit.monoArea("结果显示在这里", true);
    private final Label statusLabel = new Label();
    private final ComboBox<UnicodeCodec.Scope> scopeBox = new ComboBox<>();
    private final ComboBox<UnicodeCodec.LetterCase> caseBox = new ComboBox<>();

    public UnicodePane() {
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
        Label scopeLabel = new Label("范围");
        scopeLabel.getStyleClass().add("field-label");

        scopeBox.getItems().addAll(UnicodeCodec.Scope.values());
        scopeBox.setValue(UnicodeCodec.Scope.NON_ASCII);
        scopeBox.getStyleClass().add("indent-box");

        caseBox.getItems().addAll(UnicodeCodec.LetterCase.values());
        caseBox.setValue(UnicodeCodec.LetterCase.LOWER);
        caseBox.getStyleClass().add("indent-box");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button clearButton = new Button("清空");
        clearButton.getStyleClass().add("ghost-button");
        clearButton.setOnAction(e -> {
            input.clear();
            output.clear();
            setStatus("", false);
        });

        HBox bar = new HBox(8, UiKit.hint("\\uXXXX 与原文互转"), spacer,
                scopeLabel, scopeBox, caseBox, clearButton);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private HBox buildFooter() {
        Button escapeButton = new Button("转义 →");
        escapeButton.getStyleClass().add("primary-button");
        escapeButton.setOnAction(e -> escape());

        Button unescapeButton = new Button("← 还原");
        unescapeButton.getStyleClass().add("ghost-button");
        unescapeButton.setOnAction(e -> unescape());

        statusLabel.getStyleClass().add("status");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button copyButton = UiKit.copyButton("复制结果", () -> output.getText());

        HBox bar = new HBox(8, escapeButton, unescapeButton, statusLabel, spacer, copyButton);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private void escape() {
        String text = input.getText();
        if (text.isEmpty()) {
            setStatus("请先输入内容", true);
            return;
        }
        String result = UnicodeCodec.escape(text, scopeBox.getValue(), caseBox.getValue());
        output.setText(result);
        setStatus("✓ 转义完成，" + result.length() + " 字符", false);
    }

    private void unescape() {
        String text = input.getText();
        if (text.isEmpty()) {
            setStatus("请先输入含 \\uXXXX 的内容", true);
            return;
        }
        String result = UnicodeCodec.unescape(text);
        output.setText(result);
        if (result.equals(text)) {
            setStatus("未发现 \\uXXXX 形式的转义序列", true);
        } else {
            setStatus("✓ 还原完成，" + result.length() + " 字符", false);
        }
    }

    private void setStatus(String message, boolean error) {
        UiKit.setStatus(statusLabel, message, error);
    }
}
