package com.daliwan.tool.ui;

import com.daliwan.tool.core.TextDiff;
import javafx.animation.PauseTransition;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;

/**
 * 文本比对页：上方左右两栏输入，下方 IDEA 风格的左右分屏差异对照。
 *
 * <p>输入停止 200ms 后自动比对，无需手动点击；可选忽略行内空白差异。
 */
public class TextDiffPane extends VBox {

    private final TextArea leftInput = UiKit.monoArea("粘贴原始文本…", false);
    private final TextArea rightInput = UiKit.monoArea("粘贴修改后的文本…", false);
    private final DiffView diffView = new DiffView();
    private final Label statsLabel = new Label();
    private final CheckBox ignoreWhitespace = new CheckBox("忽略空白");
    private final PauseTransition debounce = new PauseTransition(Duration.millis(200));

    public TextDiffPane() {
        setSpacing(12);
        setPadding(new Insets(18));

        SplitPane inputs = new SplitPane(
                UiKit.section("原始文本", leftInput),
                UiKit.section("修改后文本", rightInput));
        inputs.setDividerPositions(0.5);
        inputs.getStyleClass().add("json-split");
        inputs.setPrefHeight(180);

        Label resultTitle = new Label("差异对照（左：原始 · 右：修改后）");
        resultTitle.getStyleClass().add("section-label");
        VBox result = new VBox(6, resultTitle, diffView);
        VBox.setVgrow(diffView, Priority.ALWAYS);

        SplitPane split = new SplitPane(inputs, result);
        split.setOrientation(javafx.geometry.Orientation.VERTICAL);
        split.setDividerPositions(0.38);
        split.getStyleClass().add("json-split");
        VBox.setVgrow(split, Priority.ALWAYS);

        getChildren().addAll(buildToolbar(), split, buildFooter());

        debounce.setOnFinished(e -> compare());
        leftInput.textProperty().addListener((obs, o, n) -> debounce.playFromStart());
        rightInput.textProperty().addListener((obs, o, n) -> debounce.playFromStart());
        ignoreWhitespace.selectedProperty().addListener((obs, o, n) -> compare());

        addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.ENTER) {
                debounce.stop();
                compare();
                event.consume();
            }
        });
    }

    // ---------------------------------------------------------------- 布局

    private HBox buildToolbar() {
        Button swapButton = new Button("交换两侧");
        swapButton.getStyleClass().add("ghost-button");
        swapButton.setOnAction(e -> {
            String left = leftInput.getText();
            leftInput.setText(rightInput.getText());
            rightInput.setText(left);
        });

        Button clearButton = new Button("清空");
        clearButton.getStyleClass().add("ghost-button");
        clearButton.setOnAction(e -> {
            leftInput.clear();
            rightInput.clear();
            diffView.clear();
            statsLabel.setText("");
        });

        HBox bar = new HBox(8, UiKit.hint("输入即自动比对，Ctrl+Enter 立即刷新"), UiKit.spacer(),
                ignoreWhitespace, swapButton, clearButton);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private HBox buildFooter() {
        statsLabel.getStyleClass().add("status");

        HBox bar = new HBox(8, statsLabel, UiKit.spacer(),
                UiKit.copyButton("复制结果", () -> TextDiff.toText(lastLines)));
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    // ---------------------------------------------------------------- 动作

    private List<TextDiff.Line> lastLines = List.of();
    /** 比对任务的序号：输入还在变时，先前的比对结果回来也要丢弃。 */
    private long diffToken;

    private void compare() {
        String left = leftInput.getText();
        String right = rightInput.getText();
        if (left.isEmpty() && right.isEmpty()) {
            lastLines = List.of();
            diffView.clear();
            UiKit.setStatus(statsLabel, "两侧都为空", false);
            return;
        }
        // Myers 在大文本上要跑几十到几百毫秒，放界面线程会把输入卡住，
        // 因此丢到后台；期间用户继续输入的话，旧结果直接作废。
        long token = ++diffToken;
        boolean ignore = ignoreWhitespace.isSelected();
        Task<List<TextDiff.Line>> task = new Task<>() {
            @Override
            protected List<TextDiff.Line> call() {
                return TextDiff.compare(left, right, ignore);
            }
        };
        task.setOnSucceeded(event -> {
            if (token != diffToken) {
                return;
            }
            List<TextDiff.Line> lines = task.getValue();
            lastLines = lines;
            diffView.show(lines);

            TextDiff.Stats stats = TextDiff.stats(lines);
            if (stats.added() == 0 && stats.deleted() == 0) {
                UiKit.setStatus(statsLabel, "✓ 两侧内容一致（" + stats.unchanged() + " 行）", false);
            } else {
                UiKit.setStatus(statsLabel, "新增 " + stats.added() + " 行，删除 " + stats.deleted()
                        + " 行，未变 " + stats.unchanged() + " 行", false);
            }
        });
        task.setOnFailed(event -> {
            if (token != diffToken) {
                return;
            }
            Throwable error = task.getException();
            UiKit.setStatus(statsLabel, "✗ 比对失败："
                    + (error == null ? "未知错误" : error.getMessage()), true);
        });
        UiKit.runInBackground(task, "text-diff");
    }
}
