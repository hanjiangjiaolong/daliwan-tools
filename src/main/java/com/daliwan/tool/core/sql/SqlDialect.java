package com.daliwan.tool.core.sql;

import com.github.vertical_blank.sqlformatter.languages.Dialect;

/**
 * 支持的 SQL 方言。
 *
 * <p>方言只影响格式化时的关键字识别与字符串转义规则，不改变拼接结果的结构：
 * 拼进 SQL 的值始终是带引号的字面量，因此换方言不会让输出「错位」。
 */
public enum SqlDialect {

    MYSQL("MySQL", Dialect.MySql),
    POSTGRESQL("PostgreSQL", Dialect.PostgreSql),
    ORACLE("Oracle", Dialect.PlSql),
    SQLSERVER("SQL Server", Dialect.TSql),
    STANDARD("标准 SQL", Dialect.StandardSql);

    private final String label;
    private final Dialect dialect;

    SqlDialect(String label, Dialect dialect) {
        this.label = label;
        this.dialect = dialect;
    }

    /** 界面上显示的名字。 */
    public String label() {
        return label;
    }

    /** 传给格式化库的方言枚举。 */
    public Dialect dialect() {
        return dialect;
    }

    /** 按显示名取方言，取不到时退回 MySQL（国内 MyBatis 项目绝大多数是它）。 */
    public static SqlDialect ofLabel(String label) {
        for (SqlDialect value : values()) {
            if (value.label.equalsIgnoreCase(label)) {
                return value;
            }
        }
        return MYSQL;
    }

    public static String[] labels() {
        SqlDialect[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].label;
        }
        return names;
    }
}
