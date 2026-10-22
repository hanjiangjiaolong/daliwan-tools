package com.daliwan.tool.ui;

import com.daliwan.tool.core.TextDiff;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * 左右分屏差异视图（IDEA git 比对风格）。
 *
 * <p>两栏行行对齐：相等行同行展示，连续的删除/新增配成同一行的「修改对」，
 * 行内差异片段再做字符级高亮；单侧缺失的行用补位行占位，因此左右永远等高同步。
 * 行背景用强对比色块（删除红 / 新增绿 / 补位灰），配合 {@code − +} 符号，
 * 色觉受限也能区分。整块放在一个滚动条里，纵向横向天然同步。
 */
public class DiffView extends Region {

    /** 单侧渲染的行数上限，超出只渲染前若干行，避免超长文本把界面拖垮。 */
    private static final int MAX_ROWS = 4000;

    private final VBox leftColumn = new VBox();
    private final VBox rightColumn = new VBox();
    private final Label truncationNote = new Label();
    private final VBox body = new VBox();
    private final ScrollPane scroller;

    public DiffView() {
        getStyleClass().add("diff-view");

        leftColumn.getStyleClass().add("diff-column");
        rightColumn.getStyleClass().add("diff-column");

        Region divider = new Region();
        divider.getStyleClass().add("diff-divider");

        HBox columns = new HBox(leftColumn, divider, rightColumn);
        columns.setFillHeight(true);
        HBox.setHgrow(leftColumn, Priority.ALWAYS);
        HBox.setHgrow(rightColumn, Priority.ALWAYS);
        divider.setPrefWidth(1);

        truncationNote.getStyleClass().add("hint");
        truncationNote.setPadding(new Insets(6, 10, 0, 10));

        body.getChildren().add(columns);
        scroller = new ScrollPane(body);
        // 不做宽度压缩：长行保持完整，交给横向滚动条；行背景宽度在 layoutChildren 里补齐
        scroller.setFitToWidth(false);
        scroller.setFitToHeight(false);
        scroller.getStyleClass().add("diff-scroller");
        getChildren().add(scroller);
    }

    @Override
    protected void layoutChildren() {
        scroller.resizeRelocate(0, 0, getWidth(), getHeight());
        // 内容比可视区窄时把行背景拉满，宽时按内容自然撑开触发横向滚动
        double viewport = getWidth();
        Node vbar = scroller.lookup(".scroll-bar:vertical");
        if (vbar instanceof ScrollBar bar && bar.isVisible()) {
            viewport -= bar.getWidth();
        }
        body.setMinWidth(Math.max(0, viewport - 2));
    }

    @Override
    protected double computePrefWidth(double height) {
        return 640;
    }

    @Override
    protected double computePrefHeight(double width) {
        return 320;
    }

    /** 渲染比对结果。 */
    public void show(List<TextDiff.Line> lines) {
        leftColumn.getChildren().clear();
        rightColumn.getChildren().clear();
        body.getChildren().remove(truncationNote);

        if (lines.isEmpty()) {
            return;
        }
        List<TextDiff.Row> rows = TextDiff.align(lines);
        boolean truncated = rows.size() > MAX_ROWS;
        List<TextDiff.Row> visible = truncated ? rows.subList(0, MAX_ROWS) : rows;

        for (TextDiff.Row row : visible) {
            boolean paired = row.left() != null && row.right() != null
                    && row.left().type() == TextDiff.Type.DELETE
                    && row.right().type() == TextDiff.Type.INSERT;
            TextDiff.InlineDiff inline = paired
                    ? TextDiff.inlineDiff(row.left().text(), row.right().text())
                    : null;

            leftColumn.getChildren().add(renderSide(row.left(), true, inline));
            rightColumn.getChildren().add(renderSide(row.right(), false, inline));
        }
        if (truncated) {
            truncationNote.setText("结果过长（" + rows.size() + " 行），仅显示前 " + MAX_ROWS + " 行");
            body.getChildren().add(truncationNote);
        }
    }

    /** 清空视图。 */
    public void clear() {
        leftColumn.getChildren().clear();
        rightColumn.getChildren().clear();
        body.getChildren().remove(truncationNote);
    }

    // ---------------------------------------------------------------- 渲染

    /** 渲染一侧的一行；{@code line} 为 null 表示补位行。 */
    private HBox renderSide(TextDiff.Line line, boolean left, TextDiff.InlineDiff inline) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinWidth(Region.USE_PREF_SIZE);
        row.getStyleClass().addAll("diff-row", sideClass(line, left));

        Label number = new Label(line == null ? "" : line.sideNumber(left));
        number.getStyleClass().add("diff-line-no");
        number.setPrefWidth(48);
        number.setMinWidth(Region.USE_PREF_SIZE);
        number.setTextOverrun(OverrunStyle.CLIP);

        Label marker = new Label(line == null ? "" : markerOf(line, left));
        marker.getStyleClass().add("diff-marker");
        marker.setPrefWidth(16);
        marker.setMinWidth(Region.USE_PREF_SIZE);
        marker.setTextOverrun(OverrunStyle.CLIP);

        row.getChildren().addAll(number, marker);
        if (line != null) {
            row.getChildren().add(renderContent(line.text(), left, inline));
        }
        return row;
    }

    /** 行内容：修改对按行内差异拆成三段，中间段加深色高亮。 */
    private HBox renderContent(String text, boolean left, TextDiff.InlineDiff inline) {
        HBox box = new HBox();
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMinWidth(Region.USE_PREF_SIZE);
        box.getStyleClass().add("diff-text");
        if (inline == null) {
            box.getChildren().add(segment(text, "diff-seg-plain"));
            return box;
        }
        int prefix = inline.prefixLen();
        int mid = left ? inline.leftMidLen() : inline.rightMidLen();
        int suffix = inline.suffixLen();
        if (prefix > 0) {
            box.getChildren().add(segment(text.substring(0, prefix), "diff-seg-plain"));
        }
        if (mid > 0) {
            box.getChildren().add(segment(
                    text.substring(prefix, prefix + mid),
                    left ? "diff-seg-del" : "diff-seg-ins"));
        }
        if (suffix > 0) {
            box.getChildren().add(segment(
                    text.substring(text.length() - suffix), "diff-seg-plain"));
        }
        return box;
    }

    private Label segment(String text, String styleClass) {
        Label label = new Label(text.isEmpty() ? " " : text);
        label.getStyleClass().add(styleClass);
        // min=pref：HBox 空间不足时宁可溢出交给横向滚动，也不把文本压成省略号
        label.setMinWidth(Region.USE_PREF_SIZE);
        label.setTextOverrun(OverrunStyle.CLIP);
        return label;
    }

    private static String sideClass(TextDiff.Line line, boolean left) {
        if (line == null) {
            return "diff-row-filler";
        }
        return switch (line.type()) {
            case DELETE -> left ? "diff-row-del" : "diff-row-filler";
            case INSERT -> left ? "diff-row-filler" : "diff-row-ins";
            case EQUAL -> "diff-row-equal";
        };
    }

    private static String markerOf(TextDiff.Line line, boolean left) {
        return switch (line.type()) {
            case DELETE -> left ? "\u2212" : "";  // −
            case INSERT -> left ? "" : "+";
            case EQUAL -> "";
        };
    }
}
