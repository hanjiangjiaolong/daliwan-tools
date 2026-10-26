package com.daliwan.tool.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonTableTest {

    @Test
    @DisplayName("对象数组按键名并集成列")
    void buildsColumnsFromObjects() throws Exception {
        JsonTable.Table table = JsonTable.build("""
                [{"id":1,"name":"张三"},{"id":2,"name":"李四"}]""");

        assertEquals(List.of("id", "name"), table.columns());
        assertEquals(2, table.rows().size());
        assertEquals(List.of("2", "李四"), table.rows().get(1));
        assertEquals(2, table.totalRows());
        assertFalse(table.truncatedRows());
    }

    @Test
    @DisplayName("字段不齐时按列并集补空")
    void fillsMissingFields() throws Exception {
        JsonTable.Table table = JsonTable.build("""
                [{"a":1},{"b":2},{"a":3,"c":4}]""");

        assertEquals(List.of("a", "b", "c"), table.columns());
        assertEquals(List.of("1", "", ""), table.rows().get(0));
        assertEquals(List.of("", "2", ""), table.rows().get(1));
        assertEquals(List.of("3", "", "4"), table.rows().get(2));
    }

    @Test
    @DisplayName("标量数组退回单列 value")
    void scalarArrayUsesValueColumn() throws Exception {
        JsonTable.Table table = JsonTable.build("[1,\"two\",true,null]");

        assertEquals(List.of(JsonTable.VALUE_COLUMN), table.columns());
        assertEquals(List.of("1"), table.rows().get(0));
        assertEquals(List.of("two"), table.rows().get(1));
        assertEquals(List.of("true"), table.rows().get(2));
        assertEquals(List.of("null"), table.rows().get(3));
    }

    @Test
    @DisplayName("嵌套对象与数组还原成紧凑 JSON")
    void nestedValuesBecomeCompactJson() throws Exception {
        JsonTable.Table table = JsonTable.build("""
                [{"id":1,"tags":["a","b"],"owner":{"name":"张三","vip":true}}]""");

        assertEquals(List.of("id", "tags", "owner"), table.columns());
        assertEquals("[\"a\",\"b\"]", table.rows().get(0).get(1));
        assertEquals("{\"name\":\"张三\",\"vip\":true}", table.rows().get(0).get(2));
    }

    @Test
    @DisplayName("嵌套容器里的引号被转义，标量里的转义被还原")
    void escapesNestedStrings() throws Exception {
        JsonTable.Table table = JsonTable.build("""
                [{"obj":{"t":"他说\\"好\\""},"plain":"a\\nb"}]""");

        assertEquals("{\"t\":\"他说\\\"好\\\"\"}", table.rows().get(0).get(0));
        assertEquals("a\nb", table.rows().get(0).get(1));
    }

    @Test
    @DisplayName("顶层对象当成一行")
    void objectBecomesSingleRow() throws Exception {
        JsonTable.Table table = JsonTable.build("""
                {"code":0,"message":"ok"}""");

        assertEquals(List.of("code", "message"), table.columns());
        assertEquals(1, table.rows().size());
        assertEquals(List.of("0", "ok"), table.rows().get(0));
    }

    @Test
    @DisplayName("空数组没有行也不报错")
    void emptyArrayIsAllowed() throws Exception {
        JsonTable.Table table = JsonTable.build("[]");

        assertTrue(table.isEmpty());
        assertTrue(table.columns().isEmpty());
        assertEquals(0, table.totalRows());
    }

    @Test
    @DisplayName("顶层标量给出明确提示")
    void scalarRootRejected() {
        JsonFormatException error = assertThrows(JsonFormatException.class,
                () -> JsonTable.build("123"));
        assertTrue(error.getMessage().contains("数组或对象"));
    }

    @Test
    @DisplayName("语法错误带出行列号")
    void syntaxErrorHasLocation() {
        JsonFormatException error = assertThrows(JsonFormatException.class,
                () -> JsonTable.build("[{\"a\":}]"));
        assertTrue(error.getLine() >= 1);
        assertTrue(error.getFriendlyMessage().startsWith("第 "));
    }

    @Test
    @DisplayName("超过行数上限时截断并标记")
    void truncatesRows() throws Exception {
        JsonTable.Table table = JsonTable.build("[1,2,3,4,5]", 3, 10);

        assertEquals(3, table.rows().size());
        assertEquals(5, table.totalRows());
        assertTrue(table.truncatedRows());
    }

    @Test
    @DisplayName("超过列数上限时截断并标记")
    void truncatesColumns() throws Exception {
        JsonTable.Table table = JsonTable.build("[{\"a\":1,\"b\":2,\"c\":3}]", 10, 2);

        assertEquals(List.of("a", "b"), table.columns());
        assertTrue(table.truncatedColumns());
    }

    @Test
    @DisplayName("CSV 导出按 RFC 4180 转义")
    void csvEscapesSpecials() throws Exception {
        JsonTable.Table table = JsonTable.build("""
                [{"name":"张,三","note":"含\\"引号\\"","plain":"ok"}]""");

        String csv = JsonTable.toCsv(table);
        assertEquals("name,note,plain\r\n"
                + "\"张,三\",\"含\"\"引号\"\"\",ok\r\n", csv);
    }

    @Test
    @DisplayName("CSV 首行是列名")
    void csvStartsWithHeader() throws Exception {
        JsonTable.Table table = JsonTable.build("[{\"id\":1,\"name\":\"a\"}]");

        assertEquals("id,name\r\n1,a\r\n", JsonTable.toCsv(table));
    }

    // ------------------------------------------------------------ 整行 JSON

    @Test
    @DisplayName("按行号取回原始节点，类型与原文都保住")
    void rowNodeKeepsOriginalTypes() throws Exception {
        JsonDocument document = JsonDocument.parse(
                "[{\"id\":1,\"name\":\"张三\"},{\"id\":2,\"name\":\"李四\"}]");
        JsonTable.Candidate list = new JsonTable.Candidate("", List.of(), 2, 2, true);

        JsonDocument.Node first = JsonTable.rowNode(document, list, 0);
        assertEquals(JsonDocument.Type.OBJECT, first.type());
        assertEquals("{\"id\":1,\"name\":\"张三\"}", JsonDocument.compact(first));

        JsonDocument.Node second = JsonTable.rowNode(document, list, 1);
        assertEquals("李四", second.childAt(1).literal(), "字符串取的是解码后的内容");
        assertEquals(JsonDocument.Type.NUMBER, second.childAt(0).type(),
                "数字仍是数字，不会退化成单元格里那种分不清类型的文本");
    }

    @Test
    @DisplayName("表格包在里层时，行号对应的是那个数组的元素")
    void rowNodeFollowsCandidatePath() throws Exception {
        JsonDocument document = JsonDocument.parse(
                "{\"code\":0,\"data\":{\"list\":[{\"id\":1},{\"id\":2},{\"id\":3}]}}");
        JsonTable.Candidate list = new JsonTable.Candidate("data.list", List.of(1, 0), 3, 1, true);

        assertEquals("{\"id\":3}", JsonDocument.compact(JsonTable.rowNode(document, list, 2)));
        assertEquals("{\"code\":0,\"data\":{\"list\":[{\"id\":1},{\"id\":2},{\"id\":3}]}}",
                JsonDocument.compact(JsonTable.rowNode(document, null, 0)),
                "没有候选时整张表就是顶层那一个对象");
    }

    @Test
    @DisplayName("下标越界或数据源失效时返回 null，由界面给出提示")
    void rowNodeReturnsNullWhenMissing() throws Exception {
        JsonDocument document = JsonDocument.parse("[{\"id\":1}]");
        JsonTable.Candidate list = new JsonTable.Candidate("", List.of(), 1, 1, true);

        assertNull(JsonTable.rowNode(document, list, 1), "只有一行，第二行没有对应物");
        assertNull(JsonTable.rowNode(document, list, -1));
        assertNull(JsonTable.rowNode(null, list, 0));
        assertNull(JsonTable.rowNode(document, new JsonTable.Candidate("x", List.of(9), 1, 1, true), 0),
                "路径已经不在文档里");
        assertNull(JsonTable.rowNode(document, null, 3), "顶层对象只有一行");
    }
}
