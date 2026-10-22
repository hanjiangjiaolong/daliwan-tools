import com.daliwan.tool.core.XlsxExporter;
import java.nio.file.*;
import java.util.*;

public class XlsxTime {
    public static void main(String[] a) throws Exception {
        List<String> cols = List.of("id", "name", "score");
        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {
            rows.add(List.of(String.valueOf(1000 + i), "张三" + i, String.valueOf(60 + i % 40)));
        }
        Path out = Path.of("build/verify/xlsx-time.xlsx");
        long start = System.nanoTime();
        XlsxExporter.write(out, "数据", cols, rows);
        System.out.println("[first] " + (System.nanoTime() - start) / 1_000_000 + " ms, "
                + Files.size(out) + " bytes");
        start = System.nanoTime();
        XlsxExporter.write(out, "数据", cols, rows);
        System.out.println("[second] " + (System.nanoTime() - start) / 1_000_000 + " ms");
    }
}
