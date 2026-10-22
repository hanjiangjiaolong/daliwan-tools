import com.daliwan.tool.core.sql.*;

public class SqlSmoke {
    public static void main(String[] a) {
        String log = "[2020-01-10 15:00:00] [DEBUG] getUserList.debug(159) - ==> Preparing: select user_id, user_name from t_user where delete_flag = ? and status = ? order by created_date desc\n"
                + "[2020-01-10 15:00:00] [DEBUG] getUserList.debug(159) - ==> Parameters: N(String), 01(String)";
        SqlAssembler.Result r = SqlAssembler.assemble(log, SqlDialect.MYSQL, true, true);
        System.out.println(r.text());
        System.out.println("--- 统计 ---");
        System.out.println("statements=" + r.statements() + " params=" + r.parameters()
                + " sources=" + r.sources() + " warnings=" + r.warnings());
        System.out.println("--- 不格式化 ---");
        SqlAssembler.Result raw = SqlAssembler.assemble(log, SqlDialect.MYSQL, false, false);
        System.out.println(raw.text());
        System.out.println("--- 多种参数 ---");
        String log2 = "==>  Preparing: insert into t values(?, ?, ?, ?, ?)\n"
                + "==> Parameters: 1(Long), O'Brien(String), null, true(Boolean), 2020-01-10 15:00:00.0(Timestamp)";
        System.out.println(SqlAssembler.assemble(log2, SqlDialect.MYSQL, false, false).text());
        System.out.println("--- 集合 + 字符串问号 ---");
        String log3 = "==>  Preparing: select * from t where id in (?, ?) and note = 'why?'\n"
                + "==> Parameters: [11, 22](ArrayList)";
        System.out.println(SqlAssembler.assemble(log3, SqlDialect.MYSQL, false, false).text());
    }
}
