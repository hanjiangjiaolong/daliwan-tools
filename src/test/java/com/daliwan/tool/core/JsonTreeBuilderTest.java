package com.daliwan.tool.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonTreeBuilderTest {

    private static final String SAMPLE = """
            {"a":1,"b":[true,null,"x"],"c":{"d":{"e":2.5}}}""";

    @Test
    @DisplayName("根节点为对象并统计直接子节点")
    void buildsRootObject() throws Exception {
        JsonTreeNode root = JsonTreeBuilder.build(SAMPLE);
        assertEquals(JsonTreeNode.Type.OBJECT, root.type());
        assertEquals(3, root.childCount());
        assertEquals("$", root.path());
    }

    @Test
    @DisplayName("字段节点带有正确的键名、路径与类型")
    void buildsFieldNodes() throws Exception {
        JsonTreeNode root = JsonTreeBuilder.build(SAMPLE);

        JsonTreeNode a = root.children().get(0);
        assertEquals("a", a.key());
        assertEquals("$.a", a.path());
        assertEquals(JsonTreeNode.Type.NUMBER, a.type());
        assertEquals("1", a.preview());

        JsonTreeNode b = root.children().get(1);
        assertEquals(JsonTreeNode.Type.ARRAY, b.type());
        assertEquals("$.b", b.path());
        assertEquals(3, b.childCount());
    }

    @Test
    @DisplayName("数组元素路径使用下标")
    void buildsArrayPaths() throws Exception {
        JsonTreeNode array = JsonTreeBuilder.build(SAMPLE).children().get(1);
        assertEquals("$.b[0]", array.children().get(0).path());
        assertEquals("[0]", array.children().get(0).key());
        assertEquals(JsonTreeNode.Type.BOOLEAN, array.children().get(0).type());
        assertEquals(JsonTreeNode.Type.NULL, array.children().get(1).type());
        assertEquals(JsonTreeNode.Type.STRING, array.children().get(2).type());
    }

    @Test
    @DisplayName("深层嵌套路径逐级拼接")
    void buildsNestedPath() throws Exception {
        JsonTreeNode c = JsonTreeBuilder.build(SAMPLE).children().get(2);
        JsonTreeNode e = c.children().get(0).children().get(0);
        assertEquals("$.c.d.e", e.path());
        assertEquals(JsonTreeNode.Type.NUMBER, e.type());
    }

    @Test
    @DisplayName("特殊键名使用方括号写法")
    void quotesSpecialKeyNames() throws Exception {
        JsonTreeNode root = JsonTreeBuilder.build("{\"a b\":1,\"中文\":2}");
        assertEquals("$['a b']", root.children().get(0).path());
        assertEquals("$['中文']", root.children().get(1).path());
    }

    @Test
    @DisplayName("空容器显示（空）")
    void marksEmptyContainers() throws Exception {
        JsonTreeNode root = JsonTreeBuilder.build("{\"o\":{},\"a\":[]}");
        assertEquals("（空）", root.children().get(0).preview());
        assertEquals("（空）", root.children().get(1).preview());
        assertFalse(root.children().get(0).hasChildren());
    }

    @Test
    @DisplayName("长字符串预览会被截断")
    void truncatesLongPreview() throws Exception {
        String longText = "x".repeat(200);
        JsonTreeNode node = JsonTreeBuilder.build("{\"k\":\"" + longText + "\"}").children().get(0);
        assertTrue(node.preview().endsWith("…"));
        assertTrue(node.preview().length() < longText.length());
    }

    @Test
    @DisplayName("复制值去掉双引号并还原转义，且不截断")
    void copiesStringValueWithoutQuotes() throws Exception {
        String text = "第一行\\n第二行 \\\"quoted\\\" " + "y".repeat(200);
        JsonTreeNode node = JsonTreeBuilder.build("{\"k\":\"" + text + "\"}").children().get(0);

        String value = node.value();
        assertFalse(value.startsWith("\""), "复制值不应带双引号");
        assertTrue(value.contains("第一行\n第二行 \"quoted\" "), "转义应被还原");
        assertTrue(value.endsWith("y".repeat(200)), "复制值不应被预览截断");
    }

    @Test
    @DisplayName("非字符串类型的复制值原样返回")
    void copiesNonStringValueAsIs() throws Exception {
        JsonTreeNode root = JsonTreeBuilder.build("{\"n\":12.5,\"t\":true,\"z\":null}");
        assertEquals("12.5", root.children().get(0).value());
        assertEquals("true", root.children().get(1).value());
        assertEquals("null", root.children().get(2).value());
    }

    @Test
    @DisplayName("数组下标与根节点没有可复制的键名")
    void knowsWhichNodesHaveNames() throws Exception {
        JsonTreeNode root = JsonTreeBuilder.build("[\"a\"]");
        assertFalse(root.hasName());
        assertFalse(root.children().get(0).hasName());

        JsonTreeNode named = JsonTreeBuilder.build("{\"k\":1}").children().get(0);
        assertTrue(named.hasName());
    }

    @Test
    @DisplayName("顶层数组同样可解析")
    void buildsTopLevelArray() throws Exception {
        JsonTreeNode root = JsonTreeBuilder.build("[1,2,3]");
        assertEquals(JsonTreeNode.Type.ARRAY, root.type());
        assertEquals(3, root.childCount());
        assertEquals("$[2]", root.children().get(2).path());
    }

    @Test
    @DisplayName("非法 JSON 抛出带行列的异常")
    void rejectsInvalidJson() {
        assertThrows(JsonFormatException.class, () -> JsonTreeBuilder.build("{\"a\":}"));
    }

    @Test
    @DisplayName("多个顶层值被拒绝")
    void rejectsMultipleRoots() {
        assertThrows(JsonFormatException.class, () -> JsonTreeBuilder.build("{} {}"));
    }

    @Test
    @DisplayName("空内容被拒绝")
    void rejectsBlank() {
        assertThrows(JsonFormatException.class, () -> JsonTreeBuilder.build("   "));
    }
}
