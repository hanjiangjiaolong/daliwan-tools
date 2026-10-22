package com.daliwan.tool.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonFormatterTest {

    @Test
    @DisplayName("格式化嵌套结构")
    void formatsNestedStructure() throws Exception {
        String input = "{\"a\":1,\"b\":[2,3]}";
        String expected = """
                {
                  "a": 1,
                  "b": [
                    2,
                    3
                  ]
                }""";
        assertEquals(expected, JsonFormatter.format(input, IndentStyle.TWO_SPACES));
    }

    @Test
    @DisplayName("4 空格缩进")
    void formatsWithFourSpaces() throws Exception {
        assertEquals("{\n    \"a\": 1\n}", JsonFormatter.format("{\"a\":1}", IndentStyle.FOUR_SPACES));
    }

    @Test
    @DisplayName("空对象与空数组保持紧凑")
    void keepsEmptyContainersCompact() throws Exception {
        assertEquals("{\n  \"a\": {},\n  \"b\": []\n}",
                JsonFormatter.format("{\"a\":{},\"b\":[]}", IndentStyle.TWO_SPACES));
    }

    @Test
    @DisplayName("压缩去除全部多余空白")
    void minifies() throws Exception {
        String input = """
                {
                  "a" : 1,
                  "b" : [ 2, 3 ]
                }""";
        assertEquals("{\"a\":1,\"b\":[2,3]}", JsonFormatter.minify(input));
    }

    @Test
    @DisplayName("大整数与科学计数法保持原字面量")
    void preservesNumberLiterals() throws Exception {
        String input = "{\"id\":9007199254740993,\"x\":1.50,\"e\":1e10}";
        String output = JsonFormatter.minify(input);
        assertTrue(output.contains("9007199254740993"), "超长整数不应被 double 截断");
        assertTrue(output.contains("1.50"), "小数位不应被规范化");
        assertTrue(output.contains("1e10"), "科学计数法不应被改写");
    }

    @Test
    @DisplayName("字段顺序与重复键均原样保留")
    void keepsOrderAndDuplicates() throws Exception {
        assertEquals("{\"z\":1,\"a\":2,\"z\":3}",
                JsonFormatter.minify("{\"z\":1,\"a\":2,\"z\":3}"));
    }

    @Test
    @DisplayName("中文与转义字符正确处理")
    void handlesUnicodeAndEscapes() throws Exception {
        String output = JsonFormatter.minify("{\"名称\":\"测试\\n换行\"}");
        assertEquals("{\"名称\":\"测试\\n换行\"}", output);
    }

    @Test
    @DisplayName("字符串内的引号正确转义")
    void escapesQuotes() throws Exception {
        assertEquals("{\"a\":\"say \\\"hi\\\"\"}", JsonFormatter.minify("{\"a\":\"say \\\"hi\\\"\"}"));
    }

    @Test
    @DisplayName("非法 JSON 抛出带行列号的异常")
    void reportsSyntaxError() {
        JsonFormatException error = assertThrows(JsonFormatException.class,
                () -> JsonFormatter.format("{\"a\":}", IndentStyle.TWO_SPACES));
        assertEquals(1, error.getLine());
        assertTrue(error.getColumn() > 0);
        assertTrue(error.getFriendlyMessage().contains("第 1 行"));
    }

    @Test
    @DisplayName("空内容抛出异常")
    void rejectsBlankInput() {
        assertThrows(JsonFormatException.class, () -> JsonFormatter.minify("   "));
    }

    @Test
    @DisplayName("多个顶层值应被拒绝")
    void rejectsMultipleRoots() {
        assertThrows(JsonFormatException.class, () -> JsonFormatter.minify("{} {}"));
    }

    @Test
    @DisplayName("顶层标量合法")
    void acceptsTopLevelScalar() throws Exception {
        assertEquals("123", JsonFormatter.minify(" 123 "));
    }

    @Test
    @DisplayName("校验接口对合法与非法内容分别返回")
    void validatesCorrectly() {
        assertDoesNotThrowHelper("{\"a\":[1,2]}");
        assertThrows(JsonFormatException.class, () -> JsonFormatter.validate("{\"a\":[1,2}"));
    }

    private void assertDoesNotThrowHelper(String json) {
        try {
            JsonFormatter.validate(json);
        } catch (JsonFormatException e) {
            throw new AssertionError("合法 JSON 不应校验失败：" + e.getFriendlyMessage(), e);
        }
    }
}
