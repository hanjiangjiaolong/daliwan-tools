package com.daliwan.tool.core.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * SQL 语法着色用的分词器。
 *
 * <p>只做「哪一段是什么」的判定，不碰 JavaFX：颜色由界面层按 {@link Kind} 映射到样式类，
 * 这样主题切换和单元测试都不会牵进 UI 依赖。
 *
 * <p>扫描是逐字符走一遍，字符串、注释、引号标识符都单独成段——正则版在这里容易漏掉
 * 字符串里的 {@code --} 或注释里的引号，一旦错位后面的颜色就全花了。
 */
public final class SqlHighlighter {

    /** 着色类别。{@code PLAIN} 表示不需要着色，调用方直接跳过。 */
    public enum Kind {
        KEYWORD, FUNCTION, STRING, NUMBER, LITERAL, COMMENT, QUOTED, PARAM, OPERATOR, PLAIN
    }

    /** 一段带类别的位置区间，{@code [start, end)}，按 start 升序且互不重叠。 */
    public record Token(int start, int end, Kind kind) {
        public int length() {
            return end - start;
        }
    }

    /**
     * SQL 保留字（含常用类型与谓词）。
     *
     * <p>不做方言区分：多认几个词只是颜色不同，误判成关键字的代价远小于漏判。
     */
    private static final Set<String> KEYWORDS = Set.of(
            "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "IN", "IS", "AS", "ON",
            "JOIN", "INNER", "LEFT", "RIGHT", "FULL", "OUTER", "CROSS", "USING",
            "GROUP", "BY", "ORDER", "HAVING", "LIMIT", "OFFSET", "UNION", "ALL", "DISTINCT",
            "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE", "REPLACE", "MERGE",
            "CREATE", "ALTER", "DROP", "TRUNCATE", "TABLE", "VIEW", "INDEX", "PRIMARY", "KEY",
            "FOREIGN", "REFERENCES", "CONSTRAINT", "UNIQUE", "DEFAULT", "CHECK", "AUTO_INCREMENT",
            "CASE", "WHEN", "THEN", "ELSE", "END", "EXISTS", "BETWEEN", "LIKE", "ILIKE",
            "ASC", "DESC", "WITH", "RECURSIVE", "OVER", "PARTITION", "WINDOW", "RETURNING",
            "INTERVAL", "COALESCE", "IF", "ELSEIF", "BEGIN", "COMMIT", "ROLLBACK",
            "INT", "INTEGER", "BIGINT", "SMALLINT", "TINYINT", "DECIMAL", "NUMERIC",
            "FLOAT", "DOUBLE", "REAL", "CHAR", "VARCHAR", "TEXT", "DATE", "DATETIME",
            "TIMESTAMP", "TIME", "BOOLEAN", "BOOL", "BLOB", "CLOB", "JSON", "BINARY", "VARBINARY");

    /** {@code NULL} / {@code TRUE} / {@code FALSE} 单独一类，颜色与关键字区分开。 */
    private static final Set<String> LITERALS = Set.of("NULL", "TRUE", "FALSE");

    private SqlHighlighter() {
    }

    /**
     * 扫描一段 SQL。
     *
     * @param sql 任意文本，可为 {@code null}
     * @return 按位置升序的 token 列表；空白与普通标点不会出现在结果里
     */
    public static List<Token> scan(String sql) {
        if (sql == null || sql.isEmpty()) {
            return List.of();
        }
        List<Token> tokens = new ArrayList<>();
        int length = sql.length();
        int i = 0;
        while (i < length) {
            char c = sql.charAt(i);

            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            // 行注释：-- 后面跟空白或直接行尾（a--b 视为运算符，极少见但别搞错）
            if (c == '-' && i + 1 < length && sql.charAt(i + 1) == '-') {
                int end = sql.indexOf('\n', i);
                end = end < 0 ? length : end;
                tokens.add(new Token(i, end, Kind.COMMENT));
                i = end;
                continue;
            }

            // MySQL 的 # 注释：只在行首或空白之后生效，避免误伤 # 开头的标识符
            if (c == '#' && (i == 0 || Character.isWhitespace(sql.charAt(i - 1)))) {
                int end = sql.indexOf('\n', i);
                end = end < 0 ? length : end;
                tokens.add(new Token(i, end, Kind.COMMENT));
                i = end;
                continue;
            }

            if (c == '/' && i + 1 < length && sql.charAt(i + 1) == '*') {
                int end = sql.indexOf("*/", i + 2);
                end = end < 0 ? length : end + 2;
                tokens.add(new Token(i, end, Kind.COMMENT));
                i = end;
                continue;
            }

            if (c == '\'') {
                int end = skipQuoted(sql, i, '\'');
                tokens.add(new Token(i, end, Kind.STRING));
                i = end;
                continue;
            }

            // 反引号（MySQL）与双引号（标准 / PostgreSQL）里的内容按标识符着色
            if (c == '`' || c == '"') {
                int end = skipQuoted(sql, i, c);
                tokens.add(new Token(i, end, Kind.QUOTED));
                i = end;
                continue;
            }

            if (c == '?') {
                tokens.add(new Token(i, i + 1, Kind.PARAM));
                i++;
                continue;
            }

            if (isDigit(c) || (c == '.' && i + 1 < length && isDigit(sql.charAt(i + 1)))) {
                int end = skipNumber(sql, i);
                tokens.add(new Token(i, end, Kind.NUMBER));
                i = end;
                continue;
            }

            if (isIdentifierStart(c)) {
                int end = i + 1;
                while (end < length && isIdentifierPart(sql.charAt(end))) {
                    end++;
                }
                Kind kind = identifierKind(sql, i, end);
                if (kind != Kind.PLAIN) {
                    tokens.add(new Token(i, end, kind));
                }
                i = end;
                continue;
            }

            if (isOperator(c)) {
                int end = i + 1;
                while (end < length && isOperator(sql.charAt(end)) && end - i < 3) {
                    end++;
                }
                tokens.add(new Token(i, end, Kind.OPERATOR));
                i = end;
                continue;
            }

            i++;
        }
        return tokens;
    }

    /** 标识符归类：保留字 → 字面量 → 函数（后面紧跟左括号）→ 普通标识符。 */
    private static Kind identifierKind(String sql, int start, int end) {
        String word = sql.substring(start, end);
        String upper = word.toUpperCase(Locale.ROOT);
        if (LITERALS.contains(upper)) {
            return Kind.LITERAL;
        }
        if (KEYWORDS.contains(upper)) {
            return Kind.KEYWORD;
        }
        if (nextNonSpace(sql, end) == '(') {
            return Kind.FUNCTION;
        }
        return Kind.PLAIN;
    }

    private static char nextNonSpace(String sql, int from) {
        for (int i = from; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (!Character.isWhitespace(c)) {
                return c;
            }
        }
        return '\0';
    }

    /**
     * 跳到引号结束之后。
     *
     * <p>SQL 用连写两个引号表示转义（{@code 'O''Brien'}），遇到就继续走；
     * 反斜杠转义在 MySQL 里也常见，一并吃掉，宁可多吞一个字符也别提前收尾。
     */
    private static int skipQuoted(String sql, int start, char quote) {
        int i = start + 1;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (c == '\\' && i + 1 < sql.length()) {
                i += 2;
                continue;
            }
            if (c == quote) {
                if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        return sql.length();
    }

    private static int skipNumber(String sql, int start) {
        int i = start;
        int length = sql.length();
        while (i < length && isDigit(sql.charAt(i))) {
            i++;
        }
        if (i < length && sql.charAt(i) == '.') {
            i++;
            while (i < length && isDigit(sql.charAt(i))) {
                i++;
            }
        }
        if (i < length && (sql.charAt(i) == 'e' || sql.charAt(i) == 'E')) {
            int mark = i;
            i++;
            if (i < length && (sql.charAt(i) == '+' || sql.charAt(i) == '-')) {
                i++;
            }
            if (i < length && isDigit(sql.charAt(i))) {
                while (i < length && isDigit(sql.charAt(i))) {
                    i++;
                }
            } else {
                i = mark;
            }
        }
        return i;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '@';
    }

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '@';
    }

    private static boolean isOperator(char c) {
        return c == '=' || c == '<' || c == '>' || c == '!' || c == '|' || c == '+' || c == '-'
                || c == '*' || c == '/' || c == '%' || c == '^' || c == '~';
    }
}
