package com.daliwan.tool.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextDiffTest {

    /** 从比对结果重建左侧文本，用于校验不变量。 */
    private List<String> rebuildLeft(List<TextDiff.Line> lines) {
        List<String> out = new ArrayList<>();
        for (TextDiff.Line line : lines) {
            if (line.type() != TextDiff.Type.INSERT) {
                out.add(line.text());
            }
        }
        return out;
    }

    private List<String> rebuildRight(List<TextDiff.Line> lines) {
        List<String> out = new ArrayList<>();
        for (TextDiff.Line line : lines) {
            if (line.type() != TextDiff.Type.DELETE) {
                out.add(line.text());
            }
        }
        return out;
    }

    @Test
    @DisplayName("完全相同的文本全部为 EQUAL")
    void identicalTextHasNoChanges() {
        List<TextDiff.Line> lines = TextDiff.compare("a\nb\nc", "a\nb\nc");
        assertEquals(3, lines.size());
        assertTrue(lines.stream().allMatch(l -> l.type() == TextDiff.Type.EQUAL));

        TextDiff.Stats stats = TextDiff.stats(lines);
        assertEquals(0, stats.added());
        assertEquals(0, stats.deleted());
        assertEquals(3, stats.unchanged());
    }

    @Test
    @DisplayName("新增行标记为 INSERT")
    void detectsInsertion() {
        List<TextDiff.Line> lines = TextDiff.compare("a\nc", "a\nb\nc");
        List<TextDiff.Line> inserted = lines.stream()
                .filter(l -> l.type() == TextDiff.Type.INSERT).toList();
        assertEquals(1, inserted.size());
        assertEquals("b", inserted.get(0).text());
        assertEquals(2, inserted.get(0).rightNumber());
        assertEquals(0, inserted.get(0).leftNumber());
    }

    @Test
    @DisplayName("删除行标记为 DELETE")
    void detectsDeletion() {
        List<TextDiff.Line> lines = TextDiff.compare("a\nb\nc", "a\nc");
        List<TextDiff.Line> deleted = lines.stream()
                .filter(l -> l.type() == TextDiff.Type.DELETE).toList();
        assertEquals(1, deleted.size());
        assertEquals("b", deleted.get(0).text());
        assertEquals(2, deleted.get(0).leftNumber());
        assertEquals(0, deleted.get(0).rightNumber());
    }

    @Test
    @DisplayName("替换表现为一删一增")
    void detectsReplacement() {
        List<TextDiff.Line> lines = TextDiff.compare("a\nold\nc", "a\nnew\nc");
        TextDiff.Stats stats = TextDiff.stats(lines);
        assertEquals(1, stats.added());
        assertEquals(1, stats.deleted());
        assertEquals(2, stats.unchanged());
    }

    @Test
    @DisplayName("空文本与空文本无差异")
    void handlesBothEmpty() {
        assertTrue(TextDiff.compare("", "").isEmpty());
        assertTrue(TextDiff.compare(null, null).isEmpty());
    }

    @Test
    @DisplayName("一侧为空时全部为新增或删除")
    void handlesOneSideEmpty() {
        TextDiff.Stats added = TextDiff.stats(TextDiff.compare("", "a\nb"));
        assertEquals(2, added.added());
        assertEquals(0, added.deleted());

        TextDiff.Stats removed = TextDiff.stats(TextDiff.compare("a\nb", ""));
        assertEquals(2, removed.deleted());
        assertEquals(0, removed.added());
    }

    @Test
    @DisplayName("中文与 CRLF 换行正确处理")
    void handlesChineseAndCrlf() {
        List<TextDiff.Line> lines = TextDiff.compare("第一行\r\n第二行", "第一行\r\n第二行\r\n第三行");
        assertEquals(1, TextDiff.stats(lines).added());
        assertEquals("第三行", lines.get(lines.size() - 1).text());
    }

    @Test
    @DisplayName("行号连续且与内容对应")
    void numbersLinesCorrectly() {
        List<TextDiff.Line> lines = TextDiff.compare("a\nb\nc", "a\nx\nc");
        int expectedLeft = 1;
        int expectedRight = 1;
        for (TextDiff.Line line : lines) {
            if (line.type() != TextDiff.Type.INSERT) {
                assertEquals(expectedLeft++, line.leftNumber(), "左侧行号：" + line.text());
            }
            if (line.type() != TextDiff.Type.DELETE) {
                assertEquals(expectedRight++, line.rightNumber(), "右侧行号：" + line.text());
            }
        }
    }

    @Test
    @DisplayName("任意文本都能重建出左右两侧原文")
    void alwaysRebuildsBothSides() {
        Random random = new Random(42);
        String[] pool = {"a", "b", "c", "中文", "", "x y"};
        for (int round = 0; round < 200; round++) {
            String left = randomText(random, pool);
            String right = randomText(random, pool);

            List<TextDiff.Line> lines = TextDiff.compare(left, right);

            assertEquals(TextDiff.splitLines(left), rebuildLeft(lines), "左侧重建失败：" + left + " / " + right);
            assertEquals(TextDiff.splitLines(right), rebuildRight(lines), "右侧重建失败：" + left + " / " + right);
        }
    }

    private String randomText(Random random, String[] pool) {
        int count = random.nextInt(6);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                out.append('\n');
            }
            out.append(pool[random.nextInt(pool.length)]);
        }
        return out.toString();
    }

    @Test
    @DisplayName("导出文本带 +/- 前缀")
    void exportsWithMarkers() {
        String text = TextDiff.toText(TextDiff.compare("a\nb", "a\nc"));
        assertTrue(text.contains("- b"));
        assertTrue(text.contains("+ c"));
        assertTrue(text.contains("  a"));
    }

    @Test
    @DisplayName("连续的删除与新增配成同一行的修改对")
    void alignPairsDeleteWithInsert() {
        List<TextDiff.Row> rows = TextDiff.align(TextDiff.compare("a\nold\nc", "a\nnew\nc"));
        assertEquals(3, rows.size());
        assertEquals(TextDiff.Type.EQUAL, rows.get(0).left().type());
        assertEquals(TextDiff.Type.DELETE, rows.get(1).left().type());
        assertEquals(TextDiff.Type.INSERT, rows.get(1).right().type());
        assertEquals("old", rows.get(1).left().text());
        assertEquals("new", rows.get(1).right().text());
        assertEquals(TextDiff.Type.EQUAL, rows.get(2).left().type());
    }

    @Test
    @DisplayName("单侧多余行落在补位行上")
    void alignPadsUnpairedLines() {
        List<TextDiff.Row> rows = TextDiff.align(TextDiff.compare("a\nb", "a"));
        assertEquals(2, rows.size());
        assertEquals("b", rows.get(1).left().text());
        assertEquals(null, rows.get(1).right());

        rows = TextDiff.align(TextDiff.compare("a", "a\nx"));
        assertEquals(2, rows.size());
        assertEquals(null, rows.get(1).left());
        assertEquals("x", rows.get(1).right().text());
    }

    @Test
    @DisplayName("多删除对少新增时按序配对，多余删除补位")
    void alignPairsInOrder() {
        List<TextDiff.Row> rows = TextDiff.align(
                TextDiff.compare("a\nx\ny\nz", "a\np"));
        // EQUAL a，然后 x/y/z 三个删除对 p 一个新增：x-p 配对，y、z 补位
        assertEquals(4, rows.size());
        assertEquals(TextDiff.Type.DELETE, rows.get(1).left().type());
        assertEquals(TextDiff.Type.INSERT, rows.get(1).right().type());
        assertEquals("x", rows.get(1).left().text());
        assertEquals("p", rows.get(1).right().text());
        assertEquals(null, rows.get(2).right());
        assertEquals(null, rows.get(3).right());
    }

    @Test
    @DisplayName("数量不同时按相似度配对，而不是按出现顺序硬凑")
    void alignPairsBySimilarity() {
        // 删除 1 行（throw），新增 2 行（先加了埋点，再改了 throw 的参数）：
        // throw 行应越过邻近的 metrics 行，与真正对应的新增 throw 行配对
        String left = "A\nif (order == null) {\n  throw new OrderNotFoundException(id);\n}\n";
        String right = "A\nif (order == null) {\n  metrics.increment(\"order.miss\");\n"
                + "  throw new OrderNotFoundException(\"order \" + id + \" not found\");\n}\n";
        List<TextDiff.Row> rows = TextDiff.align(TextDiff.compare(left, right));

        // 行序：EQUAL A、EQUAL if、(补位, metrics)、(throw, throw)、EQUAL }
        assertEquals(5, rows.size());
        assertEquals(null, rows.get(2).left());
        assertEquals("  metrics.increment(\"order.miss\");", rows.get(2).right().text());
        assertEquals(TextDiff.Type.DELETE, rows.get(3).left().type());
        assertEquals(TextDiff.Type.INSERT, rows.get(3).right().type());
        assertEquals("  throw new OrderNotFoundException(id);", rows.get(3).left().text());
        assertEquals("  throw new OrderNotFoundException(\"order \" + id + \" not found\");",
                rows.get(3).right().text());
    }

    @Test
    @DisplayName("行内差异给出前后缀与高亮片段长度")
    void inlineDiffLocatesChangedSpan() {
        TextDiff.InlineDiff diff = TextDiff.inlineDiff("hello world", "hello there");
        assertEquals(6, diff.prefixLen());
        assertEquals(5, diff.leftMidLen());
        assertEquals(5, diff.rightMidLen());
        assertEquals(0, diff.suffixLen());

        // 共同后缀：结尾的 1 相同
        diff = TextDiff.inlineDiff("value1", "amount1");
        assertEquals(0, diff.prefixLen());
        assertEquals(5, diff.leftMidLen());
        assertEquals(6, diff.rightMidLen());
        assertEquals(1, diff.suffixLen());

        diff = TextDiff.inlineDiff("same", "same");
        assertEquals(4, diff.prefixLen());
        assertEquals(0, diff.leftMidLen());
        assertEquals(0, diff.rightMidLen());
        assertEquals(0, diff.suffixLen());
    }

    @Test
    @DisplayName("忽略空白模式下仅空白不同视为一致")
    void ignoreWhitespaceOption() {
        List<TextDiff.Line> lines = TextDiff.compare("a  b\n c ", "ab\nc", true);
        assertTrue(lines.stream().allMatch(l -> l.type() == TextDiff.Type.EQUAL));

        lines = TextDiff.compare("a  b\n c ", "ab\nc", false);
        assertTrue(lines.stream().anyMatch(l -> l.type() != TextDiff.Type.EQUAL));
    }
}
