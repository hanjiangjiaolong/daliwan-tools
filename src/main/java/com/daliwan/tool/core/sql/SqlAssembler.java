package com.daliwan.tool.core.sql;

import java.util.ArrayList;
import java.util.List;

/**
 * 把 {@code ?} 占位符换成实际参数，并把语句整理成可读的样子。
 *
 * <p>替换时要避开字符串字面量与注释里的问号——{@code where remark = '还有?'} 里的
 * 问号不是占位符，按顺序硬换会把整条 SQL 拼错。
 */
public final class SqlAssembler {

    /** 一次拼接的完整结果。 */
    public record Result(String text, int statements, int parameters,
                         List<String> sources, List<String> warnings) {
        public Result {
            sources = List.copyOf(sources);
            warnings = List.copyOf(warnings);
        }
    }

    /** 单条语句的填充结果：没有对应参数的 {@code ?} 原样保留，并记在 missing 里。 */
    public record Fill(String sql, int filled, int missing, int unused) {
    }

    private SqlAssembler() {
    }

    /**
     * 解析日志并完成拼接。
     *
     * @param log       原始日志
     * @param dialect   目标方言
     * @param uppercase 关键字是否大写
     * @param pretty    是否格式化
     */
    public static Result assemble(String log, SqlDialect dialect, boolean uppercase, boolean pretty) {
        List<MybatisLogParser.Statement> parsed = MybatisLogParser.parse(log);
        if (parsed.isEmpty()) {
            return new Result("", 0, 0, List.of(),
                    List.of("没有识别到 MyBatis 的 Preparing / Parameters 行"));
        }

        List<String> warnings = new ArrayList<>();
        List<String> sources = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        int filledTotal = 0;
        boolean multiple = parsed.size() > 1;

        for (int index = 0; index < parsed.size(); index++) {
            MybatisLogParser.Statement statement = parsed.get(index);
            Fill fill = fill(statement.sql(), statement.params(), dialect);
            filledTotal += fill.filled();

            String label = multiple ? "第 " + (index + 1) + " 条语句" : "这条语句";
            if (fill.missing() > 0) {
                warnings.add(label + "有 " + fill.missing() + " 个 ? 没有对应参数，已原样保留");
            }
            if (fill.unused() > 0) {
                warnings.add(label + "多出 " + fill.unused() + " 个参数没有用上");
            }

            String sql = pretty ? SqlBeautifier.format(fill.sql(), dialect, uppercase) : fill.sql();
            if (multiple) {
                sql = withSemicolon(sql);
            }
            if (!text.isEmpty()) {
                text.append("\n\n");
            }
            text.append(sql);

            if (!statement.source().isBlank()) {
                sources.add(statement.source());
            }
        }
        return new Result(text.toString().strip(), parsed.size(), filledTotal, sources, warnings);
    }

    /**
     * 把参数按顺序填进 {@code ?}。
     *
     * @return 填充后的 SQL 与统计
     */
    public static Fill fill(String sql, List<ParamValue> params, SqlDialect dialect) {
        if (sql == null || sql.isEmpty()) {
            return new Fill("", 0, 0, params == null ? 0 : params.size());
        }
        List<ParamValue> values = params == null ? List.of() : params;
        StringBuilder out = new StringBuilder(sql.length() + values.size() * 16);
        int used = 0;
        int missing = 0;
        boolean single = false;
        boolean doubleQuote = false;
        boolean backtick = false;
        boolean bracket = false;
        boolean lineComment = false;
        boolean blockComment = false;

        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';

            if (lineComment) {
                out.append(c);
                if (c == '\n') {
                    lineComment = false;
                }
                continue;
            }
            if (blockComment) {
                out.append(c);
                if (c == '*' && next == '/') {
                    out.append(next);
                    i++;
                    blockComment = false;
                }
                continue;
            }
            if (single) {
                out.append(c);
                if (c == '\\' && dialect == SqlDialect.MYSQL && next != '\0') {
                    out.append(next);
                    i++;
                } else if (c == '\'') {
                    if (next == '\'') {
                        out.append(next);
                        i++;
                    } else {
                        single = false;
                    }
                }
                continue;
            }
            if (doubleQuote) {
                out.append(c);
                if (c == '"') {
                    if (next == '"') {
                        out.append(next);
                        i++;
                    } else {
                        doubleQuote = false;
                    }
                }
                continue;
            }
            if (backtick) {
                out.append(c);
                if (c == '`') {
                    backtick = false;
                }
                continue;
            }
            if (bracket) {
                out.append(c);
                if (c == ']') {
                    bracket = false;
                }
                continue;
            }

            if (c == '-' && next == '-') {
                lineComment = true;
                out.append(c).append(next);
                i++;
                continue;
            }
            if (c == '/' && next == '*') {
                blockComment = true;
                out.append(c).append(next);
                i++;
                continue;
            }
            if (c == '#' && dialect == SqlDialect.MYSQL) {
                lineComment = true;
                out.append(c);
                continue;
            }
            if (c == '\'') {
                single = true;
                out.append(c);
                continue;
            }
            if (c == '"') {
                doubleQuote = true;
                out.append(c);
                continue;
            }
            if (c == '`') {
                backtick = true;
                out.append(c);
                continue;
            }
            if (c == '[' && dialect == SqlDialect.SQLSERVER) {
                // 只有 SQL Server 用 [] 包标识符；PostgreSQL 的 ARRAY[?] 里是真占位符
                bracket = true;
                out.append(c);
                continue;
            }
            if (c == '?') {
                if (used < values.size()) {
                    out.append(values.get(used).render(dialect));
                    used++;
                } else {
                    out.append('?');
                    missing++;
                }
                continue;
            }
            out.append(c);
        }
        return new Fill(out.toString(), used, missing, values.size() - used);
    }

    private static String withSemicolon(String sql) {
        String text = sql.strip();
        return text.endsWith(";") ? text : text + ";";
    }
}
