package com.daliwan.tool.ui;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 带行号栏与 JSON 语法高亮的代码框。
 *
 * <p>JavaFX 原生 {@code TextArea} 是纯文本控件，无法局部着色，也无法绘制行号，
 * 因此这里使用 RichTextFX 的 {@link CodeArea}。
 *
 * <p>大文件分两种高亮策略：{@value #FULL_HIGHLIGHT_LIMIT} 字符以内整篇算一次；
 * 超过之后只算「看得见的那些行」——一张 1 MB 的 JSON 有四五万行，
 * 整篇着色既慢又没意义，真正被看见的永远只有当前这一屏。滚动停下 60 ms 后重算。
 *
 * <p>另有一条独立规则：**单个段落过长时整段不上色**。接口返回的原始 JSON 常常是
 * 一整行（15 万字符挤在一段里），这时整篇着色会产出几万个样式段，而样式段是切在
 * 段落内部的——RichTextFX 要按这些切点重新布局整段，实测 15 万字符的单一行着色要
 * 将近 10 秒，之后每敲一个字符都要重来一遍，界面等于卡死。行一多、每段一短就没有
 * 这个问题（1.7 万行的文档着色只要 0.5 秒），所以这条规则只在「有几段特别长」时
 * 生效，并通过 {@link #isHighlightPaused()} 告诉界面去提示用户格式化。
 */
public class JsonCodeArea extends StackPane {

    /** 超过该长度改为「只高亮可见区域」。 */
    private static final int FULL_HIGHLIGHT_LIMIT = 200_000;

    /**
     * 单个段落的字符上限，超过就不给这一段上色。
     *
     * <p>8 000 是按实测定的：单一行 8 000 字符着色约 0.2 秒（一次性的，可接受），
     * 再往上成本涨得很快（2 万→0.8 秒，15 万→9.7 秒）。
     */
    private static final int LONG_PARAGRAPH_LIMIT = 8_000;

    /** 超过该长度才去逐段检查有没有「特别长的行」，省掉小文本的遍历。 */
    private static final int LONG_PARAGRAPH_CHECK_THRESHOLD = 20_000;

    /** 可见区域上下各多算的段落数，滚一屏之内不用重算。 */
    private static final int VIEWPORT_BUFFER = 150;

    /** 滚动停止后多久补高亮，避开滚动过程中的连续重算。 */
    private static final Duration VIEWPORT_DELAY = Duration.millis(60);

    /**
     * 匹配顺序即优先级：键必须排在普通字符串之前，
     * 否则带冒号的键会被当成字符串。
     */
    private static final Pattern JSON_PATTERN = Pattern.compile(
            "(?<KEY>\"(?:\\\\.|[^\"\\\\])*\"(?=\\s*:))"
                    + "|(?<STRING>\"(?:\\\\.|[^\"\\\\])*\")"
                    + "|(?<NUMBER>-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)"
                    + "|(?<LITERAL>\\btrue\\b|\\bfalse\\b|\\bnull\\b)"
                    + "|(?<PUNCT>[{}\\[\\],:])");

    private final CodeArea codeArea = new CodeArea();

    private final PauseTransition viewportDelay = new PauseTransition(VIEWPORT_DELAY);

    /** 已高亮的段落范围，范围没变就不重复算。 */
    private int highlightedFrom = -1;
    private int highlightedTo = -1;

    /** 是否因为「有段落太长」而停掉了着色，界面据此提示用户。 */
    private boolean highlightPaused;

    public JsonCodeArea(boolean editable) {
        getStyleClass().add("code-container");

        codeArea.setEditable(editable);
        codeArea.getStyleClass().add(editable ? "json-code-area" : "json-code-area-readonly");
        LineNumberGutter.install(codeArea);

        getChildren().add(new VirtualizedScrollPane<>(codeArea));

        // 万行文档里滑块会被压到 2~3 px，看着像没有滚动条，这里给它兜一个最小长度
        ScrollBars.installMinThumb(this);

        codeArea.multiPlainChanges()
                .successionEnds(java.time.Duration.ofMillis(120))
                .subscribe(ignore -> applyHighlight());

        viewportDelay.setOnFinished(event -> highlightViewport());
        // 滚动与视口尺寸变化都可能让「看不见的那几行」变成看得见
        codeArea.estimatedScrollYProperty().addListener((observable, old, now) -> scheduleViewportHighlight());
        codeArea.heightProperty().addListener((observable, old, now) -> scheduleViewportHighlight());
    }

    /** 暴露底层控件，便于设置占位提示等细节。 */
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

    public void requestFocusOnArea() {
        codeArea.requestFocus();
    }

    /** 文本发生变化时回调。 */
    public void setOnTextChanged(Runnable action) {
        codeArea.multiPlainChanges().subscribe(ignore -> action.run());
    }

    /**
     * 因「段落太长」而停掉着色时为 {@code true}。
     *
     * <p>界面拿它去提示用户：内容是一整行，格式化之后就会恢复着色。
     */
    public boolean isHighlightPaused() {
        return highlightPaused;
    }

    /** 最长那一行的字符数，提示里用来说明「这一行有多长」。 */
    public int longestParagraphLength() {
        int longest = 0;
        for (int i = 0; i < codeArea.getParagraphs().size(); i++) {
            int length = codeArea.getParagraph(i).length();
            if (length > longest) {
                longest = length;
            }
        }
        return longest;
    }

    // ---------------------------------------------------------------- 高亮

    private void applyHighlight() {
        String text = codeArea.getText();
        highlightedFrom = -1;
        highlightedTo = -1;
        if (text.isEmpty()) {
            highlightPaused = false;
            return;
        }
        if (hasTooLongParagraph(text)) {
            // 一整行的文档：着色会让界面卡死，这里整段放弃着色并清空残留样式
            highlightPaused = true;
            codeArea.clearStyle(0, codeArea.getLength());
            return;
        }
        highlightPaused = false;
        if (text.length() > FULL_HIGHLIGHT_LIMIT) {
            // 大文档：先清掉整篇样式，再等高亮可见的那一段
            scheduleViewportHighlight();
            return;
        }
        codeArea.setStyleSpans(0, computeHighlighting(text));
    }

    /**
     * 有没有「长到不能着色」的段落。
     *
     * <p>只对超过阈值的文本逐段检查：短文本一行再长也没到 8 000 的量级，
     * 不必为它遍历段落。
     */
    private boolean hasTooLongParagraph(String text) {
        if (text.length() < LONG_PARAGRAPH_CHECK_THRESHOLD) {
            return false;
        }
        int count = codeArea.getParagraphs().size();
        for (int i = 0; i < count; i++) {
            if (codeArea.getParagraph(i).length() > LONG_PARAGRAPH_LIMIT) {
                return true;
            }
        }
        return false;
    }

    /** 节流：滚动过程中不断触发，等停下来再算。 */
    private void scheduleViewportHighlight() {
        if (codeArea.getLength() <= FULL_HIGHLIGHT_LIMIT) {
            return;
        }
        viewportDelay.playFromStart();
    }

    /**
     * 只给可见区域（上下各留一段缓冲）上色。
     *
     * <p>用段落边界切子串，不会把字符串字面量切成两半——JSON 的字符串不跨行。
     */
    private void highlightViewport() {
        int total = codeArea.getParagraphs().size();
        if (total == 0) {
            return;
        }
        int firstVisible;
        int lastVisible;
        try {
            int visible = codeArea.getVisibleParagraphs().size();
            if (visible == 0) {
                // 还没布局完，等下一帧再试
                Platform.runLater(this::highlightViewport);
                return;
            }
            firstVisible = codeArea.visibleParToAllParIndex(0);
            lastVisible = codeArea.visibleParToAllParIndex(visible - 1);
        } catch (RuntimeException notReady) {
            return;
        }
        int from = Math.max(0, firstVisible - VIEWPORT_BUFFER);
        int to = Math.min(total - 1, lastVisible + VIEWPORT_BUFFER);
        if (from == highlightedFrom && to == highlightedTo) {
            return;
        }
        int start = codeArea.getAbsolutePosition(from, 0);
        int end = codeArea.getAbsolutePosition(to, 0) + codeArea.getParagraph(to).length();
        end = Math.min(end, codeArea.getLength());
        if (end <= start) {
            return;
        }
        // 可见区域里也可能有超长行（比如一整行的超大文档），同样不能着色
        for (int i = from; i <= to; i++) {
            if (codeArea.getParagraph(i).length() > LONG_PARAGRAPH_LIMIT) {
                highlightPaused = true;
                codeArea.clearStyle(start, end);
                return;
            }
        }
        String slice;
        try {
            slice = codeArea.getText(start, end);
        } catch (RuntimeException error) {
            return;
        }
        highlightedFrom = from;
        highlightedTo = to;
        highlightPaused = false;
        codeArea.setStyleSpans(start, computeHighlighting(slice));
    }

    private static StyleSpans<Collection<String>> computeHighlighting(String text) {
        StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>();
        Matcher matcher = JSON_PATTERN.matcher(text);
        int lastEnd = 0;
        while (matcher.find()) {
            String styleClass = styleClassOf(matcher);
            int gap = matcher.start() - lastEnd;
            if (gap > 0) {
                builder.add(Collections.emptyList(), gap);
            }
            builder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastEnd = matcher.end();
        }
        int tail = text.length() - lastEnd;
        if (tail > 0) {
            builder.add(Collections.emptyList(), tail);
        }
        return builder.create();
    }

    private static String styleClassOf(Matcher matcher) {
        if (matcher.group("KEY") != null) {
            return "json-key";
        }
        if (matcher.group("STRING") != null) {
            return "json-string";
        }
        if (matcher.group("NUMBER") != null) {
            return "json-number";
        }
        if (matcher.group("LITERAL") != null) {
            return "json-literal";
        }
        return "json-punct";
    }
}
