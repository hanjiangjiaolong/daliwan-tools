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
import java.util.List;

/**
 * 把 JSON 文本解析为 {@link JsonTreeNode} 树，供界面以树形展示。
 *
 * <p>基于 Jackson 流式 API 递归下降构建，不依赖 databind。
 */
public final class JsonTreeBuilder {

    /** 单次解析允许的最大节点数，防止超深/超大文本撑爆界面。 */
    private static final int MAX_NODES = 200_000;

    private static final JsonFactory FACTORY = JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder()
                    .maxNestingDepth(2000)
                    .maxNumberLength(10_000)
                    .build())
            .build();

    private JsonTreeBuilder() {
    }

    /**
     * 解析 JSON 文本。
     *
     * @param json 原始 JSON
     * @return 根节点，路径以 {@code $} 开头
     * @throws JsonFormatException 语法错误、内容为空或节点过多
     */
    public static JsonTreeNode build(String json) throws JsonFormatException {
        if (json == null || json.isBlank()) {
            throw new JsonFormatException("内容为空", 1, 1);
        }
        int[] counter = {0};
        try (JsonParser parser = FACTORY.createParser(new StringReader(json))) {
            JsonToken token = parser.nextToken();
            if (token == null) {
                throw new JsonFormatException("内容为空", 1, 1);
            }
            JsonTreeNode root = parse(parser, token, "根节点", "$", List.of(), counter);
            if (parser.nextToken() != null) {
                throw new JsonFormatException("只允许一个顶层 JSON 值", 1, 1);
            }
            return root;
        } catch (JsonParseException e) {
            throw toFriendly(e);
        } catch (IOException e) {
            throw new JsonFormatException("读取内容失败：" + e.getMessage(), 1, 1);
        }
    }

    private static JsonTreeNode parse(JsonParser parser, JsonToken token,
                                      String key, String path, List<Integer> segments,
                                      int[] counter)
            throws IOException, JsonFormatException {
        if (++counter[0] > MAX_NODES) {
            throw new JsonFormatException("节点数量超过 " + MAX_NODES + "，请使用较小的 JSON", 1, 1);
        }
        switch (token) {
            case START_OBJECT -> {
                List<JsonTreeNode> children = new ArrayList<>();
                int index = 0;
                while (parser.nextToken() == JsonToken.FIELD_NAME) {
                    String field = parser.currentName();
                    children.add(parse(parser, parser.nextToken(), field, childPath(path, field),
                            append(segments, index), counter));
                    index++;
                }
                return JsonTreeNode.container(key, path, JsonTreeNode.Type.OBJECT, segments, children);
            }
            case START_ARRAY -> {
                List<JsonTreeNode> children = new ArrayList<>();
                int index = 0;
                JsonToken child = parser.nextToken();
                while (child != JsonToken.END_ARRAY) {
                    if (child == null) {
                        throw new JsonFormatException("数组未闭合", 1, 1);
                    }
                    children.add(parse(parser, child, "[" + index + "]", path + "[" + index + "]",
                            append(segments, index), counter));
                    index++;
                    child = parser.nextToken();
                }
                return JsonTreeNode.container(key, path, JsonTreeNode.Type.ARRAY, segments, children);
            }
            case VALUE_STRING -> {
                return JsonTreeNode.scalar(key, path, JsonTreeNode.Type.STRING,
                        "\"" + parser.getText() + "\"", segments);
            }
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> {
                return JsonTreeNode.scalar(key, path, JsonTreeNode.Type.NUMBER, parser.getText(), segments);
            }
            case VALUE_TRUE -> {
                return JsonTreeNode.scalar(key, path, JsonTreeNode.Type.BOOLEAN, "true", segments);
            }
            case VALUE_FALSE -> {
                return JsonTreeNode.scalar(key, path, JsonTreeNode.Type.BOOLEAN, "false", segments);
            }
            case VALUE_NULL -> {
                return JsonTreeNode.scalar(key, path, JsonTreeNode.Type.NULL, "null", segments);
            }
            default -> {
                return JsonTreeNode.scalar(key, path, JsonTreeNode.Type.NULL,
                        String.valueOf(parser.getText()), segments);
            }
        }
    }

    /** 在父路径末尾追加一个下标，生成子节点的定位路径。 */
    private static List<Integer> append(List<Integer> parent, int index) {
        List<Integer> next = new ArrayList<>(parent.size() + 1);
        next.addAll(parent);
        next.add(index);
        return next;
    }

    /** 键名符合标识符习惯时用点号，否则用方括号加引号。 */
    private static String childPath(String parent, String field) {
        if (field.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
            return parent + "." + field;
        }
        return parent + "['" + field.replace("'", "\\'") + "']";
    }

    private static JsonFormatException toFriendly(JsonProcessingException e) {
        JsonLocation location = e.getLocation();
        int line = location == null ? 1 : location.getLineNr();
        int column = location == null ? 1 : location.getColumnNr();
        return new JsonFormatException(JsonFormatter.translate(e.getOriginalMessage()), line, column);
    }
}
