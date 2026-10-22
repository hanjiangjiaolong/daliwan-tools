import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.IndexedColors;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.daliwan.tool.core.XlsxExporter;

/** 导出一份样例 xlsx，把表头/内容样式和列宽读回来看看。 */
public class XlsxStyleProbe {

    public static void main(String[] args) throws Exception {
        for (IndexedColors color : IndexedColors.values()) {
            if (color.getIndex() == 9 || color.getIndex() == 22 || color.getIndex() == 1) {
                System.out.println("IndexedColors " + color.name() + " = " + color.getIndex());
            }
        }

        Path target = Path.of("target", "probe.xlsx");
        List<String> columns = List.of("id", "name", "这是一列很长的表头说明文字", "score");
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("1001", "张三", "短", "98.5"));
        rows.add(List.of("1002", "欧阳娜娜娜娜娜娜娜娜", "中文内容比较长的一串文字用来看列宽", "76"));
        rows.add(List.of("1003", "007", "x", "1"));
        XlsxExporter.write(target, "数据", columns, rows);

        try (InputStream in = Files.newInputStream(target); Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row head = sheet.getRow(0);
            Row body = sheet.getRow(1);
            for (int i = 0; i < columns.size(); i++) {
                System.out.println("列 " + i + " 宽 = " + sheet.getColumnWidthInPixels(i) + "px / units "
                        + sheet.getColumnWidth(i) + " (字符 " + (sheet.getColumnWidth(i) / 256.0) + ")");
            }
            dump("表头 A1", head.getCell(1), workbook);
            dump("内容 A2", body.getCell(1), workbook);
            System.out.println("默认列宽(无样式列) = " + (sheet.getColumnWidth(10) / 256.0));
        }
    }

    private static void dump(String tag, Cell cell, Workbook workbook) {
        CellStyle style = cell.getCellStyle();
        org.apache.poi.ss.usermodel.Font font = workbook.getFontAt(style.getFontIndexAsInt());
        System.out.println(tag + ": 字号=" + font.getFontHeightInPoints()
                + " 粗体=" + font.getBold()
                + " 字体=" + font.getFontName()
                + " 填充模式=" + style.getFillPattern()
                + " 前景色=" + style.getFillForegroundColor()
                + " 对齐=" + style.getAlignment());
    }
}
