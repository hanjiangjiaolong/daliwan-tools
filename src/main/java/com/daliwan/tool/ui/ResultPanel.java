package com.daliwan.tool.ui;

import com.daliwan.tool.core.HashResult;
import com.daliwan.tool.util.ClipboardUtil;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * 摘要结果展示区。
 *
 * <p>行数随算法变化：Hex 小写 / Hex 大写 / Base64 恒定输出，
 * MD5 额外提供 16 位截取形式的大小写两行。
 */
public class ResultPanel extends VBox {

    private final VBox rows = new VBox(8);

    public ResultPanel() {
        getStyleClass().add("result-panel");
        setPadding(new Insets(14, 16, 16, 16));
        setSpacing(8);
        getChildren().add(rows);
    }

    /** 展示一次计算结果。 */
    public void setResult(HashResult result) {
        rows.getChildren().clear();
        // MD5 沿用以字符数命名的习惯（32 位 / 16 位），SHA 系列统一称 Hex，
        // 避免把「64 个字符」误读成「64 位摘要」。
        boolean md5Style = result.supportsSixteenDigitForm();
        addRow(md5Style ? "32 位小写" : "Hex 小写", result.hexLower());
        addRow(md5Style ? "32 位大写" : "Hex 大写", result.hexUpper());
        addRow("Base64", result.base64());
        if (md5Style) {
            addRow("16 位小写", result.hex16Lower());
            addRow("16 位大写", result.hex16Upper());
        }
    }

    /** 清空展示内容。 */
    public void clear() {
        rows.getChildren().clear();
    }

    private void addRow(String title, String value) {
        Label label = new Label(title);
        label.getStyleClass().add("field-label");
        label.setMinWidth(76);
        label.setPrefWidth(76);

        TextField field = new TextField(value);
        field.setEditable(false);
        field.getStyleClass().add("digest-field");
        HBox.setHgrow(field, Priority.ALWAYS);

        Button copyButton = new Button("复制");
        copyButton.getStyleClass().add("copy-button");
        copyButton.setOnAction(e -> {
            if (ClipboardUtil.copy(field.getText())) {
                copyButton.setText("已复制");
                copyButton.getStyleClass().add("copied");
                PauseTransition reset = new PauseTransition(Duration.millis(1200));
                reset.setOnFinished(ev -> {
                    copyButton.setText("复制");
                    copyButton.getStyleClass().remove("copied");
                });
                reset.play();
            }
        });

        HBox row = new HBox(10, label, field, copyButton);
        row.setAlignment(Pos.CENTER_LEFT);
        rows.getChildren().add(row);
    }
}
