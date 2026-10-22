package com.daliwan.tool.core.sql;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlHighlighterTest {

    /** 取指定文字对应的类别，找不到返回 {@code PLAIN}。 */
    private static SqlHighlighter.Kind kindOf(String sql, String text) {
        int at = sql.indexOf(text);
        for (SqlHighlighter.Token token : SqlHighlighter.scan(sql)) {
            if (token.start() == at) {
                return token.kind();
            }
        }
        return SqlHighlighter.Kind.PLAIN;
    }

    @Test
    @DisplayName("关键字、函数、字符串、数字各自归类")
    void classifiesBasics() {
        String sql = "SELECT COUNT(*) FROM t_user WHERE status = '01' AND age > 18";
        assertEquals(SqlHighlighter.Kind.KEYWORD, kindOf(sql, "SELECT"));
        assertEquals(SqlHighlighter.Kind.FUNCTION, kindOf(sql, "COUNT"));
        assertEquals(SqlHighlighter.Kind.KEYWORD, kindOf(sql, "FROM"));
        assertEquals(SqlHighlighter.Kind.STRING, kindOf(sql, "'01'"));
        assertEquals(SqlHighlighter.Kind.KEYWORD, kindOf(sql, "AND"));
        assertEquals(SqlHighlighter.Kind.NUMBER, kindOf(sql, "18"));
    }

    @Test
    @DisplayName("NULL / TRUE / FALSE 归为字面量而不是关键字")
    void literalsAreDistinct() {
        String sql = "SELECT * FROM t WHERE a IS NULL AND b = TRUE AND c = FALSE";
        assertEquals(SqlHighlighter.Kind.LITERAL, kindOf(sql, "NULL"));
        assertEquals(SqlHighlighter.Kind.LITERAL, kindOf(sql, "TRUE"));
        assertEquals(SqlHighlighter.Kind.LITERAL, kindOf(sql, "FALSE"));
        assertEquals(SqlHighlighter.Kind.KEYWORD, kindOf(sql, "IS"));
    }

    @Test
    @DisplayName("关键字大小写不敏感")
    void keywordsIgnoreCase() {
        String sql = "select * from t where a = 1";
        assertEquals(SqlHighlighter.Kind.KEYWORD, kindOf(sql, "select"));
        assertEquals(SqlHighlighter.Kind.KEYWORD, kindOf(sql, "from"));
    }

    @Test
    @DisplayName("字符串里的关键字与运算符不被单独着色")
    void stringStaysWhole() {
        String sql = "SELECT * FROM t WHERE note = 'a = b AND c -- d'";
        List<SqlHighlighter.Token> tokens = SqlHighlighter.scan(sql);
        // 字符串整段是一个 token，里面的 = AND -- 都不该再出现在结果里
        assertTrue(tokens.stream().anyMatch(t -> t.kind() == SqlHighlighter.Kind.STRING
                && sql.substring(t.start(), t.end()).equals("'a = b AND c -- d'")));
        assertFalse(tokens.stream().anyMatch(t -> t.kind() == SqlHighlighter.Kind.COMMENT));
    }

    @Test
    @DisplayName("转义单引号不会提前结束字符串")
    void handlesEscapedQuote() {
        String sql = "SELECT * FROM t WHERE name = 'O''Brien' AND x = 1";
        List<SqlHighlighter.Token> tokens = SqlHighlighter.scan(sql);
        assertTrue(tokens.stream().anyMatch(t -> t.kind() == SqlHighlighter.Kind.STRING
                && sql.substring(t.start(), t.end()).equals("'O''Brien'")));
    }

    @Test
    @DisplayName("行注释与块注释整段着色")
    void commentsAreWhole() {
        String sql = "SELECT 1 -- 说明\n/* 多行\n注释 */ FROM t";
        List<SqlHighlighter.Token> tokens = SqlHighlighter.scan(sql);
        assertEquals(2, tokens.stream().filter(t -> t.kind() == SqlHighlighter.Kind.COMMENT).count());
        assertTrue(tokens.stream().anyMatch(t -> t.kind() == SqlHighlighter.Kind.COMMENT
                && sql.substring(t.start(), t.end()).equals("-- 说明")));
    }

    @Test
    @DisplayName("反引号与双引号标识符单独一类")
    void quotedIdentifiers() {
        String sql = "SELECT `user_name`, \"remark\" FROM `t_user`";
        assertEquals(SqlHighlighter.Kind.QUOTED, kindOf(sql, "`user_name`"));
        assertEquals(SqlHighlighter.Kind.QUOTED, kindOf(sql, "\"remark\""));
    }

    @Test
    @DisplayName("小数、指数与负数（负号算运算符）")
    void numbers() {
        assertEquals(SqlHighlighter.Kind.NUMBER, kindOf("SELECT 3.14, 1e5, 2E-3", "3.14"));
        assertEquals(SqlHighlighter.Kind.NUMBER, kindOf("SELECT 3.14, 1e5, 2E-3", "1e5"));
        assertEquals(SqlHighlighter.Kind.NUMBER, kindOf("SELECT 3.14, 1e5, 2E-3", "2E-3"));
        assertEquals(SqlHighlighter.Kind.OPERATOR, kindOf("SELECT -1", "-"));
    }

    @Test
    @DisplayName("token 按位置升序且互不重叠")
    void tokensAreOrderedAndDisjoint() {
        String sql = "SELECT a, 'x' FROM t WHERE b = 1 /* c */ ORDER BY a DESC";
        List<SqlHighlighter.Token> tokens = SqlHighlighter.scan(sql);
        assertFalse(tokens.isEmpty());
        int previousEnd = 0;
        for (SqlHighlighter.Token token : tokens) {
            assertTrue(token.start() >= previousEnd, "token 重叠：" + token);
            assertTrue(token.end() > token.start(), "空 token：" + token);
            previousEnd = token.end();
        }
    }

    @Test
    @DisplayName("空输入与 null 返回空列表")
    void emptyInput() {
        assertTrue(SqlHighlighter.scan(null).isEmpty());
        assertTrue(SqlHighlighter.scan("").isEmpty());
        assertTrue(SqlHighlighter.scan("   \n\t ").isEmpty());
    }

    @Test
    @DisplayName("中文别名与字段名不受影响")
    void chineseIdentifiers() {
        String sql = "SELECT 用户名 FROM t_user";
        assertEquals(SqlHighlighter.Kind.PLAIN, kindOf(sql, "用户名"));
        assertEquals(SqlHighlighter.Kind.KEYWORD, kindOf(sql, "FROM"));
    }

    @Test
    @DisplayName("真实拼接结果能完整着色")
    void highlightsAssembledSql() {
        String log = """
                [2020-01-10 15:00:00] [DEBUG] getUserList.debug(159) - ==> Preparing: \
                select user_id, user_name from t_user where delete_flag = ? and status = ? \
                order by created_date desc
                [2020-01-10 15:00:00] [DEBUG] getUserList.debug(159) - ==> Parameters: N(String), 01(String)""";
        String sql = SqlAssembler.assemble(log, SqlDialect.MYSQL, true, true).text();

        List<SqlHighlighter.Token> tokens = SqlHighlighter.scan(sql);
        assertTrue(tokens.stream().anyMatch(t -> t.kind() == SqlHighlighter.Kind.KEYWORD));
        assertTrue(tokens.stream().anyMatch(t -> t.kind() == SqlHighlighter.Kind.STRING
                && sql.substring(t.start(), t.end()).equals("'01'")));
        assertTrue(tokens.stream().anyMatch(t -> t.kind() == SqlHighlighter.Kind.STRING
                && sql.substring(t.start(), t.end()).equals("'N'")));
    }
}
