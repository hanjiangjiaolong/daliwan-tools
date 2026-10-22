package com.daliwan.tool.core;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XlsxExporterTest {

    @Test
    void writesHeaderAndRows(@TempDir Path dir) throws Exception {
        Path target = dir.resolve("table.xlsx");
        XlsxExporter.write(target, "数据",
                List.of("id", "name", "score"),
                List.of(List.of("1001", "张三", "98.5"),
                        List.of("1002", "李四", "76")));

        assertTrue(Files.size(target) > 0, "文件应该写出来了");
        try (InputStream in = Files.newInputStream(target); Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals("数据", sheet.getSheetName());

            Row head = sheet.getRow(0);
            assertEquals("id", head.getCell(0).getStringCellValue());
            assertEquals("name", head.getCell(1).getStringCellValue());
            assertEquals("score", head.getCell(2).getStringCellValue());

            Row first = sheet.getRow(1);
            assertEquals(CellType.NUMERIC, first.getCell(0).getCellType(), "整数应写成数值");
            assertEquals(1001.0, first.getCell(0).getNumericCellValue(), 0.0001);
            assertEquals("张三", first.getCell(1).getStringCellValue());
            assertEquals(CellType.NUMERIC, first.getCell(2).getCellType());
            assertEquals(98.5, first.getCell(2).getNumericCellValue(), 0.0001);

            assertEquals(3, sheet.getLastRowNum() + 1, "表头 + 两行数据");
        }
    }

    @Test
    void keepsIdsAsText(@TempDir Path dir) throws Exception {
        Path target = dir.resolve("ids.xlsx");
        XlsxExporter.write(target, "数据", List.of("code", "long", "text"),
                List.of(List.of("007", "1234567890123456789", "A-1")));

        try (InputStream in = Files.newInputStream(target); Workbook workbook = new XSSFWorkbook(in)) {
            Row row = workbook.getSheetAt(0).getRow(1);
            assertEquals("007", row.getCell(0).getStringCellValue(), "前导 0 的编号不能变数值");
            assertEquals("1234567890123456789", row.getCell(1).getStringCellValue(),
                    "超过 15 位的长数字留文本，否则精度丢失");
            assertEquals("A-1", row.getCell(2).getStringCellValue());
        }
    }

    @Test
    void padsShortRowsAndHandlesBlankCells(@TempDir Path dir) throws Exception {
        Path target = dir.resolve("short.xlsx");
        XlsxExporter.write(target, "数据", List.of("a", "b", "c"),
                List.of(List.of("1"), List.of()));

        try (InputStream in = Files.newInputStream(target); Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row first = sheet.getRow(1);
            assertNotNull(first);
            assertEquals(1.0, first.getCell(0).getNumericCellValue(), 0.0001);
            assertEquals("", first.getCell(2).getStringCellValue(), "缺的列补空串");
            assertNotNull(sheet.getRow(2), "空行也要写出来");
        }
    }

    @Test
    void sanitizesSheetName(@TempDir Path dir) throws Exception {
        Path target = dir.resolve("name.xlsx");
        String longName = "a".repeat(40) + "/bad:name";
        XlsxExporter.write(target, longName, List.of("x"), List.of(List.of("1")));

        try (InputStream in = Files.newInputStream(target); Workbook workbook = new XSSFWorkbook(in)) {
            String name = workbook.getSheetAt(0).getSheetName();
            assertTrue(name.length() <= 31, "工作表名不能超过 31 个字符：" + name);
            assertTrue(!name.contains("/") && !name.contains(":"), "非法字符应被替换：" + name);
        }
    }

    @Test
    void headUsesElevenPointFontOnWhiteFill(@TempDir Path dir) throws Exception {
        Path target = dir.resolve("style.xlsx");
        XlsxExporter.write(target, "数据", List.of("id", "name"), List.of(List.of("1", "张三")));

        try (InputStream in = Files.newInputStream(target); Workbook workbook = new XSSFWorkbook(in)) {
            Row head = workbook.getSheetAt(0).getRow(0);
            CellStyle style = head.getCell(0).getCellStyle();
            Font font = workbook.getFontAt(style.getFontIndexAsInt());
            assertEquals(11, font.getFontHeightInPoints(), "表头字号压到 11，跟正文一致");
            assertEquals(FillPatternType.SOLID_FOREGROUND, style.getFillPattern());
            assertEquals(9, style.getFillForegroundColor(), "9 = 纯白，替掉默认的 25% 灰底");

            Font bodyFont = workbook.getFontAt(workbook.getSheetAt(0).getRow(1).getCell(0)
                    .getCellStyle().getFontIndexAsInt());
            assertEquals(11, bodyFont.getFontHeightInPoints(), "正文保持 Excel 默认 11 号");
        }
    }

    @Test
    void columnWidthFollowsContent(@TempDir Path dir) throws Exception {
        Path target = dir.resolve("width.xlsx");
        XlsxExporter.write(target, "数据", List.of("id", "name", "备注"),
                List.of(List.of("1", "张三", "短"),
                        List.of("2", "欧阳娜娜娜娜", "这是一段比较长的中文备注")));

        try (InputStream in = Files.newInputStream(target); Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            int id = sheet.getColumnWidth(0);
            int name = sheet.getColumnWidth(1);
            int note = sheet.getColumnWidth(2);
            assertEquals(2048, id, "短列取下限 8 个字符");
            assertTrue(name > id, "姓名列比 id 宽：" + name);
            assertTrue(note > name, "备注列最长，应该最宽：" + note);
            assertTrue(sheet.getColumnWidth(2) <= 255 * 256, "列宽不能超过 Excel 上限");
        }
    }

    @Test
    void capsColumnWidthForHugeCells(@TempDir Path dir) throws Exception {
        Path target = dir.resolve("cap.xlsx");
        String huge = "长".repeat(5000);
        XlsxExporter.write(target, "数据", List.of("blob"), List.of(List.of(huge)));

        try (InputStream in = Files.newInputStream(target); Workbook workbook = new XSSFWorkbook(in)) {
            int width = workbook.getSheetAt(0).getColumnWidth(0);
            assertTrue(width <= 60 * 256, "超长内容也要夹在 60 个字符以内，实际 " + width / 256.0);
        }
    }

    @Test
    void displayWidthCountsChineseAsDouble() {
        assertEquals(0, XlsxExporter.displayWidth(null));
        assertEquals(0, XlsxExporter.displayWidth(""));
        assertEquals(4, XlsxExporter.displayWidth("abcd"));
        assertEquals(4, XlsxExporter.displayWidth("张三"));
        assertEquals(6, XlsxExporter.displayWidth("a张三b"));
    }

    @Test
    void numberInference() {
        assertEquals(1001L, XlsxExporter.asNumber("1001"));
        assertEquals(-3L, XlsxExporter.asNumber("-3"));
        assertEquals(98.5, XlsxExporter.asNumber("98.5"));
        assertEquals(0L, XlsxExporter.asNumber("0"));
        assertEquals(0.5, XlsxExporter.asNumber("0.5"));

        assertNull(XlsxExporter.asNumber("007"), "前导 0 是编号");
        assertNull(XlsxExporter.asNumber("1."), "残缺写法");
        assertNull(XlsxExporter.asNumber(".5"), "残缺写法");
        assertNull(XlsxExporter.asNumber("1e3"), "科学计数法留文本");
        assertNull(XlsxExporter.asNumber("1.2.3"));
        assertNull(XlsxExporter.asNumber("1234567890123456"), "16 位超精度");
        assertNull(XlsxExporter.asNumber(""), "空串");
        assertNull(XlsxExporter.asNumber("-"));
        assertNull(XlsxExporter.asNumber("true"));
        assertNull(XlsxExporter.asNumber("张三"));
    }
}
