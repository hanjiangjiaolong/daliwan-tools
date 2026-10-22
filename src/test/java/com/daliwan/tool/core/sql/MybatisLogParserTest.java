package com.daliwan.tool.core.sql;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MybatisLogParserTest {

    private static final String SAMPLE = """
            [2020-01-10 15:00:00] [DEBUG] getUserList.debug(159) - ==> Preparing: \
            select user_id, user_name from t_user where delete_flag = ? and status = ? \
            order by created_date desc
            [2020-01-10 15:00:00] [DEBUG] getUserList.debug(159) - ==> Parameters: N(String), 01(String)""";

    @Test
    @DisplayName("带时间戳前缀的两行日志能还原出语句与参数")
    void parsesTimestampedLog() {
        List<MybatisLogParser.Statement> statements = MybatisLogParser.parse(SAMPLE);
        assertEquals(1, statements.size());

        MybatisLogParser.Statement statement = statements.get(0);
        assertTrue(statement.sql().startsWith("select user_id, user_name from t_user"));
        assertEquals(2, statement.params().size());
        assertEquals("getUserList.debug(159)", statement.source());
    }

    @Test
    @DisplayName("参数类型决定引号：字符串 01 不能被当成数字")
    void stringTypeKeepsQuotes() {
        List<ParamValue> params = MybatisLogParser.parseParams("N(String), 01(String)");
        assertEquals(2, params.size());
        assertEquals(ParamValue.Kind.TEXT, params.get(0).kind());
        assertEquals(ParamValue.Kind.TEXT, params.get(1).kind());
        assertEquals("'01'", params.get(1).render(SqlDialect.MYSQL));
    }

    @Test
    @DisplayName("数字、null、布尔、时间戳分别按类型解析")
    void parsesTypedValues() {
        List<ParamValue> params = MybatisLogParser.parseParams(
                "1(Long), null, true(Boolean), 2020-01-10 15:00:00.0(Timestamp)");
        assertEquals(4, params.size());
        assertEquals("1", params.get(0).render(SqlDialect.MYSQL));
        assertEquals("NULL", params.get(1).render(SqlDialect.MYSQL));
        assertEquals("TRUE", params.get(2).render(SqlDialect.MYSQL));
        // Timestamp 的毫秒尾巴要去掉，否则拼出来是 15:00:00.0
        assertEquals("'2020-01-10 15:00:00'", params.get(3).render(SqlDialect.MYSQL));
    }

    @Test
    @DisplayName("集合参数按元素展开，一个元素对应一个 ?")
    void expandsCollection() {
        List<ParamValue> params = MybatisLogParser.parseParams("[11, 22, 33](ArrayList)");
        assertEquals(3, params.size());
        assertEquals("11", params.get(0).render(SqlDialect.MYSQL));
        assertEquals("33", params.get(2).render(SqlDialect.MYSQL));
    }

    @Test
    @DisplayName("值本身带括号也能认出类型后缀")
    void handlesParenthesesInValue() {
        List<ParamValue> params = MybatisLogParser.parseParams("a(b)(String)");
        assertEquals(1, params.size());
        assertEquals("'a(b)'", params.get(0).render(SqlDialect.MYSQL));
    }

    @Test
    @DisplayName("空字符串参数渲染成一对空引号")
    void handlesEmptyString() {
        List<ParamValue> params = MybatisLogParser.parseParams("(String)");
        assertEquals(1, params.size());
        assertEquals("''", params.get(0).render(SqlDialect.MYSQL));
    }

    @Test
    @DisplayName("Spring Boot 默认日志格式也能取到来源")
    void parsesSpringBootFormat() {
        String log = "2020-01-10 15:00:00.123 DEBUG 1234 --- [nio-8080-exec-1] "
                + "c.e.UserMapper.getUserList : ==>  Preparing: select 1 from dual where a = ?\n"
                + "2020-01-10 15:00:00.124 DEBUG 1234 --- [nio-8080-exec-1] "
                + "c.e.UserMapper.getUserList : ==> Parameters: 7(Integer)";
        List<MybatisLogParser.Statement> statements = MybatisLogParser.parse(log);
        assertEquals(1, statements.size());
        assertEquals(1, statements.get(0).params().size());
        assertTrue(statements.get(0).source().contains("UserMapper.getUserList"));
    }

    @Test
    @DisplayName("一次粘贴多段日志解析成多条语句")
    void parsesMultipleStatements() {
        String log = """
                ==>  Preparing: insert into t(a) values(?)
                ==> Parameters: 1(Integer)
                ==>  Preparing: insert into t(a) values(?)
                ==> Parameters: 2(Integer)
                <==      Total: 1""";
        List<MybatisLogParser.Statement> statements = MybatisLogParser.parse(log);
        assertEquals(2, statements.size());
        assertEquals("1", statements.get(0).params().get(0).render(SqlDialect.MYSQL));
        assertEquals("2", statements.get(1).params().get(0).render(SqlDialect.MYSQL));
    }

    @Test
    @DisplayName("只有 Preparing 没有 Parameters 时仍算一条语句")
    void worksWithoutParametersLine() {
        List<MybatisLogParser.Statement> statements =
                MybatisLogParser.parse("==>  Preparing: select * from t where a = ?");
        assertEquals(1, statements.size());
        assertTrue(statements.get(0).params().isEmpty());
    }

    @Test
    @DisplayName("Row / Total 行不会被拼进 SQL")
    void ignoresResultLines() {
        String log = """
                ==>  Preparing: select a from t where b = ?
                ==> Parameters: 1(Integer)
                <==    Columns: a
                <==        Row: hello
                <==      Total: 1""";
        List<MybatisLogParser.Statement> statements = MybatisLogParser.parse(log);
        assertEquals(1, statements.size());
        assertFalse(statements.get(0).sql().contains("Row"));
    }

    @Test
    @DisplayName("空白输入返回空列表")
    void ignoresBlankInput() {
        assertTrue(MybatisLogParser.parse("  \n\n").isEmpty());
        assertTrue(MybatisLogParser.parse(null).isEmpty());
    }
}
