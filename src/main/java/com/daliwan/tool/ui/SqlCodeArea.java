package com.daliwan.tool.ui;

import com.daliwan.tool.core.sql.SqlHighlighter;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 只读的 SQL 结果框：行号 + 语法着色。
 *
 * <p>原生 {@code TextArea} 只能整体一种颜色，所以换用 RichTextFX 的 {@link CodeArea}；
 * 具体哪一段算什么由 {@link SqlHighlighter} 决定，这里只负责把类别翻译成样式类，
 * 颜色写在 CSS 里，跟随明暗主题变化。
 */
public class SqlCodeArea extends StackPane {

    /** 超过该长度不再着色：全量扫描上百万字符会让粘贴后卡一下。 */
    private static final int HIGHLIGHT_LIMIT = 300_000;

    private final CodeArea codeArea = new CodeArea();

    public SqlCodeArea(String placeholder) {
        getStyleClass().add("code-container");

        codeArea.setEditable(false);
        codeArea.getStyleClass().add("sql-code-area");
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        // 结果只读，光标没意义，但保留选中与复制
        codeArea.setFocusTraversable(true);

        Label hint = new Label(placeholder);
        hint.getStyleClass().addAll("hint", "code-placeholder");
        codeArea.setPlaceholder(hint);

        getChildren().add(new VirtualizedScrollPane<>(codeArea));
    }

    /** 暴露底层控件，便于外部做少量细节调整。 */
    public CodeArea codeArea() {
        return codeArea;
    }

    public String getText() {
        return codeArea.getText();
    }

    public void setText(String text) {
        String value = text == null ? "" : text;
        if (value.equals(codeArea.getText())) {
            return;
        }
        codeArea.replaceText(value);
        applyHighlight();
    }

    public void clear() {
        codeArea.clear();
        applyHighlight();
    }

    // ---------------------------------------------------------------- 着色

    private void applyHighlight() {
        String text = codeArea.getText();
        if (text.isEmpty()) {
            return;
        }
        if (text.length() > HIGHLIGHT_LIMIT) {
            codeArea.setStyleSpans(0, plainSpans(text.length()));
            return;
        }
        codeArea.setStyleSpans(0, computeHighlighting(text));
    }

    private static StyleSpans<Collection<String>> computeHighlighting(String text) {
        StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>();
        int lastEnd = 0;
        for (SqlHighlighter.Token token : SqlHighlighter.scan(text)) {
            int gap = token.start() - lastEnd;
            if (gap > 0) {
                builder.add(Collections.emptyList(), gap);
            }
            builder.add(Collections.singleton(styleClassOf(token.kind())), token.length());
            lastEnd = token.end();
        }
        int tail = text.length() - lastEnd;
        if (tail > 0) {
            builder.add(Collections.emptyList(), tail);
        }
        return builder.create();
    }

    private static String styleClassOf(SqlHighlighter.Kind kind) {
        return switch (kind) {
            case KEYWORD -> "sql-kw";
            case FUNCTION -> "sql-func";
            case STRING -> "sql-string";
            case NUMBER -> "sql-number";
            case LITERAL -> "sql-literal";
            case COMMENT -> "sql-comment";
            case QUOTED -> "sql-quoted";
            case PARAM -> "sql-param";
            case OPERATOR -> "sql-op";
            case PLAIN -> "sql-plain";
        };
    }

    private static StyleSpans<Collection<String>> plainSpans(int length) {
        StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>();
        builder.add(Collections.emptyList(), length);
        return builder.create();
    }

    /** 供界面自检工具确认着色生效（返回每个 token 的类别与文字）。 */
    public List<String> describeStyles() {
        return SqlHighlighter.scan(getText()).stream()
                .map(t -> t.kind() + ":" + getText().substring(t.start(), t.end()))
                .toList();
    }
}
