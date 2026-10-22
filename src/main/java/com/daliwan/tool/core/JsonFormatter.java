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
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * JSON 格式化 / 压缩 / 语法校验。
 *
 * <p>实现方式是 token 流重排而非对象树重建，因此：
 * <ul>
 *   <li>字段顺序原样保留</li>
 *   <li>数字字面量原样保留，不会因 double 往返精度而失真</li>
 *   <li>重复键不会被合并或丢弃</li>
 * </ul>
 * 格式化只改动空白字符，不改变语义。
 */
public final class JsonFormatter {

    private static final JsonFactory FACTORY = JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder()
                    .maxNestingDepth(2000)
                    .maxNumberLength(10_000)
                    .build())
            .build();

    private JsonFormatter() {
    }

    /**
     * 美化输出。
     *
     * @param json   原始 JSON 文本
     * @param indent 缩进风格
     * @return 格式化后的 JSON
     * @throws JsonFormatException 语法错误或内容为空
     */
    public static String format(String json, IndentStyle indent) throws JsonFormatException {
        return render(json, indent == null ? IndentStyle.TWO_SPACES.unit() : indent.unit());
    }

    /**
     * 压缩为单行 JSON（去除所有多余空白）。
     *
     * @param json 原始 JSON 文本
     * @return 压缩后的 JSON
     * @throws JsonFormatException 语法错误或内容为空
     */
    public static String minify(String json) throws JsonFormatException {
        return render(json, null);
    }

    /** 仅校验语法，不产生输出。 */
    public static void validate(String json) throws JsonFormatException {
        render(json, null);
    }

    // ------------------------------------------------------------------ 核心

    private static String render(String json, String indentUnit) throws JsonFormatException {
        if (json == null || json.isBlank()) {
            throw new JsonFormatException("内容为空", 1, 1);
        }
        boolean pretty = indentUnit != null;
        StringBuilder out = new StringBuilder(json.length() + 64);

        // 栈顶元素表示「当前打开的容器内是否已有内容」，用于决定是否输出逗号
        Deque<Boolean> hasContent = new ArrayDeque<>();
        boolean expectValue = false;
        int depth = 0;
        int rootValues = 0;

        try (JsonParser parser = FACTORY.createParser(new StringReader(json))) {
            JsonToken token;
            while ((token = parser.nextToken()) != null) {
                switch (token) {
                    case START_OBJECT, START_ARRAY -> {
                        if (depth == 0) {
                            rootValues++;
                        } else if (expectValue) {
                            if (pretty) {
                                out.append(' ');
                            }
                            expectValue = false;
                        } else {
                            if (Boolean.TRUE.equals(hasContent.peek())) {
                                out.append(',');
                            }
                            newline(out, depth, indentUnit);
                        }
                        out.append(token == JsonToken.START_OBJECT ? '{' : '[');
                        hasContent.push(Boolean.FALSE);
                        depth++;
                    }
                    case END_OBJECT, END_ARRAY -> {
                        boolean hadContent = Boolean.TRUE.equals(hasContent.pop());
                        depth--;
                        if (hadContent) {
                            newline(out, depth, indentUnit);
                        }
                        out.append(token == JsonToken.END_OBJECT ? '}' : ']');
                        if (depth > 0) {
                            markContent(hasContent);
                        }
                        expectValue = false;
                    }
                    case FIELD_NAME -> {
                        if (Boolean.TRUE.equals(hasContent.peek())) {
                            out.append(',');
                        }
                        newline(out, depth, indentUnit);
                        appendQuoted(out, parser.currentName());
                        out.append(':');
                        expectValue = true;
                        markContent(hasContent);
                    }
                    default -> {
                        if (expectValue) {
                            if (pretty) {
                                out.append(' ');
                            }
                            expectValue = false;
                        } else if (depth > 0) {
                            if (Boolean.TRUE.equals(hasContent.peek())) {
                                out.append(',');
                            }
                            newline(out, depth, indentUnit);
                        } else {
                            rootValues++;
                        }
                        appendScalar(out, token, parser.getText());
                        if (depth > 0) {
                            markContent(hasContent);
                        }
                    }
                }
            }
        } catch (JsonParseException e) {
            throw toFriendly(e);
        } catch (IOException e) {
            throw new JsonFormatException("读取内容失败：" + e.getMessage(), 1, 1);
        }

        if (rootValues == 0) {
            throw new JsonFormatException("内容为空或不是合法的 JSON", 1, 1);
        }
        if (rootValues > 1) {
            throw new JsonFormatException("只允许一个顶层 JSON 值，检测到 " + rootValues + " 个", 1, 1);
        }
        return out.toString();
    }

    private static void markContent(Deque<Boolean> stack) {
        if (!stack.isEmpty()) {
            stack.pop();
            stack.push(Boolean.TRUE);
        }
    }

    private static void newline(StringBuilder out, int depth, String indentUnit) {
        if (indentUnit == null) {
            return;
        }
        out.append('\n');
        for (int i = 0; i < depth; i++) {
            out.append(indentUnit);
        }
    }

    private static void appendScalar(StringBuilder out, JsonToken token, String text) {
        switch (token) {
            case VALUE_STRING -> appendQuoted(out, text);
            case VALUE_TRUE -> out.append("true");
            case VALUE_FALSE -> out.append("false");
            case VALUE_NULL -> out.append("null");
            default -> out.append(text);
        }
    }

    /** 按 JSON 规范转义字符串内容。包级可见：{@link JsonDocument} 写回文本时复用。 */
    static void appendQuoted(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    private static JsonFormatException toFriendly(JsonProcessingException e) {
        JsonLocation location = e.getLocation();
        int line = location == null ? 1 : location.getLineNr();
        int column = location == null ? 1 : location.getColumnNr();
        return new JsonFormatException(translate(e.getOriginalMessage()), line, column);
    }

    /** 把 Jackson 的英文提示替换为中文关键词，未覆盖的部分保留原文以免误导。 */
    static String translate(String message) {
        if (message == null || message.isBlank()) {
            return "无法解析该 JSON";
        }
        return message
                .replace("Unexpected end-of-input", "内容意外结束")
                .replace("Unexpected character", "意外的字符")
                .replace("Unexpected close marker", "意外的闭合符号")
                .replace("was expecting", "此处应为")
                .replace("Expected space", "此处缺少空格")
                .replace("Duplicate field", "字段重复")
                .replace("Illegal character", "非法字符");
    }
}
