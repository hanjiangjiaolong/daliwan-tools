package com.daliwan.tool.core.sql;

import java.util.Locale;

/**
 * 一个绑定参数值。
 *
 * <p>MyBatis 日志里每个参数都带类型，例如 {@code 01(String)}、{@code 1(Long)}，
 * 类型决定了它拼进 SQL 时该不该加引号——这正是「自动填参」最容易出错的地方：
 * {@code 01(String)} 如果当成数字会变成 {@code 01}（甚至 {@code 1}），语义就错了。
 */
public record ParamValue(Kind kind, String text) {

    /** 值的类别，决定渲染方式。 */
    public enum Kind {
        /** 字符串、日期等需要加引号的文本。 */
        TEXT,
        /** 数字，原样输出。 */
        NUMBER,
        /** 布尔值。 */
        BOOLEAN,
        /** SQL NULL。 */
        NULL
    }

    public static final ParamValue NULL_VALUE = new ParamValue(Kind.NULL, "");

    public static ParamValue text(String value) {
        return new ParamValue(Kind.TEXT, value == null ? "" : value);
    }

    public static ParamValue number(String value) {
        return new ParamValue(Kind.NUMBER, value);
    }

    /**
     * 按 MyBatis 日志里的「值 + 类型」构造参数。
     *
     * @param rawValue 括号前的原始文本，可能为空字符串
     * @param typeName 括号里的类型名，如 {@code String}、{@code Long}、{@code Timestamp}；
     *                 没有类型信息时传 {@code null} 或空串
     */
    public static ParamValue fromLog(String rawValue, String typeName) {
        String value = rawValue == null ? "" : rawValue.strip();
        if (value.equalsIgnoreCase("null")) {
            return NULL_VALUE;
        }
        String type = typeName == null ? "" : typeName.strip().toLowerCase(Locale.ROOT);
        if (type.isEmpty()) {
            return guess(value);
        }
        if (isBoolean(type)) {
            return new ParamValue(Kind.BOOLEAN, isTruthy(value) ? "true" : "false");
        }
        if (isNumber(type) && isPlainNumber(value)) {
            return number(value);
        }
        if (isTemporal(type)) {
            // Timestamp.toString() 会带毫秒尾巴，例如 2020-01-10 15:00:00.0
            return text(trimFraction(value));
        }
        return text(value);
    }

    /** 没有类型信息时的猜测：纯数字当数字，其余一律当文本（宁可多引号，也不破坏语义）。 */
    private static ParamValue guess(String value) {
        if (isPlainNumber(value) && !hasLeadingZero(value)) {
            return number(value);
        }
        return text(value);
    }

    /**
     * 渲染成 SQL 字面量。
     *
     * @param dialect 目标方言，影响布尔值与字符串转义
     */
    public String render(SqlDialect dialect) {
        return switch (kind) {
            case NULL -> "NULL";
            case NUMBER -> text;
            case BOOLEAN -> booleanLiteral(dialect);
            case TEXT -> quote(text, dialect);
        };
    }

    private String booleanLiteral(SqlDialect dialect) {
        boolean truthy = isTruthy(text);
        if (dialect == SqlDialect.SQLSERVER) {
            return truthy ? "1" : "0";
        }
        return truthy ? "TRUE" : "FALSE";
    }

    /**
     * 字符串字面量：单引号内的单引号写成两个。
     *
     * <p>MySQL 默认把反斜杠当转义符，所以反斜杠也要成对写；
     * 其他方言里反斜杠是普通字符，不能动。
     */
    static String quote(String value, SqlDialect dialect) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        out.append('\'');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\'' -> out.append("''");
                case '\\' -> out.append(dialect == SqlDialect.MYSQL ? "\\\\" : "\\");
                case '\n' -> out.append(dialect == SqlDialect.MYSQL ? "\\n" : "\n");
                case '\r' -> out.append(dialect == SqlDialect.MYSQL ? "\\r" : "\r");
                case '\t' -> out.append(dialect == SqlDialect.MYSQL ? "\\t" : "\t");
                default -> out.append(c);
            }
        }
        out.append('\'');
        return out.toString();
    }

    // ------------------------------------------------------------ 类型判定

    private static boolean isBoolean(String type) {
        return type.contains("boolean") || type.equals("bool");
    }

    private static boolean isNumber(String type) {
        return type.contains("int") || type.contains("long") || type.contains("short")
                || type.contains("byte") || type.contains("double") || type.contains("float")
                || type.contains("decimal") || type.contains("number") || type.contains("numeric");
    }

    private static boolean isTemporal(String type) {
        return type.contains("date") || type.contains("time");
    }

    static boolean isPlainNumber(String value) {
        return value.matches("-?\\d+(\\.\\d+)?") || value.matches("-?\\d+(\\.\\d+)?[eE][-+]?\\d+");
    }

    /** 带前导零的整数（如 01）多半是字符串编码，不能当数字。 */
    private static boolean hasLeadingZero(String value) {
        String digits = value.startsWith("-") ? value.substring(1) : value;
        return digits.length() > 1 && digits.charAt(0) == '0' && digits.charAt(1) != '.';
    }

    static boolean isTruthy(String value) {
        String v = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        return v.equals("true") || v.equals("1") || v.equals("y") || v.equals("yes");
    }

    private static String trimFraction(String value) {
        return value.endsWith(".0") ? value.substring(0, value.length() - 2) : value;
    }
}
