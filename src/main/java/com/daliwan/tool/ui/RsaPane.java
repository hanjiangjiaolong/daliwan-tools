package com.daliwan.tool.ui;

import com.daliwan.tool.core.CipherException;
import com.daliwan.tool.core.RsaService;
import com.daliwan.tool.util.ClipboardUtil;
import javafx.animation.PauseTransition;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
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
import javafx.util.Duration;

/**
 * RSA 页：密钥对生成、公钥加密 / 私钥解密、私钥签名 / 公钥验签。
 */
public class RsaPane extends VBox {

    private final ComboBox<Integer> keySizeBox = new ComboBox<>();
    private final TextArea privateKeyArea = createKeyArea("-----BEGIN PRIVATE KEY-----");
    private final TextArea publicKeyArea = createKeyArea("-----BEGIN PUBLIC KEY-----");
    private final TextField signatureField = new TextField();
    private final TextArea input = UiKit.monoArea("待加密的明文，或待解密的 Base64 密文，或待签名的原文…", false);
    private final TextArea output = UiKit.monoArea("结果显示在这里", true);
    private final Label statusLabel = new Label();
    private final Button generateButton = new Button("生成密钥对");

    public RsaPane() {
        setSpacing(10);
        setPadding(new Insets(16, 18, 16, 18));

        SplitPane split = new SplitPane(
                UiKit.section("输入", input),
                UiKit.section("输出", output));
        split.setDividerPositions(0.5);
        split.getStyleClass().add("json-split");
        VBox.setVgrow(split, Priority.ALWAYS);

        getChildren().addAll(buildToolbar(), buildKeyArea(), buildSignatureRow(), split, buildFooter());
    }

    // ---------------------------------------------------------------- 布局

    private HBox buildToolbar() {
        Label sizeLabel = new Label("密钥长度");
        sizeLabel.getStyleClass().add("field-label");

        keySizeBox.getItems().addAll(2048, 3072, 4096);
        keySizeBox.setValue(2048);
        keySizeBox.getStyleClass().add("indent-box");

        generateButton.getStyleClass().add("ghost-button");
        generateButton.setOnAction(e -> generateKeys());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button clearButton = new Button("清空");
        clearButton.getStyleClass().add("ghost-button");
        clearButton.setOnAction(e -> {
            input.clear();
            output.clear();
            signatureField.clear();
            setStatus("", false);
        });

        HBox bar = new HBox(8, spacer,
                sizeLabel, keySizeBox, generateButton, clearButton);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private GridPane buildKeyArea() {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);

        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(44);
        labelColumn.setPrefWidth(44);

        ColumnConstraints areaColumn = new ColumnConstraints();
        areaColumn.setHgrow(Priority.ALWAYS);

        ColumnConstraints buttonColumn = new ColumnConstraints();
        buttonColumn.setMinWidth(56);
        buttonColumn.setPrefWidth(56);

        grid.getColumnConstraints().addAll(labelColumn, areaColumn, buttonColumn);

        Label privateLabel = new Label("私钥");
        privateLabel.getStyleClass().add("field-label");
        Label publicLabel = new Label("公钥");
        publicLabel.getStyleClass().add("field-label");

        Button copyPrivate = copyButton("复制", privateKeyArea);
        Button copyPublic = copyButton("复制", publicKeyArea);

        GridPane.setValignment(privateLabel, javafx.geometry.VPos.TOP);
        GridPane.setValignment(publicLabel, javafx.geometry.VPos.TOP);

        grid.add(privateLabel, 0, 0);
        grid.add(privateKeyArea, 1, 0);
        grid.add(copyPrivate, 2, 0);

        grid.add(publicLabel, 0, 1);
        grid.add(publicKeyArea, 1, 1);
        grid.add(copyPublic, 2, 1);
        return grid;
    }

    private HBox buildSignatureRow() {
        Label label = new Label("签名值");
        label.getStyleClass().add("field-label");
        label.setMinWidth(44);
        label.setPrefWidth(44);

        signatureField.setPromptText("验签时填入签名值；点击「私钥签名」会自动生成");
        signatureField.getStyleClass().add("json-input-field");
        HBox.setHgrow(signatureField, Priority.ALWAYS);

        Button copy = copyButton("复制", signatureField);

        HBox row = new HBox(8, label, signatureField, copy);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private HBox buildFooter() {
        Button encryptButton = new Button("公钥加密");
        encryptButton.getStyleClass().add("primary-button");
        encryptButton.setOnAction(e -> runSafely(this::encrypt));

        Button decryptButton = new Button("私钥解密");
        decryptButton.getStyleClass().add("ghost-button");
        decryptButton.setOnAction(e -> runSafely(this::decrypt));

        Button signButton = new Button("私钥签名");
        signButton.getStyleClass().add("primary-button");
        signButton.setOnAction(e -> runSafely(this::sign));

        Button verifyButton = new Button("公钥验签");
        verifyButton.getStyleClass().add("ghost-button");
        verifyButton.setOnAction(e -> runSafely(this::verify));

        statusLabel.getStyleClass().add("status");
        HBox.setHgrow(statusLabel, Priority.SOMETIMES);

        HBox bar = new HBox(8, encryptButton, decryptButton, signButton, verifyButton, statusLabel);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    // ---------------------------------------------------------------- 动作

    private void generateKeys() {
        int bits = keySizeBox.getValue();
        generateButton.setDisable(true);
        setStatus("正在生成 " + bits + " 位密钥对，请稍候…", false);

        Task<RsaService.KeyPairText> task = new Task<>() {
            @Override
            protected RsaService.KeyPairText call() throws Exception {
                return RsaService.generateKeyPair(bits);
            }
        };
        task.setOnSucceeded(e -> {
            RsaService.KeyPairText keys = task.getValue();
            privateKeyArea.setText(keys.privatePem());
            publicKeyArea.setText(keys.publicPem());
            setStatus("✓ 已生成 " + bits + " 位密钥对", false);
            generateButton.setDisable(false);
        });
        task.setOnFailed(e -> {
            Throwable error = task.getException();
            setStatus("✗ 生成失败：" + (error == null ? "未知错误" : error.getMessage()), true);
            generateButton.setDisable(false);
        });

        Thread worker = new Thread(task, "rsa-keygen");
        worker.setDaemon(true);
        worker.start();
    }

    private void encrypt() throws CipherException {
        String cipherText = RsaService.encrypt(input.getText(), publicKeyArea.getText());
        output.setText(cipherText);
        setStatus("✓ 加密完成，" + cipherText.length() + " 字符", false);
    }

    private void decrypt() throws CipherException {
        String plainText = RsaService.decrypt(input.getText(), privateKeyArea.getText());
        output.setText(plainText);
        setStatus("✓ 解密完成，" + plainText.length() + " 字符", false);
    }

    private void sign() throws CipherException {
        String signature = RsaService.sign(input.getText(), privateKeyArea.getText());
        output.setText(signature);
        signatureField.setText(signature);
        setStatus("✓ 签名完成，签名值已填入上方输入框", false);
    }

    private void verify() throws CipherException {
        String signature = signatureField.getText().isBlank() ? output.getText() : signatureField.getText();
        boolean valid = RsaService.verify(input.getText(), signature, publicKeyArea.getText());
        output.setText(valid ? "验签通过：签名与公钥匹配" : "验签失败：签名不匹配或内容已被修改");
        setStatus(valid ? "✓ 验签通过" : "✗ 验签未通过", !valid);
    }

    private void runSafely(Action action) {
        try {
            action.run();
        } catch (CipherException e) {
            output.clear();
            setStatus("✗ " + e.getMessage(), true);
        } catch (Exception e) {
            output.clear();
            setStatus("✗ " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), true);
        }
    }

    private void setStatus(String message, boolean error) {
        UiKit.setStatus(statusLabel, message, error);
    }

    // ---------------------------------------------------------------- 小工具

    private static TextArea createKeyArea(String prompt) {
        TextArea area = UiKit.monoArea(prompt, false);
        area.setPrefRowCount(3);
        area.setMinHeight(58);
        area.setStyle("-fx-font-size: 11px;");
        return area;
    }

    private static Button copyButton(String text, Node source) {
        Button button = new Button(text);
        button.getStyleClass().add("copy-button");
        button.setOnAction(e -> {
            String value = source instanceof TextArea area ? area.getText()
                    : source instanceof TextField field ? field.getText() : "";
            if (ClipboardUtil.copy(value)) {
                button.setText("已复制");
                PauseTransition reset = new PauseTransition(Duration.millis(1200));
                reset.setOnFinished(ev -> button.setText(text));
                reset.play();
            }
        });
        return button;
    }

    /** 简化动作接口，便于统一异常处理。 */
    @FunctionalInterface
    private interface Action {
        void run() throws Exception;
    }
}
