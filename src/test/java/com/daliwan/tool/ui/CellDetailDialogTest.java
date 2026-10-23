package com.daliwan.tool.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 单元格详情弹窗里的纯文本逻辑：能不能当 JSON 格式化、超长文本怎么截断。
 *
 * <p>只测静态方法，不构造窗口——构造 Stage 需要起 JavaFX 工具包，单测环境里没有。
 */
class CellDetailDialogTest {

    @Test
    @DisplayName("对象与数组的紧凑串算 JSON，其余不算")
    void detectsJsonValues() {
        assertTrue(CellDetailDialog.looksLikeJson("{\"a\":1}"));
        assertTrue(CellDetailDialog.looksLikeJson("[1,2,3]"));
        assertTrue(CellDetailDialog.looksLikeJson("  {\"a\":1}  "), "前后空白不算数");
        assertFalse(CellDetailDialog.looksLikeJson("\"{\\\"a\\\":1}\""), "JSON 字符串是字符串，不是 JSON");
        assertFalse(CellDetailDialog.looksLikeJson("张三"));
        assertFalse(CellDetailDialog.looksLikeJson(""));
        assertFalse(CellDetailDialog.looksLikeJson(null));
    }

    @Test
    @DisplayName("是 JSON 就格式化，不是或解析失败就返回 null")
    void prettifiesOnlyValidJson() {
        String formatted = CellDetailDialog.pretty("{\"a\":[1,2],\"b\":\"x\"}");
        assertTrue(formatted.contains("\n"), "格式化后应当多行");
        assertTrue(formatted.contains("\"a\""));

        assertNull(CellDetailDialog.pretty("张三"), "普通文本没有格式化这一说");
        assertNull(CellDetailDialog.pretty("{a:1}"), "语法错误时返回 null，由界面禁用按钮");
    }

    @Test
    @DisplayName("超长文本截断，并在末尾说明")
    void clipsHugeValues() {
        assertEquals("abc", CellDetailDialog.clip("abc"));
        assertEquals("", CellDetailDialog.clip(null));

        String huge = "x".repeat(CellDetailDialog.MAX_CHARS + 50);
        String shown = CellDetailDialog.clip(huge);
        assertTrue(shown.length() < huge.length(), "应当比原文短");
        assertTrue(shown.endsWith("字符"), "末尾要说明为什么被截断");
        assertTrue(shown.startsWith("x".repeat(200)), "前面完整保留");
    }
}
