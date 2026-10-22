package com.daliwan.tool.core.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfCommandTest {

    @Test
    @DisplayName("识别 --to-pdf 开关")
    void detectsFlag() {
        assertTrue(PdfCommand.matches(new String[]{"--to-pdf", "D:\\a.pptx"}));
        assertTrue(PdfCommand.matches(new String[]{"D:\\a.pptx", "--to-pdf"}));
        assertFalse(PdfCommand.matches(new String[]{"--help"}));
        assertFalse(PdfCommand.matches(new String[]{}));
        assertFalse(PdfCommand.matches(null));
    }

    @Test
    @DisplayName("从命令行里取出文件，忽略开关与其它参数")
    void collectsFileArguments() {
        List<Path> files = PdfCommand.collect(new String[]{
                "--to-pdf", "D:\\a.docx", "", "--verbose", "D:\\b.xlsx"});
        assertEquals(List.of(Path.of("D:\\a.docx"), Path.of("D:\\b.xlsx")), files);
    }

    @Test
    @DisplayName("同一个文件重复出现只算一次")
    void deduplicates() {
        List<Path> files = PdfCommand.collect(new String[]{
                "--to-pdf", "C:\\a.ppt", "C:\\a.ppt"});
        assertEquals(1, files.size());
    }

    @Test
    @DisplayName("没有文件参数时返回空列表，不会把开关当路径")
    void ignoresBlankInput() {
        assertTrue(PdfCommand.collect(new String[]{"--to-pdf"}).isEmpty());
        assertTrue(PdfCommand.collect(null).isEmpty());
    }
}
