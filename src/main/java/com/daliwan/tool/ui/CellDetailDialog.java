package com.daliwan.tool.ui;

import com.daliwan.tool.core.IndentStyle;
import com.daliwan.tool.core.JsonFormatException;
import com.daliwan.tool.core.JsonFormatter;
import com.daliwan.tool.util.ClipboardUtil;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * 单元格详情：表格里一格的完整内容。
 *
 * <p>表格列宽有限，长文本会被截断成一行省略号——嵌套的对象 / 数组还原成 JSON 后
 * 动辄几百字符，光看表格根本读不全。双击单元格弹出这个窗口，内容照原样铺开：
 * 能滚动、能复制，看着像 JSON 的还会默认格式化好（一键切回原始串）。
 *
 * <p>刻意做成<b>非模态</b>：看一个单元格时往往还要对照别的行，模态窗口会把主界面
 * 锁住，只能关了再看下一个。非模态下双击另一个单元格，这里的内容就地换掉。
 *
 * <p>样式沿用 {@link AboutDialog} 那套（{@code app-root} + {@code card}），
 * 样式表从主窗口拷贝，深浅主题自动跟随。
 */
public final class CellDetailDialog {

    /** 展示的字符上限：再大的文本 TextArea 布局会明显卡住，超出部分截断并说明。 */
    static final int MAX_CHARS = 200_000;

    private static final IndentStyle INDENT = IndentStyle.TWO_SPACES;

    /** 当前打开的窗口，复用它换内容，避免连点单元格弹出一叠窗口。 */
    private static CellDetailDialog open;

    private final Stage stage = new Stage();
    private final Label meta = new Label();
    private final TextArea area = UiKit.monoArea("", true, true);
    private final Button prettyButton = new Button("查看原始");
    private final Button copyButton = new Button("复制");

    private String raw = "";
    private String prettyText;

    /** 当前显示的是格式化后的文本，还是原始串。 */
    private boolean showPretty;

    private CellDetailDialog(Window owner) {
        Label title = new Label("单元格内容");
        title.getStyleClass().add("card-title");
        meta.getStyleClass().add("hint");

        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox header = new HBox(10, title, gap, meta);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox.setVgrow(area, Priority.ALWAYS);
        area.setMinHeight(180);
        // 换行默认开：单元格里最常见的是一整串紧凑 JSON，不换行得一直横向拖
        CheckBox wrap = new CheckBox("自动换行");
        wrap.setSelected(true);
        wrap.selectedProperty().addListener((observable, wasOn, on) -> area.setWrapText(on));

        prettyButton.getStyleClass().add("ghost-button");
        prettyButton.setFocusTraversable(false);
        prettyButton.setVisible(false);
        prettyButton.setManaged(false);
        // 文案写的是「接下来点一下会变成什么」：正在看格式化文本时给「查看原始」，
        // 反过来给「格式化」——主题里没有开关的选中态样式，靠选中与否区分不出来
        prettyButton.setOnAction(event -> {
            showPretty = !showPretty;
            prettyButton.setText(showPretty ? "查看原始" : "格式化");
            refreshText();
        });

        Region cardGap = new Region();
        HBox.setHgrow(cardGap, Priority.ALWAYS);
        HBox areaBar = new HBox(8, wrap, cardGap, prettyButton);
        areaBar.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(8, area, areaBar);
        card.getStyleClass().add("card");
        VBox.setVgrow(card, Priority.ALWAYS);

        copyButton.getStyleClass().add("ghost-button");
        copyButton.setOnAction(event -> copyText());

        Button close = new Button("关闭");
        close.getStyleClass().add("primary-button");
        close.setDefaultButton(true);
        close.setOnAction(event -> stage.close());

        Region footGap = new Region();
        HBox.setHgrow(footGap, Priority.ALWAYS);
        HBox footer = new HBox(10, copyButton, footGap, close);
        footer.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(12, header, card, footer);
        root.getStyleClass().add("app-root");
        root.setPadding(new Insets(16, 18, 14, 18));

        Scene scene = new Scene(root, 620, 460);
        if (owner != null && owner.getScene() != null) {
            scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                stage.close();
            }
        });

        stage.setScene(scene);
        stage.setTitle("单元格内容");
        stage.setMinWidth(420);
        stage.setMinHeight(280);
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.initModality(Modality.NONE);
        stage.setOnHidden(event -> {
            if (open == this) {
                open = null;
            }
        });
    }

    /**
     * 弹出（或刷新）单元格详情。
     *
     * @param owner      主窗口，用于定位与继承样式表，可为 {@code null}
     * @param column     列名
     * @param rowNumber  表格里的行号（第一列那个数字）
     * @param text       单元格原文
     */
    public static void show(Window owner, String column, String rowNumber, String text) {
        if (open != null && open.stage.getOwner() != owner) {
            // 换了主窗口：旧的那扇已经没有关系了
            open.stage.close();
            open = null;
        }
        if (open == null) {
            open = new CellDetailDialog(owner);
        }
        open.fill(column, rowNumber, text);
        open.stage.show();
        open.stage.toFront();
        open.stage.requestFocus();
    }

    // ---------------------------------------------------------------- 内容

    private void fill(String column, String rowNumber, String text) {
        raw = text == null ? "" : text;
        prettyText = pretty(raw);
        showPretty = prettyText != null;
        boolean canPretty = prettyText != null;
        prettyButton.setVisible(canPretty);
        prettyButton.setManaged(canPretty);
        prettyButton.setText(canPretty ? "查看原始" : "格式化");

        StringBuilder info = new StringBuilder("第 ").append(rowNumber).append(" 行");
        if (column != null && !column.isBlank()) {
            info.append(" · ").append(column);
        }
        info.append(" · ").append(raw.length()).append(" 字符");
        if (raw.length() > MAX_CHARS) {
            info.append("（只显示前 ").append(MAX_CHARS).append(" 字符）");
        }
        meta.setText(info.toString());
        refreshText();
    }

    /** 按当前形态重新铺文本。 */
    private void refreshText() {
        String shown = showPretty && prettyText != null ? prettyText : raw;
        area.setText(clip(shown));
        area.positionCaret(0);
    }

    /** 复制当前看到的内容（格式化后的或原始串），并给出「已复制」反馈。 */
    private void copyText() {
        ClipboardUtil.copy(area.getText());
        copyButton.setText("已复制");
        PauseTransition wait = new PauseTransition(Duration.seconds(1.6));
        wait.setOnFinished(event -> copyButton.setText("复制"));
        wait.play();
    }

    // ---------------------------------------------------------------- 纯文本逻辑

    /** 看着像 JSON 的对象或数组（紧凑串也算）。 */
    static boolean looksLikeJson(String text) {
        if (text == null) {
            return false;
        }
        String trimmed = text.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }

    /** 格式化后的文本；不是合法 JSON 时返回 {@code null}，由调用方决定要不要给按钮。 */
    static String pretty(String text) {
        if (!looksLikeJson(text)) {
            return null;
        }
        try {
            return JsonFormatter.format(text, INDENT);
        } catch (JsonFormatException | RuntimeException e) {
            return null;
        }
    }

    /** 超过 {@link #MAX_CHARS} 时截断，并在末尾说明，避免大文本把布局拖垮。 */
    static String clip(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() <= MAX_CHARS) {
            return text;
        }
        return text.substring(0, MAX_CHARS) + "\n\n… 内容过长，只显示前 " + MAX_CHARS + " 字符";
    }
}
