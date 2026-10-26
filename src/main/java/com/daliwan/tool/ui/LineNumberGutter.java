package com.daliwan.tool.ui;

import com.daliwan.tool.util.LineNumbers;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;
import org.fxmisc.richtext.CodeArea;
import org.reactfx.collection.LiveList;
import org.reactfx.value.Val;

import java.util.List;
import java.util.function.IntFunction;

/**
 * 代码框左侧的行号栏。
 *
 * <p>本来直接用 RichTextFX 的 {@code LineNumberFactory.get(area)}，换掉的原因只有一个：
 * 它给行号写死了 {@code Font.font("monospace", 斜体, 13)}。{@code monospace} 是
 * <b>逻辑字体名</b>，JavaFX 会先把它翻译成 {@code Monospaced}，再按每台机器的字体配置
 * 决定最终落到哪个真实字体——系统语言、字体替换表、装没装某套字体都会改变结果。
 * 大多数机器落到 Courier New 正常显示，少数机器落到的字体不是我们想要的那个，
 * 行号就变成方块或者怪字符，而同一份程序在另一台机器上又完全正常。
 *
 * <p>这里改成自己画行号，字体只认「系统里确实存在」的名字：逐个候选去问 JavaFX，
 * 名字问不回来（说明这台机器没有）就换下一个，全都没有就退回 JavaFX 默认字体——
 * 那是系统字体，数字字形一定有。顺带把斜体去掉：行号不需要强调。
 *
 * <p>字体用 inline 样式写在节点上，而不是写进 {@code app.css}：
 * 样式表里给 {@code .lineno} 写的字体族同样要靠机器解析，
 * inline 样式优先级最高，不会被 {@code .root} 的继承字体值盖掉。
 */
public final class LineNumberGutter implements IntFunction<Node> {

    /**
     * 候选等宽字体，按优先级排列。
     *
     * <p>必须是真实字体名（{@code Consolas} 这种），不能用 {@code monospace} 这类逻辑名。
     */
    private static final List<String> MONO_FAMILIES =
            List.of("Cascadia Mono", "Consolas", "Courier New", "Lucida Console");

    /** 行号字号；比正文小一号，让视线先落在代码上。 */
    private static final double SIZE = 11;

    /** 解析结果缓存：字体探测要走一次系统字体表，没必要每行都做。 */
    private static volatile String resolvedFamily;

    private final Val<Integer> paragraphCount;

    private LineNumberGutter(CodeArea area) {
        paragraphCount = LiveList.sizeOf(area.getParagraphs());
    }

    /** 给代码框装上这套行号。 */
    public static void install(CodeArea area) {
        area.setParagraphGraphicFactory(new LineNumberGutter(area));
    }

    @Override
    public Node apply(int index) {
        Label lineNo = new Label();
        lineNo.getStyleClass().add("lineno");
        lineNo.setAlignment(Pos.TOP_RIGHT);
        lineNo.setMinWidth(Region.USE_PREF_SIZE);
        lineNo.setStyle(fontStyle());

        Val<String> text = paragraphCount.map(count -> LineNumbers.label(index + 1, count));
        // conditionOnShowing：行号滚出视野后不再监听段落数，避免被整份文档拖住
        lineNo.textProperty().bind(text.conditionOnShowing(lineNo));
        return lineNo;
    }

    private static String fontStyle() {
        String family = monospaceFamily();
        return "-fx-font-family: \"" + family + "\";"
                + " -fx-font-size: " + SIZE + "px;"
                + " -fx-font-style: normal;";
    }

    /**
     * 挑一台机器上真实存在的等宽字体。
     *
     * <p>{@code Font.font(name)} 找不到字体时会退回默认字体，
     * 这时 {@code getFamily()} 返回的是 {@code System} 而不是我们问的那个名字——
     * 用这个差异就能判断「这台机器到底有没有这套字体」。
     */
    static String monospaceFamily() {
        String cached = resolvedFamily;
        if (cached != null) {
            return cached;
        }
        String resolved = null;
        for (String family : MONO_FAMILIES) {
            Font font = Font.font(family, SIZE);
            if (font != null && font.getFamily().equalsIgnoreCase(family)) {
                resolved = family;
                break;
            }
        }
        if (resolved == null) {
            resolved = Font.getDefault().getFamily();
        }
        resolvedFamily = resolved;
        return resolved;
    }
}
