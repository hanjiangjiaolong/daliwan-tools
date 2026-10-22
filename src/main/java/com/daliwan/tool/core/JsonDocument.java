package com.daliwan.tool.core;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 可修改的 JSON 文档：承载结构、支持按位置删除节点、重新写回文本。
 *
 * <p>现有的两个 JSON 工具都不适合改内容：{@link JsonFormatter} 走 token 流，
 * 只重排空白，没有结构；{@link JsonTreeBuilder} 产出的是只读展示快照。
 * 树视图要「删掉这个节点」，就得先把内容读进一棵可变的树，删完再写回去。
 *
 * <p>两处刻意的保真设计：
 * <ul>
 *   <li><b>重复键不合并</b>：对象内部用「键列表 + 值列表」平行存放，
 *       同名键各占一个位置，序列化时原样写出。用 Map 会让后一个覆盖前一个，
 *       等于删除一个节点时把别处的重复字段也一起丢了。</li>
 *   <li><b>数字保留原字面量</b>：{@code 1.10} 写回去还是 {@code 1.10}，
 *       不走 double 往返，避免精度与写法被改掉。</li>
 * </ul>
 *
 * <p>字符串的转义写法会被规范化（{@code \/} 变成 {@code /}），这是写回的固有代价。
 */
public final class JsonDocument {

    /** 单次解析允许的最大节点数，与树视图保持一致。 */
    private static final int MAX_NODES = 200_000;

    private static final JsonFactory FACTORY = JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder()
                    .maxNestingDepth(2000)
                    .maxNumberLength(10_000)
                    .build())
            .build();

    /** 节点类型，与 {@link JsonTreeNode.Type} 一一对应。 */
    public enum Type {
        OBJECT, ARRAY, STRING, NUMBER, BOOLEAN, NULL;

        public boolean isContainer() {
            return this == OBJECT || this == ARRAY;
        }
    }

    /** 可变节点。 */
    public static final class Node {

        private final Type type;
        private final List<String> keys;
        private final List<Node> children;
        private final String literal;

        private Node(Type type, List<String> keys, List<Node> children, String literal) {
            this.type = type;
            this.keys = keys;
            this.children = children;
            this.literal = literal;
        }

        public Type type() {
            return type;
        }

        public boolean isContainer() {
            return type.isContainer();
        }

        /** 对象成员数或数组元素数。 */
        public int childCount() {
            return children.size();
        }

        public Node childAt(int index) {
            return children.get(index);
        }

        /** 第 {@code index} 个子项的键名；数组返回 {@code [index]} 形式的展示名。 */
        public String keyAt(int index) {
            if (type == Type.OBJECT) {
                return keys.get(index);
            }
            return "[" + index + "]";
        }

        /** 对象所有键名（按出现顺序，含重复键）；数组返回空列表。 */
        public List<String> keys() {
            return Collections.unmodifiableList(keys);
        }

        /** 标量原文：字符串为解码后的内容，数字 / 布尔为原始字面量。 */
        public String literal() {
            return literal;
        }

        private boolean removeChild(int index) {
            if (index < 0 || index >= children.size()) {
                return false;
            }
            children.remove(index);
            if (type == Type.OBJECT) {
                keys.remove(index);
            }
            return true;
        }
    }

    private final Node root;

    private JsonDocument(Node root) {
        this.root = root;
    }

    /**
     * 解析 JSON 文本。
     *
     * @throws JsonFormatException 语法错误、内容为空或节点过多
     */
    public static JsonDocument parse(String json) throws JsonFormatException {
        if (json == null || json.isBlank()) {
            throw new JsonFormatException("内容为空", 1, 1);
        }
        int[] counter = {0};
        try (JsonParser parser = FACTORY.createParser(new StringReader(json))) {
            JsonToken token = parser.nextToken();
            if (token == null) {
                throw new JsonFormatException("内容为空", 1, 1);
            }
            Node root = parseValue(parser, token, counter);
            if (parser.nextToken() != null) {
                throw new JsonFormatException("只允许一个顶层 JSON 值", 1, 1);
            }
            return new JsonDocument(root);
        } catch (JsonParseException e) {
            throw toFriendly(e);
        } catch (IOException e) {
            throw new JsonFormatException("读取内容失败：" + e.getMessage(), 1, 1);
        }
    }

    public Node root() {
        return root;
    }

    /**
     * 按下标路径删除节点（路径来自树视图节点的 {@link JsonTreeNode#segments()}）。
     *
     * @return 是否删除成功；路径失效或指向根节点时返回 {@code false}
     */
    public boolean remove(List<Integer> segments) {
        if (segments == null || segments.isEmpty()) {
            return false;
        }
        Node parent = root;
        for (int i = 0; i < segments.size() - 1; i++) {
            if (!parent.isContainer()) {
                return false;
            }
            int index = segments.get(i);
            if (index < 0 || index >= parent.childCount()) {
                return false;
            }
            parent = parent.childAt(index);
        }
        return parent.isContainer() && parent.removeChild(segments.get(segments.size() - 1));
    }

    /** 按缩进风格写回多行文本。 */
    public String toJson(IndentStyle indent) {
        String unit = indent == null ? IndentStyle.TWO_SPACES.unit() : indent.unit();
        return write(root, unit);
    }

    /** 写回单行文本（原文本来就是压缩格式时用，避免删一个节点把整份内容撑成多行）。 */
    public String toCompactJson() {
        return write(root, null);
    }

    /** 把任意节点写成单行 JSON。 */
    public static String compact(Node node) {
        return write(node, null);
    }

    private static String write(Node node, String indentUnit) {
        StringBuilder out = new StringBuilder(256);
        writeNode(node, out, indentUnit, 0);
        return out.toString();
    }

    private static void writeNode(Node node, StringBuilder out, String indentUnit, int depth) {
        switch (node.type()) {
            case OBJECT -> {
                if (node.childCount() == 0) {
                    out.append("{}");
                    return;
                }
                out.append('{');
                for (int i = 0; i < node.childCount(); i++) {
                    if (i > 0) {
                        out.append(',');
                    }
                    newline(out, indentUnit, depth + 1);
                    JsonFormatter.appendQuoted(out, node.keyAt(i));
                    out.append(':');
                    if (indentUnit != null) {
                        out.append(' ');
                    }
                    writeNode(node.childAt(i), out, indentUnit, depth + 1);
                }
                newline(out, indentUnit, depth);
                out.append('}');
            }
            case ARRAY -> {
                if (node.childCount() == 0) {
                    out.append("[]");
                    return;
                }
                out.append('[');
                for (int i = 0; i < node.childCount(); i++) {
                    if (i > 0) {
                        out.append(',');
                    }
                    newline(out, indentUnit, depth + 1);
                    writeNode(node.childAt(i), out, indentUnit, depth + 1);
                }
                newline(out, indentUnit, depth);
                out.append(']');
            }
            case STRING -> JsonFormatter.appendQuoted(out, node.literal());
            case NUMBER, BOOLEAN -> out.append(node.literal());
            case NULL -> out.append("null");
        }
    }

    private static void newline(StringBuilder out, String indentUnit, int depth) {
        if (indentUnit == null) {
            return;
        }
        out.append('\n');
        for (int i = 0; i < depth; i++) {
            out.append(indentUnit);
        }
    }

    // ------------------------------------------------------------------ 解析

    private static Node parseValue(JsonParser parser, JsonToken token, int[] counter)
            throws IOException, JsonFormatException {
        if (++counter[0] > MAX_NODES) {
            throw new JsonFormatException("节点数量超过 " + MAX_NODES + "，请使用较小的 JSON", 1, 1);
        }
        switch (token) {
            case START_OBJECT -> {
                List<String> keys = new ArrayList<>();
                List<Node> children = new ArrayList<>();
                while (parser.nextToken() == JsonToken.FIELD_NAME) {
                    keys.add(parser.currentName());
                    children.add(parseValue(parser, parser.nextToken(), counter));
                }
                return new Node(Type.OBJECT, keys, children, null);
            }
            case START_ARRAY -> {
                List<Node> children = new ArrayList<>();
                JsonToken element = parser.nextToken();
                while (element != JsonToken.END_ARRAY) {
                    if (element == null) {
                        throw new JsonFormatException("数组未闭合", 1, 1);
                    }
                    children.add(parseValue(parser, element, counter));
                    element = parser.nextToken();
                }
                return new Node(Type.ARRAY, List.of(), children, null);
            }
            case VALUE_STRING -> {
                return new Node(Type.STRING, List.of(), List.of(), parser.getText());
            }
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> {
                return new Node(Type.NUMBER, List.of(), List.of(), parser.getText());
            }
            case VALUE_TRUE -> {
                return new Node(Type.BOOLEAN, List.of(), List.of(), "true");
            }
            case VALUE_FALSE -> {
                return new Node(Type.BOOLEAN, List.of(), List.of(), "false");
            }
            case VALUE_NULL -> {
                return new Node(Type.NULL, List.of(), List.of(), "null");
            }
            default -> {
                throw new JsonFormatException("无法识别的 JSON 值", 1, 1);
            }
        }
    }

    private static JsonFormatException toFriendly(JsonProcessingException e) {
        JsonLocation location = e.getLocation();
        int line = location == null ? 1 : location.getLineNr();
        int column = location == null ? 1 : location.getColumnNr();
        return new JsonFormatException(JsonFormatter.translate(e.getOriginalMessage()), line, column);
    }
}
