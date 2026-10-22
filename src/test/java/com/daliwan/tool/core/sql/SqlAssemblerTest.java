package com.daliwan.tool.core.sql;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlAssemblerTest {

    private static final String SAMPLE = """
            [2020-01-10 15:00:00] [DEBUG] getUserList.debug(159) - ==> Preparing: \
            select user_id, user_name from t_user where delete_flag = ? and status = ? \
            order by created_date desc
            [2020-01-10 15:00:00] [DEBUG] getUserList.debug(159) - ==> Parameters: N(String), 01(String)""";

    @Test
    @DisplayName("样例日志拼出带引号参数的 SQL")
    void assemblesSample() {
        SqlAssembler.Result result = SqlAssembler.assemble(SAMPLE, SqlDialect.MYSQL, true, true);
        assertTrue(result.text().contains("delete_flag = 'N'"), result.text());
        assertTrue(result.text().contains("status = '01'"), result.text());
        assertEquals(1, result.statements());
        assertEquals(2, result.parameters());
        assertTrue(result.warnings().isEmpty(), result.warnings().toString());
    }

    @Test
    @DisplayName("格式化后关键字大写并按方言换行")
    void formatsWithUppercase() {
        SqlAssembler.Result result = SqlAssembler.assemble(SAMPLE, SqlDialect.MYSQL, true, true);
        assertTrue(result.text().startsWith("SELECT"));
        assertTrue(result.text().contains("\nFROM\n"));
        assertTrue(result.text().contains("ORDER BY"));
    }

    @Test
    @DisplayName("关掉格式化时保留原样，只换参数")
    void keepsLayoutWhenNotFormatted() {
        SqlAssembler.Result result = SqlAssembler.assemble(SAMPLE, SqlDialect.MYSQL, false, false);
        assertTrue(result.text().startsWith("select user_id, user_name"));
        assertTrue(result.text().endsWith("created_date desc"));
    }

    @Test
    @DisplayName("字符串字面量里的问号不是占位符")
    void ignoresQuestionMarkInLiteral() {
        SqlAssembler.Fill fill = SqlAssembler.fill(
                "select * from t where note = 'why?' and id = ?",
                List.of(ParamValue.number("7")), SqlDialect.MYSQL);
        assertEquals("select * from t where note = 'why?' and id = 7", fill.sql());
        assertEquals(1, fill.filled());
        assertEquals(0, fill.missing());
    }

    @Test
    @DisplayName("占位符比参数多时原样保留 ? 并给出警告")
    void warnsOnMissingParams() {
        SqlAssembler.Result result = SqlAssembler.assemble(
                "==>  Preparing: select * from t where a = ? and b = ?\n==> Parameters: 1(Integer)",
                SqlDialect.MYSQL, false, false);
        assertTrue(result.text().contains("a = 1 and b = ?"), result.text());
        assertFalse(result.warnings().isEmpty());
    }

    @Test
    @DisplayName("参数比占位符多时提示多出的参数")
    void warnsOnUnusedParams() {
        SqlAssembler.Result result = SqlAssembler.assemble(
                "==>  Preparing: select * from t where a = ?\n==> Parameters: 1(Integer), 2(Integer)",
                SqlDialect.MYSQL, false, false);
        assertTrue(result.text().contains("a = 1"), result.text());
        assertFalse(result.warnings().isEmpty());
    }

    @Test
    @DisplayName("单引号按 SQL 规则转义成两个")
    void escapesQuote() {
        SqlAssembler.Result result = SqlAssembler.assemble(
                "==>  Preparing: select * from t where name = ?\n==> Parameters: O'Brien(String)",
                SqlDialect.MYSQL, false, false);
        assertTrue(result.text().contains("'O''Brien'"), result.text());
    }

    @Test
    @DisplayName("多段日志按分号分隔，便于直接执行")
    void separatesStatements() {
        String log = """
                ==>  Preparing: update t set a = ? where id = ?
                ==> Parameters: N(String), 1(Integer)
                ==>  Preparing: update t set a = ? where id = ?
                ==> Parameters: Y(String), 2(Integer)""";
        SqlAssembler.Result result = SqlAssembler.assemble(log, SqlDialect.MYSQL, false, false);
        assertEquals(2, result.statements());
        String[] parts = result.text().split("\n\n");
        assertEquals(2, parts.length);
        assertTrue(parts[0].trim().endsWith(";"));
    }

    @Test
    @DisplayName("布尔值按方言渲染")
    void rendersBooleanByDialect() {
        String log = "==>  Preparing: update t set a = ? where b = ?\n==> Parameters: true(Boolean), false(Boolean)";
        assertTrue(SqlAssembler.assemble(log, SqlDialect.MYSQL, false, false)
                .text().contains("a = TRUE"));
        assertTrue(SqlAssembler.assemble(log, SqlDialect.SQLSERVER, false, false)
                .text().contains("a = 1"));
    }

    @Test
    @DisplayName("不是 MyBatis 日志时给出明确提示")
    void reportsWhenNothingFound() {
        SqlAssembler.Result result = SqlAssembler.assemble(
                "select * from t where a = 1", SqlDialect.MYSQL, true, true);
        assertEquals("", result.text());
        assertFalse(result.warnings().isEmpty());
    }

    @Test
    @DisplayName("反引号包裹的标识符里的问号不参与替换")
    void ignoresQuestionMarkInBacktick() {
        SqlAssembler.Fill fill = SqlAssembler.fill(
                "select `a?` from t where b = ?", List.of(ParamValue.number("1")), SqlDialect.MYSQL);
        assertEquals("select `a?` from t where b = 1", fill.sql());
    }

    @Test
    @DisplayName("PostgreSQL 的 ARRAY[?] 里是真占位符，不能被当成方括号标识符")
    void fillsInsideArrayConstructor() {
        SqlAssembler.Fill fill = SqlAssembler.fill(
                "select * from t where id = any(array[?])",
                List.of(ParamValue.number("7")), SqlDialect.POSTGRESQL);
        assertEquals("select * from t where id = any(array[7])", fill.sql());
    }

    @Test
    @DisplayName("SQL Server 的方括号标识符原样保留")
    void keepsSqlServerBracketIdentifier() {
        SqlAssembler.Fill fill = SqlAssembler.fill(
                "select [user name] from [dbo].[t] where a = ?",
                List.of(ParamValue.number("1")), SqlDialect.SQLSERVER);
        assertEquals("select [user name] from [dbo].[t] where a = 1", fill.sql());
    }
}
