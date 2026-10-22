package com.daliwan.tool.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonDocumentTest {

    @Test
    @DisplayName("删除对象成员后按键顺序写回")
    void removesObjectMember() throws Exception {
        JsonDocument document = JsonDocument.parse("{\"a\":1,\"b\":2,\"c\":3}");

        assertTrue(document.remove(List.of(1)));

        assertEquals("""
                {
                  "a": 1,
                  "c": 3
                }""", document.toJson(IndentStyle.TWO_SPACES));
    }

    @Test
    @DisplayName("删除数组元素，后面的元素不动")
    void removesArrayElement() throws Exception {
        JsonDocument document = JsonDocument.parse("[10,20,30]");

        assertTrue(document.remove(List.of(0)));

        assertEquals("[20,30]", document.toCompactJson());
    }

    @Test
    @DisplayName("按多级路径删除嵌套节点")
    void removesNestedNode() throws Exception {
        JsonDocument document = JsonDocument.parse("""
                {"data":{"list":[{"id":1,"name":"张三"},{"id":2}]}}""");

        // data(0) → list(0) → 第一个元素(0) → 它的 name 字段(1)
        assertTrue(document.remove(List.of(0, 0, 0, 1)));

        assertEquals("{\"data\":{\"list\":[{\"id\":1},{\"id\":2}]}}",
                document.toCompactJson());
    }

    @Test
    @DisplayName("删掉最后一个成员后写成空容器")
    void emptiesContainer() throws Exception {
        JsonDocument object = JsonDocument.parse("{\"a\":1}");
        assertTrue(object.remove(List.of(0)));
        assertEquals("{}", object.toCompactJson());

        JsonDocument array = JsonDocument.parse("[1]");
        assertTrue(array.remove(List.of(0)));
        assertEquals("[]", array.toCompactJson());
    }

    @Test
    @DisplayName("根节点不能删除")
    void refusesRoot() throws Exception {
        JsonDocument document = JsonDocument.parse("{\"a\":1}");

        assertFalse(document.remove(List.of()));
        assertFalse(document.remove(null));
        assertEquals("{\"a\":1}", document.toCompactJson());
    }

    @Test
    @DisplayName("路径失效时返回 false 且内容不变")
    void refusesStalePath() throws Exception {
        JsonDocument document = JsonDocument.parse("{\"a\":[1]}");

        assertFalse(document.remove(List.of(5)));
        assertFalse(document.remove(List.of(0, 3)));
        assertFalse(document.remove(List.of(0, 0, 0)));
        assertEquals("{\"a\":[1]}", document.toCompactJson());
    }

    @Test
    @DisplayName("重复键原样保留，删除时按位置区分")
    void keepsDuplicateKeys() throws Exception {
        JsonDocument document = JsonDocument.parse("{\"a\":1,\"a\":2,\"b\":3}");

        // 第三个成员才是 b；若用 Map 存，前面两个 a 早已被合并成一个
        assertTrue(document.remove(List.of(2)));

        assertEquals("{\"a\":1,\"a\":2}", document.toCompactJson());
    }

    @Test
    @DisplayName("数字字面量原样写回，不经 double 往返")
    void keepsNumberLiteral() throws Exception {
        JsonDocument document = JsonDocument.parse("{\"n\":1.10,\"m\":1e3,\"big\":12345678901234567890}");

        assertEquals("{\"n\":1.10,\"m\":1e3,\"big\":12345678901234567890}",
                document.toCompactJson());
    }

    @Test
    @DisplayName("字符串按 JSON 规范转义")
    void escapesStrings() throws Exception {
        JsonDocument document = JsonDocument.parse(
                "{\"s\":\"他说\\\"好\\\"\\n下一行\",\"t\":\"制表\\t符\"}");

        assertEquals("{\"s\":\"他说\\\"好\\\"\\n下一行\",\"t\":\"制表\\t符\"}",
                document.toCompactJson());
    }

    @Test
    @DisplayName("支持 Tab 与四空格缩进")
    void supportsIndentStyles() throws Exception {
        JsonDocument document = JsonDocument.parse("{\"a\":[1]}");

        assertEquals("{\n\t\"a\": [\n\t\t1\n\t]\n}", document.toJson(IndentStyle.TAB));
        assertEquals("{\n    \"a\": [\n        1\n    ]\n}",
                document.toJson(IndentStyle.FOUR_SPACES));
    }

    @Test
    @DisplayName("写回结果可以再次解析，结构不变")
    void roundTrips() throws Exception {
        JsonDocument first = JsonDocument.parse("""
                {"code":0,"data":{"list":[{"id":1,"tags":["a","b"]},{"id":2,"tags":[]}]}}""");

        String written = first.toJson(IndentStyle.TWO_SPACES);
        JsonDocument second = JsonDocument.parse(written);

        assertEquals(written, second.toJson(IndentStyle.TWO_SPACES));
        assertEquals(2, second.root().childCount());
    }

    @Test
    @DisplayName("空内容与非 JSON 内容报错")
    void rejectsInvalidInput() {
        assertThrows(JsonFormatException.class, () -> JsonDocument.parse(""));
        assertThrows(JsonFormatException.class, () -> JsonDocument.parse("   "));
        assertThrows(JsonFormatException.class, () -> JsonDocument.parse("{a:1}"));
        assertThrows(JsonFormatException.class, () -> JsonDocument.parse("{\"a\":1} {}"));
    }
}
