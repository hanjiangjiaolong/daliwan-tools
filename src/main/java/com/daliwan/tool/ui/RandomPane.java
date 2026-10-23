package com.daliwan.tool.ui;

import com.daliwan.tool.core.RandomService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * 随机标识页：UUID（v4）与自定义字符集随机串的批量生成。
 *
 * <p>两种模式共用一块结果区与「生成 / 复制」按钮，只切换上方的选项行，
 * 避免两页之间来回跳。
 */
public class RandomPane extends VBox {

    private static final List<Integer> COUNTS = List.of(1, 5, 10, 20, 50, 100);

    private final ToggleButton uuidMode = new ToggleButton("UUID");
    private final ToggleButton stringMode = new ToggleButton("随机字符串");

    private final CheckBox hyphenBox = new CheckBox("保留连字符");
    private final CheckBox upperBox = new CheckBox("大写");

    private final Spinner<Integer> lengthSpinner = new Spinner<>(1, 256, 16);
    private final CheckBox digitBox = new CheckBox("数字 0-9");
    private final CheckBox lowerBox = new CheckBox("小写 a-z");
    private final CheckBox upperLetterBox = new CheckBox("大写 A-Z");
    private final CheckBox symbolBox = new CheckBox("符号 !@#$");
    private final CheckBox avoidBox = new CheckBox("排除易混淆字符（0 O l I）");
    private final Label poolLabel = new Label();

    private final VBox uuidOptions;
    private final VBox stringOptions;

    private final ComboBox<Integer> countBox = new ComboBox<>();
    private final TextArea output = UiKit.monoArea("生成结果将显示在这里", true);
    private final Label statusLabel = new Label();

    public RandomPane() {
        setSpacing(12);
        setPadding(new Insets(18));

        HBox toolbar = buildToolbar();
        configureModes();

        uuidOptions = buildUuidOptions();
        stringOptions = buildStringOptions();

        VBox resultSection = UiKit.section("生成结果", output);
        VBox.setVgrow(resultSection, Priority.ALWAYS);
        VBox body = new VBox(12, uuidOptions, stringOptions, resultSection);

        getChildren().addAll(toolbar, body, buildFooter());
        // 选中 UUID 模式会触发一次生成，此时数量等控件都已就绪
        uuidMode.setSelected(true);
        setModeVisibility(true);
    }

    // ---------------------------------------------------------------- 布局

    private HBox buildToolbar() {
        uuidMode.getStyleClass().add("mode-toggle");
        stringMode.getStyleClass().add("mode-toggle");
        uuidMode.setFocusTraversable(false);
        stringMode.setFocusTraversable(false);

        Label countLabel = new Label("数量");
        countLabel.getStyleClass().add("field-label");

        countBox.getItems().addAll(COUNTS);
        countBox.setValue(10);
        countBox.getStyleClass().add("indent-box");

        Region spacer = UiKit.spacer();

        HBox bar = new HBox(8, UiKit.hint("UUID v4 与自定义字符集随机串"), spacer,
                countLabel, countBox, uuidMode, stringMode);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private VBox buildUuidOptions() {
        hyphenBox.setSelected(true);
        upperBox.setSelected(false);
        hyphenBox.getStyleClass().add("option-check");
        upperBox.getStyleClass().add("option-check");
        hyphenBox.setOnAction(e -> generate());
        upperBox.setOnAction(e -> generate());

        HBox row = new HBox(14, hyphenBox, upperBox);
        row.setAlignment(Pos.CENTER_LEFT);
        return new VBox(6, UiKit.cardTitle("UUID 选项"), row);
    }

    private VBox buildStringOptions() {
        lengthSpinner.setEditable(true);
        lengthSpinner.setPrefWidth(90);

        Label lengthLabel = new Label("长度");
        lengthLabel.getStyleClass().add("field-label");

        for (CheckBox box : new CheckBox[]{digitBox, lowerBox, upperLetterBox, symbolBox, avoidBox}) {
            box.getStyleClass().add("option-check");
            box.setOnAction(e -> refreshPoolHint());
        }
        digitBox.setSelected(true);
        lowerBox.setSelected(true);
        upperLetterBox.setSelected(true);

        poolLabel.getStyleClass().add("hint");
        poolLabel.setWrapText(true);
        refreshPoolHint();

        HBox lengthRow = new HBox(8, lengthLabel, lengthSpinner);
        lengthRow.setAlignment(Pos.CENTER_LEFT);

        HBox charsetRow = new HBox(14, digitBox, lowerBox, upperLetterBox, symbolBox, avoidBox);
        charsetRow.setAlignment(Pos.CENTER_LEFT);

        return new VBox(8, UiKit.cardTitle("随机字符串选项"), lengthRow, charsetRow, poolLabel);
    }

    private HBox buildFooter() {
        Button generateButton = new Button("生成");
        generateButton.getStyleClass().add("primary-button");
        generateButton.setOnAction(e -> generate());

        Button copyButton = UiKit.copyButton("复制结果", () -> output.getText());

        statusLabel.getStyleClass().add("status");
        statusLabel.setWrapText(true);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);

        HBox bar = new HBox(8, generateButton, copyButton, statusLabel);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    // ---------------------------------------------------------------- 逻辑

    private void configureModes() {
        ToggleGroup group = new ToggleGroup();
        uuidMode.setToggleGroup(group);
        stringMode.setToggleGroup(group);
        group.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
            if (newToggle == null) {
                if (oldToggle != null) {
                    oldToggle.setSelected(true);
                }
                return;
            }
            setModeVisibility(newToggle == uuidMode);
            generate();
        });
    }

    private void setModeVisibility(boolean uuid) {
        uuidOptions.setVisible(uuid);
        uuidOptions.setManaged(uuid);
        stringOptions.setVisible(!uuid);
        stringOptions.setManaged(!uuid);
    }

    private void refreshPoolHint() {
        try {
            String pool = RandomService.poolText(currentOptions());
            poolLabel.setText("可用字符（" + pool.length() + " 个）：" + pool);
        } catch (IllegalArgumentException e) {
            poolLabel.setText(e.getMessage());
        }
    }

    private RandomService.Options currentOptions() {
        return new RandomService.Options(
                lengthSpinner.getValue(),
                digitBox.isSelected(),
                lowerBox.isSelected(),
                upperLetterBox.isSelected(),
                symbolBox.isSelected(),
                avoidBox.isSelected());
    }

    private void generate() {
        int count = countBox.getValue() == null ? 1 : countBox.getValue();
        try {
            List<String> values;
            if (uuidMode.isSelected()) {
                values = RandomService.uuids(count, hyphenBox.isSelected(), upperBox.isSelected());
                setStatus("✓ 已生成 " + count + " 个 UUID", false);
            } else {
                RandomService.Options options = currentOptions();
                int length = options.length();
                if (length <= 0 || length > RandomService.MAX_LENGTH) {
                    setStatus("✗ 长度需在 1 ~ " + RandomService.MAX_LENGTH + " 之间", true);
                    return;
                }
                values = RandomService.randomStrings(count, options);
                setStatus("✓ 已生成 " + count + " 条 × " + length + " 字符", false);
            }
            output.setText(String.join("\n", values));
        } catch (IllegalArgumentException e) {
            output.clear();
            setStatus("✗ " + e.getMessage(), true);
        }
    }

    private void setStatus(String message, boolean error) {
        UiKit.setStatus(statusLabel, message, error);
    }
}
