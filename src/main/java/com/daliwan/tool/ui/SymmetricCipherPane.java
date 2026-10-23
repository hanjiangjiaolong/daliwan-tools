package com.daliwan.tool.ui;

import com.daliwan.tool.core.CipherException;
import com.daliwan.tool.core.SymmetricCipher;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * 对称加解密页：AES / DES / 3DES，支持 ECB 与 CBC 模式。
 */
public class SymmetricCipherPane extends VBox {

    private final ComboBox<SymmetricCipher.Algorithm> algorithmBox = new ComboBox<>();
    private final ComboBox<SymmetricCipher.Mode> modeBox = new ComboBox<>();
    private final ComboBox<SymmetricCipher.Encoding> encodingBox = new ComboBox<>();
    private final ComboBox<SymmetricCipher.KeyFormat> keyFormatBox = new ComboBox<>();
    private final ComboBox<SymmetricCipher.KeyFormat> ivFormatBox = new ComboBox<>();

    private final TextField keyField = new TextField();
    private final TextField ivField = new TextField();
    private final Label keyHint = new Label();
    private final Label ivHint = new Label();

    private final TextArea input = UiKit.monoArea("在此粘贴明文（加密）或密文（解密）…", false);
    private final TextArea output = UiKit.monoArea("结果显示在这里", true);
    private final Label statusLabel = new Label();

    public SymmetricCipherPane() {
        setSpacing(12);
        setPadding(new Insets(18));

        SplitPane split = new SplitPane(
                UiKit.section("输入", input),
                UiKit.section("输出", output));
        split.setDividerPositions(0.5);
        split.getStyleClass().add("json-split");
        VBox.setVgrow(split, Priority.ALWAYS);

        getChildren().addAll(buildToolbar(), buildParameters(), split, buildFooter(), buildSecurityNote());

        attachListeners();
        refreshHints();
    }

    // ---------------------------------------------------------------- 布局

    private HBox buildToolbar() {
        Label algorithmLabel = new Label("算法");
        algorithmLabel.getStyleClass().add("field-label");

        algorithmBox.getItems().addAll(SymmetricCipher.Algorithm.values());
        algorithmBox.setValue(SymmetricCipher.Algorithm.AES_128);
        algorithmBox.getStyleClass().add("indent-box");

        Label modeLabel = new Label("模式");
        modeLabel.getStyleClass().add("field-label");

        modeBox.getItems().addAll(SymmetricCipher.Mode.values());
        modeBox.setValue(SymmetricCipher.Mode.CBC);
        modeBox.getStyleClass().add("indent-box");

        Label encodingLabel = new Label("密文格式");
        encodingLabel.getStyleClass().add("field-label");

        encodingBox.getItems().addAll(SymmetricCipher.Encoding.values());
        encodingBox.setValue(SymmetricCipher.Encoding.BASE64);
        encodingBox.getStyleClass().add("indent-box");

        Region spacer = UiKit.spacer();

        Button clearButton = new Button("清空");
        clearButton.getStyleClass().add("ghost-button");
        clearButton.setOnAction(e -> {
            input.clear();
            output.clear();
            setStatus("", false);
        });

        HBox bar = new HBox(8, spacer,
                algorithmLabel, algorithmBox, modeLabel, modeBox, encodingLabel, encodingBox, clearButton);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private GridPane buildParameters() {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);

        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(58);
        labelColumn.setPrefWidth(58);

        ColumnConstraints fieldColumn = new ColumnConstraints();
        fieldColumn.setHgrow(Priority.ALWAYS);

        ColumnConstraints formatColumn = new ColumnConstraints();
        formatColumn.setMinWidth(96);
        formatColumn.setPrefWidth(96);

        ColumnConstraints hintColumn = new ColumnConstraints();
        hintColumn.setMinWidth(150);
        hintColumn.setPrefWidth(150);

        grid.getColumnConstraints().addAll(labelColumn, fieldColumn, formatColumn, hintColumn);

        Label keyLabel = new Label("密钥");
        keyLabel.getStyleClass().add("field-label");
        keyField.setPromptText("按所选格式输入密钥");
        keyField.getStyleClass().add("json-input-field");
        keyFormatBox.getItems().addAll(SymmetricCipher.KeyFormat.values());
        keyFormatBox.setValue(SymmetricCipher.KeyFormat.TEXT);
        keyFormatBox.getStyleClass().add("indent-box");
        keyHint.getStyleClass().add("param-hint");

        Label ivLabel = new Label("向量 IV");
        ivLabel.getStyleClass().add("field-label");
        ivField.setPromptText("CBC 模式必填，长度等于分组大小");
        ivField.getStyleClass().add("json-input-field");
        ivFormatBox.getItems().addAll(SymmetricCipher.KeyFormat.values());
        ivFormatBox.setValue(SymmetricCipher.KeyFormat.TEXT);
        ivFormatBox.getStyleClass().add("indent-box");
        ivHint.getStyleClass().add("param-hint");

        grid.add(keyLabel, 0, 0);
        grid.add(keyField, 1, 0);
        grid.add(keyFormatBox, 2, 0);
        grid.add(keyHint, 3, 0);

        grid.add(ivLabel, 0, 1);
        grid.add(ivField, 1, 1);
        grid.add(ivFormatBox, 2, 1);
        grid.add(ivHint, 3, 1);
        return grid;
    }

    private HBox buildFooter() {
        Button encryptButton = new Button("加密 →");
        encryptButton.getStyleClass().add("primary-button");
        encryptButton.setOnAction(e -> encrypt());

        Button decryptButton = new Button("← 解密");
        decryptButton.getStyleClass().add("ghost-button");
        decryptButton.setOnAction(e -> decrypt());

        statusLabel.getStyleClass().add("status");

        Region spacer = UiKit.spacer();

        Button copyButton = UiKit.copyButton("复制结果", () -> output.getText());

        HBox bar = new HBox(8, encryptButton, decryptButton, statusLabel, spacer, copyButton);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private Label buildSecurityNote() {
        Label note = new Label("提示：DES 密钥仅 56 位，已可被穷举；ECB 模式会让相同明文块产生相同密文块。"
                + "两者仅建议用于教学、兼容旧系统或内部测试，真实数据请用 AES-256 + CBC。");
        note.getStyleClass().add("footer");
        note.setWrapText(true);
        return note;
    }

    // ---------------------------------------------------------------- 交互

    private void attachListeners() {
        algorithmBox.valueProperty().addListener((obs, oldValue, newValue) -> refreshHints());
        modeBox.valueProperty().addListener((obs, oldValue, newValue) -> refreshHints());
        keyFormatBox.valueProperty().addListener((obs, oldValue, newValue) -> refreshHints());
        ivFormatBox.valueProperty().addListener((obs, oldValue, newValue) -> refreshHints());
        keyField.textProperty().addListener((obs, oldValue, newValue) -> refreshHints());
        ivField.textProperty().addListener((obs, oldValue, newValue) -> refreshHints());
    }

    private void refreshHints() {
        SymmetricCipher.Algorithm algorithm = algorithmBox.getValue();
        boolean needsIv = modeBox.getValue().needsIv();

        ivField.setDisable(!needsIv);
        ivFormatBox.setDisable(!needsIv);

        int keyNeed = algorithm.keyBytes();
        int keyActual = SymmetricCipher.byteLength(keyField.getText(), keyFormatBox.getValue());
        keyHint.setText(label(keyNeed, keyActual));
        keyHint.getStyleClass().setAll("param-hint");
        if (keyActual > 0) {
            keyHint.getStyleClass().add(keyActual == keyNeed ? "param-hint-ok" : "param-hint-bad");
        }

        if (needsIv) {
            int ivNeed = algorithm.blockBytes();
            int ivActual = SymmetricCipher.byteLength(ivField.getText(), ivFormatBox.getValue());
            ivHint.setText(label(ivNeed, ivActual));
            ivHint.getStyleClass().setAll("param-hint");
            if (ivActual > 0) {
                ivHint.getStyleClass().add(ivActual == ivNeed ? "param-hint-ok" : "param-hint-bad");
            }
        } else {
            ivHint.setText("ECB 模式不需要");
            ivHint.getStyleClass().setAll("param-hint");
        }
    }

    private String label(int need, int actual) {
        if (actual == 0) {
            return "需要 " + need + " 字节";
        }
        return actual + " / " + need + " 字节" + (actual == need ? " ✓" : "");
    }

    private void encrypt() {
        try {
            String cipherText = SymmetricCipher.encrypt(
                    input.getText(),
                    algorithmBox.getValue(),
                    modeBox.getValue(),
                    keyBytes(keyField, keyFormatBox),
                    modeBox.getValue().needsIv() ? keyBytes(ivField, ivFormatBox) : null,
                    encodingBox.getValue());
            output.setText(cipherText);
            setStatus("✓ 加密完成，" + cipherText.length() + " 字符", false);
        } catch (CipherException e) {
            output.clear();
            setStatus("✗ " + e.getMessage(), true);
        }
    }

    private void decrypt() {
        try {
            String plainText = SymmetricCipher.decrypt(
                    input.getText(),
                    algorithmBox.getValue(),
                    modeBox.getValue(),
                    keyBytes(keyField, keyFormatBox),
                    modeBox.getValue().needsIv() ? keyBytes(ivField, ivFormatBox) : null,
                    encodingBox.getValue());
            output.setText(plainText);
            setStatus("✓ 解密完成，" + plainText.length() + " 字符", false);
        } catch (CipherException e) {
            output.clear();
            setStatus("✗ " + e.getMessage(), true);
        }
    }

    private byte[] keyBytes(TextField field, ComboBox<SymmetricCipher.KeyFormat> formatBox)
            throws CipherException {
        return SymmetricCipher.parseKeyMaterial(field.getText(), formatBox.getValue());
    }

    private void setStatus(String message, boolean error) {
        UiKit.setStatus(statusLabel, message, error);
    }
}
