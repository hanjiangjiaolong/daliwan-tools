import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** 生成中文样例文档，用于对比不同引擎转 PDF 的保真度。 */
public class MakeSamples {

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args.length > 0 ? args[0] : "build/samples");
        Files.createDirectories(dir);

        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph title = doc.createParagraph();
            title.setStyle("Heading1");
            title.createRun().setText("大力丸转换保真度测试文档");
            XWPFParagraph p = doc.createParagraph();
            p.createRun().setText("这是一段中文正文，用来检验中文字体是否被正确嵌入与换行是否正常。"
                    + "后面跟着一个表格和一段加粗文字。");
            XWPFParagraph bold = doc.createParagraph();
            bold.createRun().setBold(true);
            bold.createRun().setText("这一行应该是加粗的中文。");
            XWPFTable table = doc.createTable(3, 3);
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    table.getRow(r).getCell(c).setText("单元格 " + (r + 1) + "-" + (c + 1));
                }
            }
            try (FileOutputStream out = new FileOutputStream(dir.resolve("sample.docx").toFile())) {
                doc.write(out);
            }
        }

        try (XMLSlideShow ppt = new XMLSlideShow()) {
            XSLFSlide slide = ppt.createSlide();
            XSLFTextShape title = slide.createTextBox();
            title.setText("中文演示文稿标题");
            title.setAnchor(new java.awt.Rectangle(80, 60, 560, 80));
            XSLFTextShape body = slide.createTextBox();
            body.setText("第二行正文：检验 PPT 转 PDF 的版式与中文渲染。");
            body.setAnchor(new java.awt.Rectangle(80, 180, 560, 60));
            try (FileOutputStream out = new FileOutputStream(dir.resolve("sample.pptx").toFile())) {
                ppt.write(out);
            }
        }

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("数据表");
            for (int r = 0; r < 12; r++) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(r);
                for (int c = 0; c < 5; c++) {
                    row.createCell(c).setCellValue(r == 0
                            ? "列头" + (c + 1)
                            : "数据" + (r) + "-" + (c + 1));
                }
            }
            try (FileOutputStream out = new FileOutputStream(dir.resolve("sample.xlsx").toFile())) {
                wb.write(out);
            }
        }
        System.out.println("样例已生成于 " + dir.toAbsolutePath());
    }
}
