package com.daliwan.tool.core.shell;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextMenuIntegrationTest {

    @Test
    @DisplayName("注册表键落在当前用户的 SystemFileAssociations 下")
    void buildsUserScopeKey() {
        assertEquals("HKEY_CURRENT_USER\\Software\\Classes\\SystemFileAssociations\\.docx\\shell\\DaliwanToPdf",
                ContextMenuIntegration.keyPath(".docx"));
    }

    @Test
    @DisplayName("扩展名不带点也能拼出正确的键")
    void acceptsExtensionWithoutDot() {
        assertEquals("HKEY_CURRENT_USER\\Software\\Classes\\SystemFileAssociations\\.xlsx\\shell\\DaliwanToPdf",
                ContextMenuIntegration.keyPath("xlsx"));
    }

    @Test
    @DisplayName("命令行把程序路径与 %1 都加上引号")
    void quotesFilePlaceholder() {
        String prefix = "\"C:\\Program Files\\daliwan_tool\\大力丸.exe\"";
        assertEquals(prefix + " --to-pdf \"%1\"", ContextMenuIntegration.commandValue(prefix));
    }

    @Test
    @DisplayName("reg 文件里的反斜杠与引号按格式转义")
    void escapesRegStrings() {
        assertEquals("C:\\\\Tools\\\\大力丸.exe,0", ContextMenuIntegration.escape("C:\\Tools\\大力丸.exe,0"));
        assertEquals("\\\"a\\\"", ContextMenuIntegration.escape("\"a\""));
    }

    @Test
    @DisplayName("覆盖 Word / Excel / PPT 三类后缀")
    void coversOfficeExtensions() {
        assertTrue(ContextMenuIntegration.covers(".doc"));
        assertTrue(ContextMenuIntegration.covers(".xlsx"));
        assertTrue(ContextMenuIntegration.covers(".pptx"));
        assertTrue(ContextMenuIntegration.covers(".CSV"), "大小写不敏感");
        assertFalse(ContextMenuIntegration.covers(".pdf"));
        assertFalse(ContextMenuIntegration.covers(".txt"));
    }

    @Test
    @DisplayName("扩展名都是带点的形式，避免注册到错误键上")
    void extensionsAreNormalized() {
        assertTrue(ContextMenuIntegration.EXTENSIONS.contains(".doc"));
        assertTrue(ContextMenuIntegration.EXTENSIONS.contains(".docx"));
        assertTrue(ContextMenuIntegration.EXTENSIONS.contains(".xls"));
        assertTrue(ContextMenuIntegration.EXTENSIONS.contains(".xlsx"));
        assertTrue(ContextMenuIntegration.EXTENSIONS.contains(".ppt"));
        assertTrue(ContextMenuIntegration.EXTENSIONS.contains(".pptx"));
        assertTrue(ContextMenuIntegration.EXTENSIONS.stream().allMatch(name -> name.startsWith(".")));
    }

    @Test
    @DisplayName("安装脚本覆盖所有扩展名，并通知外壳刷新")
    void installScriptCoversEverythingAndNotifies() {
        String command = "\"C:\\Tools\\大力丸.exe\" --to-pdf \"%1\"";
        String script = ContextMenuIntegration.installScript(
                List.of(ContextMenuIntegration.TO_PDF),
                "\"C:\\Tools\\大力丸.exe\"", "C:\\Tools\\大力丸.exe,0", true);

        assertTrue(script.contains("SystemFileAssociations\\$ext\\shell\\DaliwanToPdf"));
        assertTrue(script.contains("SHChangeNotify"));
        assertTrue(script.contains("'MultiSelectModel'"));
        assertTrue(script.contains(command), "命令行原样写入，单引号字符串里不做反斜杠转义");
        for (String extension : ContextMenuIntegration.EXTENSIONS) {
            assertTrue(script.contains("'" + extension + "'"), "缺少扩展名：" + extension);
        }
    }

    @Test
    @DisplayName("没有自带图标时脚本不写 Icon 值")
    void installScriptSkipsIconWhenUnavailable() {
        String script = ContextMenuIntegration.installScript(
                List.of(ContextMenuIntegration.TO_PDF),
                "\"java.exe\" -jar app.jar", "java.exe,0", false);
        assertFalse(script.contains("'Icon'"));
    }

    @Test
    @DisplayName("PDF 那一组四个菜单项各写各的开关，互不串台")
    void pdfVerbsUseTheirOwnFlags() {
        String script = ContextMenuIntegration.installScript(
                ContextMenuIntegration.PDF_TO_OFFICE,
                "\"C:\\Tools\\大力丸.exe\"", "C:\\Tools\\大力丸.exe,0", true);

        assertEquals(4, ContextMenuIntegration.PDF_TO_OFFICE.size());
        for (ContextMenuIntegration.MenuVerb verb : ContextMenuIntegration.PDF_TO_OFFICE) {
            assertTrue(script.contains(verb.key()), "缺少菜单项：" + verb.menuText());
            assertTrue(script.contains(verb.flag()), "缺少开关：" + verb.flag());
            assertTrue(script.contains(verb.menuText()), "缺少中文菜单名：" + verb.menuText());
            assertTrue(verb.extensions().equals(List.of(".pdf")), "PDF 组只挂在 .pdf 上");
        }
        // 四项共用同一个扩展名循环，但键名必须各不相同，否则互相覆盖
        long distinctKeys = ContextMenuIntegration.PDF_TO_OFFICE.stream()
                .map(ContextMenuIntegration.MenuVerb::key).distinct().count();
        assertEquals(4, distinctKeys);
    }

    @Test
    @DisplayName("卸载脚本只删本程序创建的子键")
    void uninstallScriptRemovesOwnKeys() {
        String script = ContextMenuIntegration.uninstallScript(ContextMenuIntegration.ALL);
        assertTrue(script.contains("Remove-Item -Path $base -Recurse -Force"));
        assertTrue(script.contains("\\shell\\DaliwanToPdf"));
        assertTrue(script.contains("\\shell\\DaliwanPdfToWord"));
        assertTrue(script.contains("\\shell\\DaliwanPdfToExcel"));
        assertTrue(script.contains("\\shell\\DaliwanPdfToSlides"));
        assertTrue(script.contains("\\shell\\DaliwanPdfToText"));
    }

    @Test
    @DisplayName("reg 兜底内容按 .reg 语法逐项列出")
    void regFallbackListsAllKeys() {
        String reg = ContextMenuIntegration.regInstall(
                List.of(ContextMenuIntegration.TO_PDF),
                "\"C:\\Tools\\大力丸.exe\"", "C:\\Tools\\大力丸.exe,0", true);
        assertTrue(reg.startsWith("Windows Registry Editor Version 5.00"));
        assertTrue(reg.contains("[HKEY_CURRENT_USER\\Software\\Classes\\SystemFileAssociations\\.docx\\shell\\DaliwanToPdf]"));
        assertTrue(reg.contains("\"MultiSelectModel\"=\"Document\""));
        assertTrue(reg.contains("\\command]"));
    }

    @Test
    @DisplayName("reg 卸载把每一项的键都删掉")
    void regUninstallListsEveryVerb() {
        String reg = ContextMenuIntegration.regUninstall(ContextMenuIntegration.ALL);
        assertTrue(reg.contains("[-HKEY_CURRENT_USER\\Software\\Classes\\SystemFileAssociations\\.pdf\\shell\\DaliwanPdfToWord]"));
        assertTrue(reg.contains("[-HKEY_CURRENT_USER\\Software\\Classes\\SystemFileAssociations\\.docx\\shell\\DaliwanToPdf]"));
    }

    @Test
    @DisplayName("菜单名与命令行开关一一对应，改文字不会改错命令")
    void verbTextMatchesFlag() {
        assertEquals("大力丸 · 转 Word", ContextMenuIntegration.PDF_TO_OFFICE.get(0).menuText());
        assertEquals("--to-docx", ContextMenuIntegration.PDF_TO_OFFICE.get(0).flag());
        assertEquals("大力丸 · 转 Excel", ContextMenuIntegration.PDF_TO_OFFICE.get(1).menuText());
        assertEquals("--to-xlsx", ContextMenuIntegration.PDF_TO_OFFICE.get(1).flag());
        assertEquals("大力丸 · 转 PPT", ContextMenuIntegration.PDF_TO_OFFICE.get(2).menuText());
        assertEquals("--to-pptx", ContextMenuIntegration.PDF_TO_OFFICE.get(2).flag());
        assertEquals("大力丸 · 提取文本", ContextMenuIntegration.PDF_TO_OFFICE.get(3).menuText());
        assertEquals("--to-txt", ContextMenuIntegration.PDF_TO_OFFICE.get(3).flag());
    }
}
