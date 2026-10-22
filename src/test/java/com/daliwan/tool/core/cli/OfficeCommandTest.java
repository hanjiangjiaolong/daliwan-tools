package com.daliwan.tool.core.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfficeCommandTest {

    @Test
    @DisplayName("认得 PDF 转 Office 的开关，不认得转 PDF 的开关")
    void matchesItsOwnFlags() {
        assertTrue(OfficeCommand.matches(new String[]{"--to-docx", "D:\\a.pdf"}));
        assertTrue(OfficeCommand.matches(new String[]{"D:\\a.pdf", "--to-xlsx"}));
        assertTrue(OfficeCommand.matches(new String[]{"--to-pptx", "--to-txt", "D:\\a.pdf"}));
        assertFalse(OfficeCommand.matches(new String[]{"--to-pdf", "D:\\a.docx"}));
        assertFalse(OfficeCommand.matches(new String[]{"D:\\a.pdf"}));
        assertFalse(OfficeCommand.matches(null));
    }

    @Test
    @DisplayName("多个开关同时出现时取第一个，和右键菜单只点一项的行为一致")
    void takesFirstFlag() {
        assertEquals("--to-pptx", OfficeCommand.flagOf(new String[]{"--to-pptx", "--to-txt", "a.pdf"}));
        assertNull(OfficeCommand.flagOf(new String[]{"a.pdf", "b.pdf"}));
    }

    @Test
    @DisplayName("命令行里的文件参数排除开关本身")
    void collectsOnlyFiles() {
        List<Path> files = PdfCommand.collect(new String[]{"--to-docx", "D:\\a.pdf", "D:\\b.pdf"});
        assertEquals(List.of(Path.of("D:\\a.pdf"), Path.of("D:\\b.pdf")), files);
    }

    @Test
    @DisplayName("开关清单与转换目标一一对应")
    void flagsMatchTargets() {
        assertEquals(4, OfficeCommand.flags().size());
        assertTrue(OfficeCommand.flags().contains("--to-docx"));
        assertTrue(OfficeCommand.flags().contains("--to-xlsx"));
        assertTrue(OfficeCommand.flags().contains("--to-pptx"));
        assertTrue(OfficeCommand.flags().contains("--to-txt"));
    }
}
