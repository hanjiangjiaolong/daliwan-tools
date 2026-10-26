import com.daliwan.tool.core.IndentStyle;
import com.daliwan.tool.core.JsonFormatter;

/** 探一下反斜杠 u 转义在当前实现里的表现：单层转义 Jackson 会自动解，二次转义不会。 */
public class JsonEscapeProbe {

    public static void main(String[] args) throws Exception {
        String single = "{\"vid\":814,\"store_name\":\"\\u4f70\\u8d62\"}";
        String twice = "{\"vid\":814,\"store_name\":\"\\\\u4f70\\\\u8d62\"}";
        run("单层转义（JSON 里的 \\u4f70）", single);
        run("二次转义（JSON 里的 \\\\u4f70）", twice);
    }

    private static void run(String title, String raw) throws Exception {
        System.out.println("== " + title);
        System.out.println("  原文   " + raw);
        System.out.println("  格式化 " + JsonFormatter.format(raw, IndentStyle.TWO_SPACES)
                .replace("\n", "\\n"));
    }
}
