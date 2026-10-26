package com.daliwan.tool.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonUnescapeTest {

    @Test
    @DisplayName("只剩内部转义的数组还原成真 JSON")
    void unwrapsEscapedArray() {
        // 文本里的引号带着反斜杠：接口日志与字符串字段里最常见的样子
        String escaped = "[{\\\"id\\\": \\\"654\\\", \\\"attributeName\\\": \\\"材质\\\"}]";

        JsonUnescape.Result result = JsonUnescape.unescape(escaped);

        assertEquals("[{\"id\": \"654\", \"attributeName\": \"材质\"}]", result.text());
        assertTrue(result.changed());
    }

    @Test
    @DisplayName("外面裹一层引号时，连引号一起剥掉")
    void stripsWrappingQuotes() {
        String escaped = "\"[{\\\"id\\\":1}]\"";

        assertEquals("[{\"id\":1}]", JsonUnescape.apply(escaped));
        assertEquals(1, JsonUnescape.unescape(escaped).levels());
    }

    @Test
    @DisplayName("日志里常见的两层包裹一层层剥开")
    void stripsSeveralLayers() {
        String twice = "\"\\\"[{\\\\\\\"id\\\\\\\":1}]\\\"\"";

        JsonUnescape.Result result = JsonUnescape.unescape(twice);

        assertEquals("[{\"id\":1}]", result.text());
        assertTrue(result.levels() >= 2, "剥了两层，实际 " + result.levels());
    }

    @Test
    @DisplayName("转义的换行、制表符、unicode 一并还原")
    void decodesEscapeSequences() {
        assertEquals("a\nb\tc", JsonUnescape.apply("a\\nb\\tc"));
        assertEquals("斜杠/不/改", JsonUnescape.apply("斜杠\\/不\\/改"));
        assertEquals("张三", JsonUnescape.apply("\\u5f20\\u4e09"), "\\uXXXX 解成字符");
    }

    @Test
    @DisplayName("本来就是合法 JSON 的不动，避免把正常内容改坏")
    void leavesValidJsonAlone() {
        String plain = "{\"a\":1,\"b\":[true,null]}";
        JsonUnescape.Result plainResult = JsonUnescape.unescape(plain);
        assertEquals(plain, plainResult.text());
        assertFalse(plainResult.changed());

        // 合法 JSON 的字符串值里本来就可以有 \" ，一刀切替换会把它变成非法内容
        String withQuote = "{\"a\":\"he said \\\"hi\\\"\"}";
        assertEquals(withQuote, JsonUnescape.apply(withQuote));
    }

    @Test
    @DisplayName("换不出来的内容原样返回，不猜")
    void keepsUnknownEscapes() {
        String path = "C:\\users\\docs";
        assertEquals(path, JsonUnescape.apply(path), "\\u 后面不是十六进制，按原样留着");

        assertEquals("a\\qb", JsonUnescape.apply("a\\qb"), "不认得的转义序列不动");
    }

    @Test
    @DisplayName("空内容与 null 不报错")
    void handlesEmptyInput() {
        assertEquals("", JsonUnescape.apply(""));
        assertEquals("", JsonUnescape.apply("   "));
        assertEquals("", JsonUnescape.apply(null));
        assertFalse(JsonUnescape.unescape(null).changed());
    }

    @Test
    @DisplayName("认得出「是不是一整个字符串字面量」")
    void detectsQuotedLiteral() {
        assertTrue(JsonUnescape.isQuotedLiteral("\"abc\""));
        assertTrue(JsonUnescape.isQuotedLiteral("\"a\\\"b\""));
        assertFalse(JsonUnescape.isQuotedLiteral("abc"), "没有引号");
        assertFalse(JsonUnescape.isQuotedLiteral("\"a\"+\"b\""), "中间有没转义的引号");
        assertFalse(JsonUnescape.isQuotedLiteral("\"abc\\\""), "结尾引号本身被转义了，字面量没闭合");
        assertFalse(JsonUnescape.isQuotedLiteral("\""), "只有一个引号");
    }
}
