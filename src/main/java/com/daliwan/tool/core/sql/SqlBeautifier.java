package com.daliwan.tool.core.sql;

import com.github.vertical_blank.sqlformatter.SqlFormatter;
import com.github.vertical_blank.sqlformatter.core.FormatConfig;

/**
 * SQL 美化：统一交给 sql-formatter 处理，只在这里收口配置与失败兜底。
 */
public final class SqlBeautifier {

    private SqlBeautifier() {
    }

    /**
     * 格式化一条 SQL。
     *
     * @param sql       待美化的 SQL
     * @param dialect   目标方言
     * @param uppercase 关键字是否转大写
     * @return 美化后的 SQL；任何异常都退回原文，绝不吞掉用户的内容
     */
    public static String format(String sql, SqlDialect dialect, boolean uppercase) {
        if (sql == null || sql.isBlank()) {
            return sql;
        }
        try {
            FormatConfig config = FormatConfig.builder()
                    .indent("  ")
                    .uppercase(uppercase)
                    .linesBetweenQueries(1)
                    .build();
            String formatted = SqlFormatter.of(dialect.dialect()).format(sql, config);
            return formatted == null ? sql : formatted.strip();
        } catch (Throwable e) {
            // 格式化库对畸形 SQL 可能抛异常；此时保留原文，用户至少还能拿到拼接结果
            return sql;
        }
    }
}
