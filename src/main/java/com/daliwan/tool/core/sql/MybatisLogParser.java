package com.daliwan.tool.core.sql;

import java.util.ArrayList;
import java.util.List;

/**
 * 从 MyBatis 的 DEBUG 日志里还原出「SQL 模板 + 参数值」。
 *
 * <p>能识别的就是控制台里那两行：
 * <pre>
 * ==>  Preparing: select * from t_user where delete_flag = ? and status = ?
 * ==> Parameters: N(String), 01(String)
 * </pre>
 *
 * <p>日志前面带时间戳、级别、Logger 名都没关系——只按 {@code Preparing:} /
 * {@code Parameters:} 这两个标记定位，前面的内容当来源信息保留下来。
 * 一次粘贴多段日志（例如批量插入）会解析成多条语句。
 */
public final class MybatisLogParser {

    /** 一条还原出来的语句。 */
    public record Statement(String source, String sql, List<ParamValue> params) {
        public Statement {
            params = List.copyOf(params);
        }
    }

    private static final String PREPARING = "Preparing:";
    private static final String PARAMETERS = "Parameters:";

    private MybatisLogParser() {
    }

    /**
     * 解析整段日志。
     *
     * @param log 原始日志文本，可以含多段
     * @return 按出现顺序排列的语句；识别不到时返回空列表
     */
    public static List<Statement> parse(String log) {
        List<Statement> statements = new ArrayList<>();
        if (log == null || log.isBlank()) {
            return statements;
        }

        Collector collector = new Collector();
        for (String rawLine : log.split("\\R")) {
            if (rawLine.isBlank()) {
                continue;
            }
            int preparingAt = rawLine.indexOf(PREPARING);
            if (preparingAt >= 0) {
                collector.flush(statements);
                collector.source = sourceOf(rawLine.substring(0, preparingAt));
                collector.sql.append(rawLine.substring(preparingAt + PREPARING.length()).strip());
                collector.collecting = true;
                continue;
            }

            int parametersAt = rawLine.indexOf(PARAMETERS);
            if (parametersAt >= 0) {
                if (collector.collecting) {
                    collector.params = parseParams(rawLine.substring(parametersAt + PARAMETERS.length()));
                    collector.collecting = false;
                }
                continue;
            }

            String message = stripLogPrefix(rawLine);
            if (message.isEmpty()) {
                continue;
            }
            if (message.startsWith("<==>")) {
                continue;
            }
            if (message.startsWith("==>") || message.startsWith("<==")) {
                // Row / Total 之类的后续事件，说明 SQL 已经结束
                collector.collecting = false;
                continue;
            }
            if (collector.collecting) {
                // 日志被包装工具折行时，把续行接回去
                if (collector.sql.length() > 0) {
                    collector.sql.append(' ');
                }
                collector.sql.append(message);
            }
        }
        collector.flush(statements);
        return statements;
    }

    // ------------------------------------------------------------ 参数解析

    /**
     * 解析 {@code Parameters:} 后面的内容。
     *
     * <p>格式是「值(类型)」用逗号连接，值本身可能带括号、逗号甚至方括号
     * （集合参数会打印成 {@code [1, 2](ArrayList)}）。集合按元素展开，
     * 因为 foreach 生成的 SQL 里每个元素各占一个 {@code ?}。
     */
    public static List<ParamValue> parseParams(String text) {
        List<ParamValue> params = new ArrayList<>();
        if (text == null) {
            return params;
        }
        String rest = text.strip();
        int i = 0;
        while (i < rest.length()) {
            while (i < rest.length() && (Character.isWhitespace(rest.charAt(i)) || rest.charAt(i) == ',')) {
                i++;
            }
            if (i >= rest.length()) {
                break;
            }

            // null 参数没有类型括号，得单独认
            if (rest.regionMatches(true, i, "null", 0, 4) && isValueBoundary(rest, i + 4)) {
                params.add(ParamValue.NULL_VALUE);
                i += 4;
                continue;
            }

            int open = findTypeOpen(rest, i);
            if (open < 0) {
                // 没有类型信息，整段当一个值
                params.add(ParamValue.fromLog(rest.substring(i), null));
                break;
            }
            int close = rest.indexOf(')', open);
            String value = rest.substring(i, open).strip();
            String type = rest.substring(open + 1, close).strip();
            params.addAll(fromValue(value, type));
            i = close + 1;
        }
        return params;
    }

    private static List<ParamValue> fromValue(String value, String type) {
        boolean collection = value.startsWith("[") && value.endsWith("]");
        if (collection && !type.toLowerCase().contains("map") && !value.contains("=")) {
            List<String> items = splitTopLevel(value.substring(1, value.length() - 1));
            if (!items.isEmpty()) {
                List<ParamValue> expanded = new ArrayList<>(items.size());
                for (String item : items) {
                    expanded.add(elementValue(item));
                }
                return expanded;
            }
        }
        return List.of(ParamValue.fromLog(value, type));
    }

    /** 集合元素没有单独类型：能当数字就当数字，其余当文本。 */
    private static ParamValue elementValue(String item) {
        String value = item.strip();
        if (value.equalsIgnoreCase("null")) {
            return ParamValue.NULL_VALUE;
        }
        return ParamValue.fromLog(value, null);
    }

    /** 找「值(类型)」里那个左括号：紧邻逗号或行尾的那个才可能是类型。 */
    private static int findTypeOpen(String text, int from) {
        int index = text.indexOf('(', from);
        while (index >= 0) {
            int close = text.indexOf(')', index);
            if (close < 0) {
                return -1;
            }
            String type = text.substring(index + 1, close).strip();
            if (looksLikeType(type) && isValueBoundary(text, close + 1)) {
                return index;
            }
            index = text.indexOf('(', close + 1);
        }
        return -1;
    }

    private static boolean looksLikeType(String type) {
        return type.matches("[A-Za-z_$][\\w.$\\[\\]]*(<.+>)?");
    }

    private static boolean isValueBoundary(String text, int index) {
        int i = index;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i >= text.length() || text.charAt(i) == ',';
    }

    /** 按顶层逗号切分，忽略括号、方括号与引号里的逗号。 */
    private static List<String> splitTopLevel(String text) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        char quote = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                current.append(c);
                if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            switch (c) {
                case '\'', '"' -> {
                    quote = c;
                    current.append(c);
                }
                case '(', '[', '{' -> {
                    depth++;
                    current.append(c);
                }
                case ')', ']', '}' -> {
                    depth--;
                    current.append(c);
                }
                case ',' -> {
                    if (depth == 0) {
                        parts.add(current.toString().strip());
                        current.setLength(0);
                    } else {
                        current.append(c);
                    }
                }
                default -> current.append(c);
            }
        }
        if (!current.isEmpty()) {
            parts.add(current.toString().strip());
        }
        parts.removeIf(String::isEmpty);
        return parts;
    }

    // ------------------------------------------------------------ 行解析

    /** 去掉「[时间] [DEBUG] Logger - 」这类前缀，拿到真正的消息体。 */
    static String stripLogPrefix(String line) {
        String text = stripBrackets(line).strip();
        int dash = text.indexOf(" - ");
        if (dash > 0) {
            String head = text.substring(0, dash).strip();
            String tail = text.substring(dash + 3).strip();
            // 只有前半段像 Logger 名时才敢切，否则 SQL 里的「a - b」会被误伤
            if (tail.startsWith("==>") || tail.startsWith("<==") || looksLikeLogger(head)) {
                text = tail;
            }
        }
        return text;
    }

    /** 从标记前面的内容里取出 Logger / 方法名，作为来源标注。 */
    static String sourceOf(String beforeMarker) {
        String text = stripBrackets(beforeMarker).strip();
        int dash = text.lastIndexOf(" - ");
        if (dash < 0) {
            dash = text.lastIndexOf(" : ");
        }
        if (dash > 0) {
            text = text.substring(0, dash).strip();
        }
        return text.strip();
    }

    private static boolean looksLikeLogger(String head) {
        return head.matches("[\\w.$\\[\\]]+(\\(\\d+\\))?");
    }

    /** 依次剥掉开头的 {@code [..]} 分组。 */
    private static String stripBrackets(String text) {
        String s = text.strip();
        while (s.startsWith("[")) {
            int close = s.indexOf(']');
            if (close < 0) {
                break;
            }
            s = s.substring(close + 1).strip();
        }
        return s;
    }

    /** 解析过程中的可变状态。 */
    private static final class Collector {
        private final StringBuilder sql = new StringBuilder();
        private List<ParamValue> params = new ArrayList<>();
        private String source = "";
        private boolean collecting;

        private void flush(List<Statement> out) {
            String text = sql.toString().strip();
            if (!text.isEmpty()) {
                out.add(new Statement(source, text, params));
            }
            sql.setLength(0);
            params = new ArrayList<>();
            source = "";
            collecting = false;
        }
    }
}
