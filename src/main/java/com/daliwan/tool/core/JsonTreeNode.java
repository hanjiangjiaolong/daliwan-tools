package com.daliwan.tool.core;

import java.util.Collections;
import java.util.List;

/**
 * JSON 树视图的节点模型。
 *
 * <p>只保留展示所需的信息：键名、JSONPath、类型、值预览与子节点。
 */
public final class JsonTreeNode {

    /** 节点类型。 */
    public enum Type {
        OBJECT("对象", "{}"),
        ARRAY("数组", "[]"),
        STRING("字符串", "\"\""),
        NUMBER("数字", "0"),
        BOOLEAN("布尔", "true"),
        NULL("空值", "null");

        private final String label;
        private final String badge;

        Type(String label, String badge) {
            this.label = label;
            this.badge = badge;
        }

        public String label() {
            return label;
        }

        public String badge() {
            return badge;
        }

        public boolean isContainer() {
            return this == OBJECT || this == ARRAY;
        }
    }

    private static final int PREVIEW_LIMIT = 90;

    private final String key;
    private final String path;
    private final Type type;
    private final String preview;
    /** 未经截断、未经去引号处理的原始文本，供「复制值」使用。 */
    private final String raw;
    /**
     * 从根到本节点的下标路径：对象成员取它在成员列表里的序号，数组元素取元素下标。
     *
     * <p>键名不足以定位：JSON 允许重复键，树里会并排出现两个同名节点，
     * 只有序号能说清删的是哪一个。根节点为空列表。
     */
    private final List<Integer> segments;
    private final List<JsonTreeNode> children;

    private JsonTreeNode(String key, String path, Type type, String preview, String raw,
                         List<Integer> segments, List<JsonTreeNode> children) {
        this.key = key;
        this.path = path;
        this.type = type;
        this.preview = preview;
        this.raw = raw == null ? "" : raw;
        this.segments = segments == null ? List.of() : List.copyOf(segments);
        this.children = children == null ? List.of() : List.copyOf(children);
    }

    static JsonTreeNode container(String key, String path, Type type, List<Integer> segments,
                                  List<JsonTreeNode> children) {
        int size = children == null ? 0 : children.size();
        String unit = type == Type.ARRAY ? " 个元素" : " 个字段";
        String preview = size == 0 ? "（空）" : size + unit;
        return new JsonTreeNode(key, path, type, preview, preview, segments, children);
    }

    static JsonTreeNode scalar(String key, String path, Type type, String raw,
                               List<Integer> segments) {
        String text = raw == null ? "" : raw;
        return new JsonTreeNode(key, path, type, truncate(text), text, segments, List.of());
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        String singleLine = text.replace("\n", "\\n").replace("\r", "");
        if (singleLine.length() <= PREVIEW_LIMIT) {
            return singleLine;
        }
        return singleLine.substring(0, PREVIEW_LIMIT) + "…";
    }

    public String key() {
        return key;
    }

    public String path() {
        return path;
    }

    /** 从根到本节点的下标路径，供删除节点时定位；根节点为空列表。 */
    public List<Integer> segments() {
        return segments;
    }

    /** 是否根节点（根节点不能删除）。 */
    public boolean isRoot() {
        return segments.isEmpty();
    }

    public Type type() {
        return type;
    }

    public String preview() {
        return preview;
    }

    /**
     * 复制用的完整值：字符串去掉首尾双引号并还原转义，其余类型原样返回。
     *
     * <p>与 {@link #preview()} 的区别在于不截断、不带 JSON 语法里的引号，
     * 例：节点显示 {@code "张三"} 时，复制得到 {@code 张三}。
     */
    public String value() {
        if (type != Type.STRING) {
            return raw;
        }
        String text = raw;
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            text = text.substring(1, text.length() - 1);
        }
        return unescape(text);
    }

    /** 还原 JSON 字符串里的常见转义，让复制出来的是真实内容而非 {@code \n} 之类的字面量。 */
    private static String unescape(String text) {
        if (text.indexOf('\\') < 0) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '\\' || i + 1 >= text.length()) {
                out.append(c);
                continue;
            }
            char next = text.charAt(++i);
            switch (next) {
                case 'n' -> out.append('\n');
                case 't' -> out.append('\t');
                case 'r' -> out.append('\r');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'u' -> {
                    if (i + 4 < text.length()) {
                        String hex = text.substring(i + 1, i + 5);
                        try {
                            out.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                        } catch (NumberFormatException e) {
                            out.append('\\').append(next);
                        }
                    } else {
                        out.append('\\').append(next);
                    }
                }
                // \" \\ \/ 之外的转义按原字符保留
                default -> out.append(next);
            }
        }
        return out.toString();
    }

    /** 键名是否可复制：数组下标（{@code [0]}）与根节点占位名都不是真正的键名。 */
    public boolean hasName() {
        return key != null && !key.isEmpty() && key.charAt(0) != '[' && !"根节点".equals(key);
    }

    public List<JsonTreeNode> children() {
        return Collections.unmodifiableList(children);
    }

    public boolean hasChildren() {
        return !children.isEmpty();
    }

    /** 该节点为容器时的直接子节点数量。 */
    public int childCount() {
        return children.size();
    }

    @Override
    public String toString() {
        return key + " : " + preview;
    }
}
