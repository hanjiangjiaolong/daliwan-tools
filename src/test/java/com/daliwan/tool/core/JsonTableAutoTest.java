package com.daliwan.tool.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonTableAutoTest {

    @Test
    @DisplayName("包裹结构自动下钻到 data.list")
    void findsNestedArray() throws Exception {
        JsonTable.AutoResult result = JsonTable.buildAuto("""
                {"code":0,"message":"ok","data":{"total":2,"page":1,"list":[\
                {"id":1001,"name":"张三"},{"id":1002,"name":"李四"}]}}""");

        assertEquals("data.list", result.sourcePath());
        assertEquals(List.of("id", "name"), result.table().columns());
        assertEquals(2, result.table().rows().size());
        assertEquals(1, result.candidates().size());
        assertEquals(0, result.chosen());
    }

    @Test
    @DisplayName("顶层就是数组时也作为一个候选，路径为空")
    void keepsTopLevelArray() throws Exception {
        JsonTable.AutoResult result = JsonTable.buildAuto("[{\"id\":1},{\"id\":2}]");

        assertEquals("", result.sourcePath());
        assertEquals(1, result.candidates().size());
        assertEquals("", result.candidates().get(0).path());
        assertEquals(2, result.candidates().get(0).rows());
        assertEquals(List.of("id"), result.table().columns());
        assertEquals(2, result.table().rows().size());
    }

    @Test
    @DisplayName("多个数组时对象数组优先于标量数组")
    void prefersObjectArray() throws Exception {
        JsonTable.AutoResult result = JsonTable.buildAuto("""
                {"tags":["a","b","c"],"users":[{"id":1,"name":"张三"},{"id":2,"name":"李四"}]}""");

        assertEquals("users", result.sourcePath());
        assertEquals(List.of("id", "name"), result.table().columns());
    }

    @Test
    @DisplayName("两个对象数组取列多的那个")
    void prefersMoreColumns() throws Exception {
        JsonTable.AutoResult result = JsonTable.buildAuto("""
                {"brief":[{"id":1}],"detail":[{"id":1,"name":"张三","age":30}]}""");

        assertEquals("detail", result.sourcePath());
        assertEquals(List.of("id", "name", "age"), result.table().columns());
    }

    @Test
    @DisplayName("只有标量数组时退回单列 value")
    void scalarArrayBecomesValueColumn() throws Exception {
        JsonTable.AutoResult result = JsonTable.buildAuto("{\"roles\":[\"admin\",\"dev\"]}");

        assertEquals("roles", result.sourcePath());
        assertEquals(List.of(JsonTable.VALUE_COLUMN), result.table().columns());
        assertEquals(List.of("admin"), result.table().rows().get(0));
    }

    @Test
    @DisplayName("一个数组都没有时顶层对象当一行")
    void fallsBackToOneRow() throws Exception {
        JsonTable.AutoResult result = JsonTable.buildAuto("{\"code\":0,\"message\":\"ok\"}");

        assertEquals("", result.sourcePath());
        assertTrue(result.candidates().isEmpty());
        assertEquals(List.of("code", "message"), result.table().columns());
        assertEquals(1, result.table().rows().size());
    }

    @Test
    @DisplayName("不进入数组内部找候选，避免选中某一行的嵌套数组")
    void doesNotDrillIntoArrays() throws Exception {
        JsonTable.AutoResult result = JsonTable.buildAuto("""
                {"items":[\
                {"id":1,"details":[{"a":1,"b":2,"c":3},{"a":4,"b":5,"c":6}]},\
                {"id":2,"details":[]}]}""");

        assertEquals("items", result.sourcePath());
        assertEquals(1, result.candidates().size(), "details 在数组内部，不该成为候选");
        assertEquals(List.of("id", "details"), result.table().columns());
        assertEquals(2, result.table().rows().size());
    }

    @Test
    @DisplayName("空数组也算候选，显示为空表")
    void acceptsEmptyArray() throws Exception {
        JsonTable.AutoResult result = JsonTable.buildAuto("{\"data\":{\"list\":[]}}");

        assertEquals("data.list", result.sourcePath());
        assertEquals(0, result.table().rows().size());
        assertTrue(result.table().columns().isEmpty());
    }

    @Test
    @DisplayName("多级对象链上的数组会给出完整路径")
    void reportsDeepPath() throws Exception {
        JsonTable.AutoResult result = JsonTable.buildAuto(
                "{\"result\":{\"paging\":{\"list\":[{\"id\":1}]}}}");

        assertEquals("result.paging.list", result.sourcePath());
        assertEquals(1, result.table().rows().size());
    }

    @Test
    @DisplayName("所有数组都进候选列表，按「像表格」的程度排序")
    void listsAllCandidatesInPriorityOrder() throws Exception {
        JsonTable.AutoResult result = JsonTable.buildAuto(
                "{\"a\":[1,2],\"b\":{\"c\":[{\"x\":1}]},\"d\":[]}");

        assertEquals("b.c", result.sourcePath());
        assertEquals(3, result.candidates().size());
        // 对象数组 > 标量数组；同为标量数组时行多的靠前
        assertEquals(List.of("b.c", "a", "d"),
                result.candidates().stream().map(JsonTable.Candidate::path).toList());
    }

    @Test
    @DisplayName("按候选切换数据源：切到另一个数组能拿到自己的表")
    void switchesBetweenCandidates() throws Exception {
        JsonTable.AutoResult result = JsonTable.buildAuto("""
                {"users":[{"id":1,"name":"张三"}],"tags":["admin","dev","ops"]}""");

        assertEquals("users", result.sourcePath());

        JsonTable.Candidate tags = result.candidates().stream()
                .filter(item -> item.path().equals("tags"))
                .findFirst().orElseThrow();
        JsonTable.Table table = JsonTable.buildAt(result.document(), tags);

        assertEquals(List.of(JsonTable.VALUE_COLUMN), table.columns());
        assertEquals(3, table.rows().size());
        assertEquals(List.of("admin"), table.rows().get(0));
    }

    @Test
    @DisplayName("候选带上位置路径与人类可读的标签")
    void candidateCarriesPathAndLabel() throws Exception {
        JsonTable.AutoResult result = JsonTable.buildAuto(
                "{\"data\":{\"list\":[{\"id\":1,\"name\":\"张三\"}]}}");

        JsonTable.Candidate selected = result.selected();
        assertNotNull(selected);
        assertEquals(List.of(0, 0), selected.segments(), "data → list 的下标路径");
        assertEquals(1, selected.rows());
        assertEquals(2, selected.columns());
        assertTrue(selected.objectArray());
        assertEquals("data.list · 1 行 × 2 列", selected.label());
        assertFalse(selected.label().isEmpty());
    }

    @Test
    @DisplayName("顶层是标量时仍按原逻辑报错")
    void rejectsScalarRoot() {
        assertThrows(JsonFormatException.class, () -> JsonTable.buildAuto("123"));
        assertThrows(JsonFormatException.class, () -> JsonTable.buildAuto("null"));
    }
}
