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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把 JSON 转成「列 + 行」的二维表。
 *
 * <p>主要面向接口返回里的对象数组：列取所有对象键名的并集（按首次出现顺序），
 * 某行缺这个字段就留空。也接受顶层对象（当成只有一行）和标量数组（单列 {@code value}）。
 *
 * <p>嵌套的对象 / 数组不展开成多列，而是还原成紧凑 JSON 放在单元格里——
 * 展开成 {@code a.b.c} 一旦遇到数组就会无限膨胀，表格反而没法看。
 */
public final class JsonTable {

    /** 表格默认最多渲染的行数：再多界面会卡，超出的部分只提示不渲染。 */
    public static final int DEFAULT_MAX_ROWS = 5000;

    /** 列数上限，防止每个对象键名都不一样时列数爆炸。 */
    public static final int DEFAULT_MAX_COLUMNS = 200;

    /** 标量数组使用的列名。 */
    public static final String VALUE_COLUMN = "value";

    private static final JsonFactory FACTORY = JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder()
                    .maxNestingDepth(2000)
                    .maxNumberLength(10_000)
                    .build())
            .build();

    private JsonTable() {
    }

    /**
     * 二维表结果。
     *
     * @param columns    列名，顺序即界面列顺序
     * @param rows       每行按 {@code columns} 顺序排好的单元格文本
     * @param totalRows  解析到的总行数（可能大于 {@code rows.size()}）
     * @param truncatedRows    是否因为行数上限丢弃了部分行
     * @param truncatedColumns 是否因为列数上限丢弃了部分列
     */
    public record Table(List<String> columns, List<List<String>> rows, int totalRows,
                        boolean truncatedRows, boolean truncatedColumns) {

        public Table {
            columns = List.copyOf(columns);
            rows = List.copyOf(rows);
        }

        public boolean isEmpty() {
            return rows.isEmpty();
        }
    }

    public static Table build(String json) throws JsonFormatException {
        return build(json, DEFAULT_MAX_ROWS, DEFAULT_MAX_COLUMNS);
    }

    /**
     * 一个可以当表格的数组：它在哪里、多大规模。
     *
     * @param path        字段路径（如 {@code data.list}），顶层数组为空串
     * @param segments    在文档里的位置，用于按候选切换数据源
     * @param rows        元素个数
     * @param columns     键名并集的大小（标量数组为 1）
     * @param objectArray 是否为对象数组——比标量数组更像表格
     */
    public record Candidate(String path, List<Integer> segments, int rows, int columns,
                            boolean objectArray) {

        public Candidate {
            segments = List.copyOf(segments);
        }

        /** 下拉里显示的一行，如 {@code data.list · 3 行 × 7 列}。 */
        public String label() {
            String where = path.isEmpty() ? "顶层" : path;
            return where + " · " + rows + " 行 × " + columns + " 列";
        }

        @Override
        public String toString() {
            return label();
        }
    }

    /**
     * 自动找表格的结果。
     *
     * @param table      转换出的表
     * @param candidates 全文里所有可选的数据源，第一个是自动挑中的
     * @param chosen     自动挑中的那个的下标，{@code -1} 表示没有候选
     * @param document   解析后的文档，切换候选时用它取别的数组，省一次解析
     */
    public record AutoResult(Table table, List<Candidate> candidates, int chosen,
                             JsonDocument document) {

        public AutoResult {
            candidates = List.copyOf(candidates);
        }

        /** 数据来自哪个字段（如 {@code data.list}），顶层本身合适时为空串。 */
        public String sourcePath() {
            Candidate selected = selected();
            return selected == null ? "" : selected.path();
        }

        /** 当前选中的候选，没有候选时为 {@code null}。 */
        public Candidate selected() {
            return chosen >= 0 && chosen < candidates.size() ? candidates.get(chosen) : null;
        }
    }

    /** 收集候选时最多保留多少个，避免超大文档里为了一个下拉框遍历几千个数组。 */
    private static final int MAX_CANDIDATES = 300;

    /** 估算列名并集时最多看前多少个元素：同一数组里的对象字段基本一致，看一部分就够。 */
    private static final int COLUMN_SAMPLE = 100;

    /** 候选排序：对象数组优先，列多、行多，最后取层级浅的。 */
    private static final java.util.Comparator<Scored> PRIORITY =
            java.util.Comparator.comparing((Scored scored) -> !scored.candidate().objectArray())
                    .thenComparing(scored -> -scored.candidate().columns())
                    .thenComparing(scored -> -scored.candidate().rows())
                    .thenComparingInt(Scored::depth)
                    .thenComparing(scored -> scored.candidate().path());

    /** 候选 + 它在文档里的层级，层级只用于排序，不暴露给界面。 */
    private record Scored(Candidate candidate, int depth) {
    }

    /**
     * 自动寻找可当表格的数据并转换。
     *
     * <p>接口返回常常是 {@code {"code":0,"data":{"total":2,"list":[…]}}} 这种包裹结构，
     * 顶层对象只有一行、真正想看的数组在里层。这里按下面的顺序挑：
     * <ol>
     *   <li>顶层本身是数组 → 直接用（用户已经指明了数据在哪）</li>
     *   <li>顶层是对象 → 顺着对象成员往下找数组，<b>对象数组优先</b>，
     *       其次列数多的、行数多的、层级浅的</li>
     *   <li>一个数组都没有 → 退回「顶层对象当一行」，与 {@link #build(String)} 一致</li>
     * </ol>
     * 只沿对象链下钻、不进入数组内部：数组元素里的数组通常是某个单元格的内容，
     * 拿来当整张表既不符合意图，路径也会变成 {@code items[0].details} 这种没法看的形状。
     *
     * <p>所有候选都会带回来，界面可以让用户在它们之间切换——自动挑的只是「最像」，
     * 真实文档里同时有 {@code data.list} 和 {@code data.dict} 时，用户才知道要看哪个。
     */
    public static AutoResult buildAuto(String json) throws JsonFormatException {
        return buildAuto(json, DEFAULT_MAX_ROWS, DEFAULT_MAX_COLUMNS);
    }

    /** 同 {@link #buildAuto(String)}，可指定行列上限。 */
    public static AutoResult buildAuto(String json, int maxRows, int maxColumns)
            throws JsonFormatException {
        if (json == null || json.isBlank()) {
            throw new JsonFormatException("内容为空", 1, 1);
        }
        JsonDocument document = JsonDocument.parse(json);
        List<Scored> found = new ArrayList<>();
        collect(document.root(), "", List.of(), 0, found);
        if (found.isEmpty()) {
            // 顶层既不是对象也不是数组：交给 build 抛出同一个错误
            return new AutoResult(build(json, maxRows, maxColumns), List.of(), -1, document);
        }
        found.sort(PRIORITY);
        List<Candidate> candidates = new ArrayList<>(found.size());
        for (Scored scored : found) {
            candidates.add(scored.candidate());
        }
        Table table = buildAt(document, candidates.get(0), maxRows, maxColumns);
        return new AutoResult(table, candidates, 0, document);
    }

    /** 按候选切换数据源：把那个数组单独转成表格。 */
    public static Table buildAt(JsonDocument document, Candidate candidate) throws JsonFormatException {
        return buildAt(document, candidate, DEFAULT_MAX_ROWS, DEFAULT_MAX_COLUMNS);
    }

    /** 按候选切换数据源，可指定行列上限。 */
    public static Table buildAt(JsonDocument document, Candidate candidate, int maxRows, int maxColumns)
            throws JsonFormatException {
        JsonDocument.Node node = nodeAt(document.root(), candidate.segments());
        if (node == null || node.type() != JsonDocument.Type.ARRAY) {
            throw new JsonFormatException("这个位置已经不是数组了，请重新解析", 1, 1);
        }
        return build(JsonDocument.compact(node), maxRows, maxColumns);
    }

    /** 按位置路径取节点。 */
    private static JsonDocument.Node nodeAt(JsonDocument.Node root, List<Integer> segments) {
        JsonDocument.Node node = root;
        for (int index : segments) {
            if (!node.isContainer() || index < 0 || index >= node.childCount()) {
                return null;
            }
            node = node.childAt(index);
        }
        return node;
    }

    /**
     * 表格第 {@code index} 行对应的原始节点。
     *
     * <p>表格里一格只是一个字段的值，看不出这一行整体长什么样：字段多到要横向拖、
     * 某个字段又是嵌套对象时尤其明显。要把整行还原成 JSON，只能回到解析好的文档
     * 树里按位置取——不能拿单元格文本反拼，那一步已经把类型信息丢了
     * （数字、布尔、字符串在单元格里都是同一种文本，拼回去分不出 {@code 1} 和 {@code "1"}）。
     *
     * @param document  解析好的文档，{@link AutoResult#document()}
     * @param candidate 当前数据源；{@code null} 表示顶层被当成一行（全文没有数组时）
     * @param index     行下标，从 0 开始
     * @return 对应的节点；下标越界或数据源已失效时返回 {@code null}
     */
    public static JsonDocument.Node rowNode(JsonDocument document, Candidate candidate, int index) {
        if (document == null || index < 0) {
            return null;
        }
        if (candidate == null) {
            // 顶层对象当一行：整张表就它一个，第二行起没有对应物
            return index == 0 ? document.root() : null;
        }
        JsonDocument.Node array = nodeAt(document.root(), candidate.segments());
        if (array == null || array.type() != JsonDocument.Type.ARRAY || index >= array.childCount()) {
            return null;
        }
        return array.childAt(index);
    }

    /**
     * 沿对象链收集候选数组。
     *
     * <p>遇到数组就评估并停下，不深入其内部；遇到对象继续往下。
     */
    private static void collect(JsonDocument.Node node, String path, List<Integer> segments,
                                int depth, List<Scored> out) {
        if (out.size() >= MAX_CANDIDATES) {
            return;
        }
        if (node.type() == JsonDocument.Type.ARRAY) {
            out.add(new Scored(evaluate(node, path, segments), depth));
            return;
        }
        if (node.type() != JsonDocument.Type.OBJECT) {
            return;
        }
        for (int i = 0; i < node.childCount(); i++) {
            List<Integer> childSegments = new ArrayList<>(segments.size() + 1);
            childSegments.addAll(segments);
            childSegments.add(i);
            collect(node.childAt(i), joinPath(path, node.keyAt(i)), childSegments, depth + 1, out);
        }
    }

    private static Candidate evaluate(JsonDocument.Node array, String path, List<Integer> segments) {
        int rows = array.childCount();
        int objects = 0;
        Set<String> keys = new LinkedHashSet<>();
        int sample = Math.min(rows, COLUMN_SAMPLE);
        for (int i = 0; i < sample; i++) {
            JsonDocument.Node element = array.childAt(i);
            if (element.type() == JsonDocument.Type.OBJECT) {
                objects++;
                keys.addAll(element.keys());
            }
        }
        // 前 100 个都是标量、而整体不止这么点时，不能断定它就是标量数组
        boolean objectArray = objects > 0;
        return new Candidate(path, segments, rows, objectArray ? keys.size() : 1, objectArray);
    }

    /** 拼一段给人看的字段路径，如 {@code data.list}、{@code data['odd key']}。 */
    private static String joinPath(String parent, String key) {
        String segment = key.matches("[A-Za-z_$][A-Za-z0-9_$]*")
                ? key
                : "['" + key.replace("'", "\\'") + "']";
        if (parent.isEmpty()) {
            return segment;
        }
        return segment.startsWith("[") ? parent + segment : parent + "." + segment;
    }

    /**
     * 解析 JSON 并转成表格。
     *
     * @throws JsonFormatException 语法错误、顶层不是数组 / 对象
     */
    public static Table build(String json, int maxRows, int maxColumns) throws JsonFormatException {
        if (json == null || json.isBlank()) {
            throw new JsonFormatException("内容为空", 1, 1);
        }
        List<LinkedHashMap<String, String>> records = new ArrayList<>();
        Set<String> columns = new LinkedHashSet<>();
        int total = 0;
        boolean truncatedRows = false;
        boolean truncatedColumns = false;

        try (JsonParser parser = FACTORY.createParser(new StringReader(json))) {
            JsonToken token = parser.nextToken();
            if (token == null) {
                throw new JsonFormatException("内容为空", 1, 1);
            }
            if (token == JsonToken.START_ARRAY) {
                JsonToken element = parser.nextToken();
                while (element != null && element != JsonToken.END_ARRAY) {
                    total++;
                    if (records.size() >= maxRows) {
                        truncatedRows = true;
                        parser.skipChildren();
                    } else if (element == JsonToken.START_OBJECT) {
                        LinkedHashMap<String, String> record = new LinkedHashMap<>();
                        truncatedColumns |= !readObject(parser, record, columns, maxColumns);
                        records.add(record);
                    } else {
                        // 标量数组（或数组套数组）：每行只有一个值
                        LinkedHashMap<String, String> record = new LinkedHashMap<>();
                        record.put(VALUE_COLUMN, readValue(parser, element));
                        columns.add(VALUE_COLUMN);
                        records.add(record);
                    }
                    element = parser.nextToken();
                }
            } else if (token == JsonToken.START_OBJECT) {
                LinkedHashMap<String, String> record = new LinkedHashMap<>();
                truncatedColumns = !readObject(parser, record, columns, maxColumns);
                records.add(record);
                total = 1;
            } else if (token == JsonToken.VALUE_NULL) {
                throw new JsonFormatException("顶层是 null，没有可以列出的内容", 1, 1);
            } else {
                throw new JsonFormatException("顶层需要是数组或对象，当前是一个标量", 1, 1);
            }

            if (parser.nextToken() != null) {
                throw new JsonFormatException("只允许一个顶层 JSON 值", 1, 1);
            }
        } catch (JsonParseException e) {
            throw toFriendly(e);
        } catch (IOException e) {
            throw new JsonFormatException("读取内容失败：" + e.getMessage(), 1, 1);
        }

        List<String> columnList = new ArrayList<>(columns);
        List<List<String>> rows = new ArrayList<>(records.size());
        for (Map<String, String> record : records) {
            List<String> row = new ArrayList<>(columnList.size());
            for (String column : columnList) {
                row.add(record.getOrDefault(column, ""));
            }
            rows.add(row);
        }
        return new Table(columnList, rows, total, truncatedRows, truncatedColumns);
    }

    /**
     * 读取一个对象的所有字段。
     *
     * @return false 表示遇到了超出列数上限的字段（已跳过）
     */
    private static boolean readObject(JsonParser parser, Map<String, String> record,
                                      Set<String> columns, int maxColumns) throws IOException {
        boolean complete = true;
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String name = parser.currentName();
            JsonToken value = parser.nextToken();
            if (columns.contains(name)) {
                record.put(name, readValue(parser, value));
            } else if (columns.size() < maxColumns) {
                columns.add(name);
                record.put(name, readValue(parser, value));
            } else {
                complete = false;
                parser.skipChildren();
            }
        }
        return complete;
    }

    /** 读取一个值：标量取文本，容器还原成紧凑 JSON。 */
    private static String readValue(JsonParser parser, JsonToken token) throws IOException {
        if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
            StringBuilder out = new StringBuilder();
            dense(parser, token, out);
            return out.toString();
        }
        return scalar(parser, token);
    }

    private static String scalar(JsonParser parser, JsonToken token) throws IOException {
        if (token == null) {
            return "";
        }
        return switch (token) {
            case VALUE_NULL -> "null";
            case VALUE_STRING, VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT, VALUE_TRUE, VALUE_FALSE ->
                    parser.getText();
            default -> "";
        };
    }

    /** 把容器节点重新拼成紧凑 JSON 文本。 */
    private static void dense(JsonParser parser, JsonToken token, StringBuilder out)
            throws IOException {
        switch (token) {
            case START_OBJECT -> {
                out.append('{');
                boolean first = true;
                while (parser.nextToken() == JsonToken.FIELD_NAME) {
                    if (!first) {
                        out.append(',');
                    }
                    first = false;
                    appendQuoted(out, parser.currentName());
                    out.append(':');
                    dense(parser, parser.nextToken(), out);
                }
                out.append('}');
            }
            case START_ARRAY -> {
                out.append('[');
                boolean first = true;
                JsonToken element = parser.nextToken();
                while (element != null && element != JsonToken.END_ARRAY) {
                    if (!first) {
                        out.append(',');
                    }
                    first = false;
                    dense(parser, element, out);
                    element = parser.nextToken();
                }
                out.append(']');
            }
            case VALUE_STRING -> appendQuoted(out, parser.getText());
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT, VALUE_TRUE, VALUE_FALSE ->
                    out.append(parser.getText());
            case VALUE_NULL -> out.append("null");
            default -> {
                // 其余 token 不会出现在值的位置上
            }
        }
    }

    private static void appendQuoted(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
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

    /**
     * 导出 CSV 文本（不含 BOM，写文件时由调用方决定）。
     *
     * <p>RFC 4180：含逗号、引号、换行的单元格用双引号包裹，内部引号写两遍。
     */
    public static String toCsv(Table table) {
        StringBuilder out = new StringBuilder();
        appendCsvRow(out, table.columns());
        for (List<String> row : table.rows()) {
            appendCsvRow(out, row);
        }
        return out.toString();
    }

    private static void appendCsvRow(StringBuilder out, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            String cell = cells.get(i) == null ? "" : cells.get(i);
            if (needsQuote(cell)) {
                out.append('"').append(cell.replace("\"", "\"\"")).append('"');
            } else {
                out.append(cell);
            }
        }
        out.append("\r\n");
    }

    private static boolean needsQuote(String cell) {
        return cell.indexOf(',') >= 0 || cell.indexOf('"') >= 0
                || cell.indexOf('\n') >= 0 || cell.indexOf('\r') >= 0;
    }

    private static JsonFormatException toFriendly(JsonProcessingException e) {
        JsonLocation location = e.getLocation();
        int line = location == null ? 1 : location.getLineNr();
        int column = location == null ? 1 : location.getColumnNr();
        return new JsonFormatException(JsonFormatter.translate(e.getOriginalMessage()), line, column);
    }
}
