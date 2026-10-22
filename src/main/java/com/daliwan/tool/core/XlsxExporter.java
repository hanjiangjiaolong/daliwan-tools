package com.daliwan.tool.core;

import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.enums.poi.FillPatternTypeEnum;
import org.apache.fesod.sheet.support.ExcelTypeEnum;
import org.apache.fesod.sheet.write.handler.SheetWriteHandler;
import org.apache.fesod.sheet.write.handler.context.SheetWriteHandlerContext;
import org.apache.fesod.sheet.write.metadata.holder.WriteSheetHolder;
import org.apache.fesod.sheet.write.metadata.holder.WriteWorkbookHolder;
import org.apache.fesod.sheet.write.metadata.style.WriteCellStyle;
import org.apache.fesod.sheet.write.metadata.style.WriteFont;
import org.apache.fesod.sheet.write.style.HorizontalCellStyleStrategy;
import org.apache.poi.ss.usermodel.Sheet;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 把「列 + 行」写成 xlsx。
 *
 * <p>用它而不是 CSV：CSV 在中文 Excel 里坑很多——双击打开按系统编码猜，
 * 逗号、引号、换行要自己转义，长数字被转成科学计数法，前导 0 被吃掉。
 * xlsx 是二进制格式，这些都不存在。
 *
 * <p>单元格按内容推断类型：能放进 15 位有效数字的整数 / 小数写成数值，其余写文本。
 * 15 位是 Excel 的精度上限，再长的 ID（雪花号、订单号）一律留文本，
 * 否则 {@code 1234567890123456789} 会变成 {@code 1234567890123450000}。
 *
 * <p>表头用 11 号白底（fesod 默认是 14 号加粗 + 25% 灰底），列宽按内容自动撑开。
 */
public final class XlsxExporter {

    private static final int SHEET_NAME_LIMIT = 31;

    /** 表头字号：默认样式是 14 号加粗，太抢戏，压到跟正文一样的 11 号。 */
    private static final short HEAD_FONT_HEIGHT = 11;
    /**
     * 表头底色：9 = {@code IndexedColors.WHITE}，纯白。
     * 默认样式是 22（25% 灰），整片灰底看着脏，这里改成白底靠字体区分。
     */
    private static final short HEAD_FILL_COLOR = 9;

    /** 列宽下限（字符数）：短列别挤成一条线。 */
    private static final int MIN_COLUMN_CHARS = 8;
    /** 列宽上限：单元格里塞了整段 JSON 时不至于把表拉到几屏宽。 */
    private static final int MAX_COLUMN_CHARS = 60;
    /** 列宽留白，避免文字顶到格子边。 */
    private static final int COLUMN_PADDING = 2;
    /** Excel 单列最多 255 个字符，宽度单位是 1/256 字符。 */
    private static final int MAX_COLUMN_UNITS = 255 * 256;

    private XlsxExporter() {
    }

    /**
     * 写出一个工作簿。
     *
     * @param target  目标文件
     * @param sheetName 工作表名，非法字符会被替换
     * @param columns 表头
     * @param rows    数据行，长度不足的按空单元格补齐
     * @throws IOException 写文件失败（含 fesod 内部异常）
     */
    public static void write(Path target, String sheetName, List<String> columns,
                             List<List<String>> rows) throws IOException {
        List<List<String>> head = new ArrayList<>(columns.size());
        for (String column : columns) {
            head.add(List.of(column == null ? "" : column));
        }
        List<List<Object>> data = new ArrayList<>(rows.size());
        for (List<String> row : rows) {
            List<Object> cells = new ArrayList<>(columns.size());
            for (int i = 0; i < columns.size(); i++) {
                cells.add(cell(row, i));
            }
            data.add(cells);
        }
        try {
            FesodSheet.write(target.toFile())
                    .excelType(ExcelTypeEnum.XLSX)
                    .head(head)
                    .registerWriteHandler(headStyle())
                    .registerWriteHandler(new ColumnWidthHandler(columnWidths(columns, data)))
                    .sheet(safeSheetName(sheetName))
                    .doWrite(data);
        } catch (RuntimeException error) {
            throw new IOException("写出 Excel 失败：" + describe(error), error);
        }
    }

    /**
     * 表头样式：11 号字 + 纯白底。
     *
     * <p>fesod 默认表头是 14 号加粗 + 25% 灰底，导出几百列宽表时特别扎眼，所以显式压下来。
     * 内容区不指定样式，保持 Excel 默认（11 号、常规）。
     */
    private static HorizontalCellStyleStrategy headStyle() {
        WriteFont font = new WriteFont();
        font.setFontHeightInPoints(HEAD_FONT_HEIGHT);

        WriteCellStyle head = new WriteCellStyle();
        head.setWriteFont(font);
        head.setFillPatternType(FillPatternTypeEnum.SOLID_FOREGROUND.getPoiFillPatternType());
        head.setFillForegroundColor(HEAD_FILL_COLOR);

        return new HorizontalCellStyleStrategy(head, new WriteCellStyle());
    }

    /**
     * 按表头 + 全部数据算每列宽度：取最长的一个，再夹在上下限之间。
     *
     * <p>没用 fesod 自带的 {@code LongestMatchColumnWidthStyleStrategy}——它每个单元格都要
     * 回写一次列宽，上万行时纯属浪费；这里数据本来就在内存里，一次算完即可。
     */
    private static int[] columnWidths(List<String> columns, List<List<Object>> data) {
        int[] widest = new int[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            widest[i] = displayWidth(columns.get(i));
        }
        for (List<Object> row : data) {
            for (int i = 0; i < row.size() && i < widest.length; i++) {
                int width = displayWidth(String.valueOf(row.get(i)));
                if (width > widest[i]) {
                    widest[i] = width;
                }
            }
        }
        int[] units = new int[widest.length];
        for (int i = 0; i < widest.length; i++) {
            int chars = Math.max(widest[i] + COLUMN_PADDING, MIN_COLUMN_CHARS);
            units[i] = Math.min(Math.min(chars, MAX_COLUMN_CHARS) * 256, MAX_COLUMN_UNITS);
        }
        return units;
    }

    /**
     * 显示宽度：中文/全角按 2 个英文字符算，其余按 1 个。
     *
     * <p>算到上限就停，单元格里塞了几万字也不影响耗时。
     */
    static int displayWidth(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int width = 0;
        for (int i = 0; i < text.length() && width < MAX_COLUMN_CHARS; i++) {
            width += isWide(text.charAt(i)) ? 2 : 1;
        }
        return width;
    }

    private static boolean isWide(char c) {
        return (c >= '\u1100' && c <= '\u115F')
                || (c >= '\u2E80' && c <= '\uA4CF')
                || (c >= '\uAC00' && c <= '\uD7A3')
                || (c >= '\uF900' && c <= '\uFAFF')
                || (c >= '\uFE30' && c <= '\uFE6F')
                || (c >= '\uFF00' && c <= '\uFF60')
                || (c >= '\uFFE0' && c <= '\uFFE6');
    }

    /** 把算好的列宽一次性写进工作表，表建好就设，跟单元格数量无关。 */
    private static final class ColumnWidthHandler implements SheetWriteHandler {

        private final int[] widths;

        ColumnWidthHandler(int[] widths) {
            this.widths = widths;
        }

        @Override
        public void afterSheetCreate(SheetWriteHandlerContext context) {
            apply(context == null ? null : context.getWriteSheetHolder());
        }

        @Override
        public void afterSheetCreate(WriteWorkbookHolder workbookHolder, WriteSheetHolder sheetHolder) {
            apply(sheetHolder);
        }

        private void apply(WriteSheetHolder holder) {
            if (holder == null) {
                return;
            }
            Sheet sheet = holder.getSheet();
            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i]);
            }
        }
    }

    /** 取一个单元格：缺列当空串，能当数字的转数字，其余留文本。 */
    private static Object cell(List<String> row, int index) {
        String text = index < row.size() ? row.get(index) : "";
        if (text == null) {
            return "";
        }
        Object number = asNumber(text);
        return number != null ? number : text;
    }

    /**
     * 把形如 {@code 12}、{@code -3.5} 的文本转成数字；不像数字或超出 Excel 精度就返回 {@code null}。
     *
     * <p>排除前导 0（{@code 007} 是编号不是数值）、{@code 1.} 与 {@code .5} 这类残缺写法，
     * 以及超过 15 位的长数字——Excel 存不下那么多有效位，转过去只会看见一串 0。
     */
    static Object asNumber(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        String body = text;
        char first = body.charAt(0);
        if (first == '-' || first == '+') {
            body = body.substring(1);
        }
        if (body.isEmpty()) {
            return null;
        }
        int dot = body.indexOf('.');
        if (dot >= 0 && body.indexOf('.', dot + 1) >= 0) {
            return null;
        }
        String intPart = dot < 0 ? body : body.substring(0, dot);
        String fracPart = dot < 0 ? "" : body.substring(dot + 1);
        if (intPart.isEmpty() || (dot >= 0 && fracPart.isEmpty())) {
            return null;
        }
        for (int i = 0; i < intPart.length(); i++) {
            if (!isDigit(intPart.charAt(i))) {
                return null;
            }
        }
        for (int i = 0; i < fracPart.length(); i++) {
            if (!isDigit(fracPart.charAt(i))) {
                return null;
            }
        }
        // 007、00.5 这类带前导 0 的当成编号
        if (intPart.length() > 1 && intPart.charAt(0) == '0') {
            return null;
        }
        if (intPart.length() + fracPart.length() > 15) {
            return null;
        }
        try {
            if (dot < 0) {
                return Long.parseLong(text);
            }
            double value = Double.parseDouble(text);
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    /** 工作表名：去掉 Excel 不允许的字符，并截到 31 个字符以内。 */
    static String safeSheetName(String name) {
        String cleaned = name == null ? "" : name.replaceAll("[\\\\/*?:\\[\\]]", " ").trim();
        if (cleaned.isEmpty()) {
            return "数据";
        }
        return cleaned.length() > SHEET_NAME_LIMIT ? cleaned.substring(0, SHEET_NAME_LIMIT) : cleaned;
    }

    private static String describe(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }
}
