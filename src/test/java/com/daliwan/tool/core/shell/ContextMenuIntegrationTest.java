package com.daliwan.tool.core.shell;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextMenuIntegrationTest {

    @Test
    @DisplayName("一级菜单落在当前用户的 SystemFileAssociations 下，键名统一为 Daliwan")
    void buildsUserScopeKey() {
        assertEquals("HKEY_CURRENT_USER\\Software\\Classes\\SystemFileAssociations\\.docx\\shell\\Daliwan",
                ContextMenuIntegration.keyPath(".docx"));
    }

    @Test
    @DisplayName("扩展名不带点也能拼出正确的键")
    void acceptsExtensionWithoutDot() {
        assertEquals("HKEY_CURRENT_USER\\Software\\Classes\\SystemFileAssociations\\.xlsx\\shell\\Daliwan",
                ContextMenuIntegration.keyPath("xlsx"));
    }

    @Test
    @DisplayName("二级菜单项挂在父键的 Shell 子键下")
    void childKeySitsUnderParent() {
        assertEquals("HKEY_CURRENT_USER\\Software\\Classes\\SystemFileAssociations\\.pdf\\shell\\Daliwan"
                        + "\\Shell\\DaliwanPdfToWord",
                ContextMenuIntegration.keyPath(ContextMenuIntegration.PDF_TO_OFFICE.get(0), ".pdf"));
    }

    @Test
    @DisplayName("集中键按扩展名分开，避免 .pdf 上出现「转 PDF」这种没意义的项")
    void storeKeyIsPerExtension() {
        assertEquals("HKEY_CURRENT_USER\\Software\\Classes\\DaliwanMenu\\.pdf\\shell\\DaliwanPdfToText",
                ContextMenuIntegration.storeKeyPath(ContextMenuIntegration.PDF_TO_OFFICE.get(3), ".pdf"));
        assertEquals("HKEY_CURRENT_USER\\Software\\Classes\\DaliwanMenu\\.docx\\shell\\DaliwanToPdf",
                ContextMenuIntegration.storeKeyPath(ContextMenuIntegration.TO_PDF, ".docx"));
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
    @DisplayName("安装脚本把动作收进一级菜单，并覆盖所有扩展名")
    void installScriptBuildsCascade() {
        String command = "\"C:\\Tools\\大力丸.exe\" --to-pdf \"%1\"";
        String script = ContextMenuIntegration.installScript(
                List.of(ContextMenuIntegration.TO_PDF),
                "\"C:\\Tools\\大力丸.exe\"", "C:\\Tools\\大力丸.exe,0", true);

        assertTrue(script.contains("SystemFileAssociations\\$ext\\shell\\Daliwan"));
        assertTrue(script.contains("'MUIVerb'"), "一级菜单名写在 MUIVerb 上");
        assertTrue(script.contains(psTitle()), "一级菜单名是「大力丸」");
        assertTrue(script.contains("'SubCommands' -Value ''"), "SubCommands 置空才是子菜单");
        assertTrue(script.contains("'ExtendedSubCommandsKey'"));
        assertTrue(script.contains("Software\\Classes\\DaliwanMenu\\$ext"));
        assertTrue(script.contains("SHChangeNotify"));
        assertTrue(script.contains("'MultiSelectModel'"));
        assertTrue(script.contains(command), "命令行原样写入，单引号字符串里不做反斜杠转义");
        for (String extension : ContextMenuIntegration.EXTENSIONS) {
            assertTrue(script.contains("'" + extension + "'"), "缺少扩展名：" + extension);
        }
    }

    @Test
    @DisplayName("旧版平铺的键在安装时先清掉，避免右键里出现两套菜单")
    void installScriptCleansLegacyFlatKeys() {
        String script = ContextMenuIntegration.installScript(
                List.of(ContextMenuIntegration.TO_PDF),
                "\"C:\\Tools\\大力丸.exe\"", "C:\\Tools\\大力丸.exe,0", true);

        assertTrue(script.contains("$old = \"HKCU:\\Software\\Classes\\SystemFileAssociations\\$ext"
                + "\\shell\\DaliwanToPdf\""));
        assertTrue(script.contains("$old = \"HKCU:\\Software\\Classes\\SystemFileAssociations\\$ext"
                + "\\shell\\DaliwanPdfToWord\""));
        // 清理必须发生在新键写入之前，否则会把刚写的子键删掉
        assertTrue(script.indexOf("$old") < script.indexOf("SystemFileAssociations\\$ext\\shell\\Daliwan\""));
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
        long distinctKeys = ContextMenuIntegration.PDF_TO_OFFICE.stream()
                .map(ContextMenuIntegration.MenuVerb::key).distinct().count();
        assertEquals(4, distinctKeys);
        // 四项共用同一级菜单：.pdf 只建一次一级入口（旧键清理里的路径不算）
        assertEquals(1, countOccurrences(script,
                "$base = \"HKCU:\\Software\\Classes\\SystemFileAssociations\\$ext\\shell\\Daliwan\""));
    }

    @Test
    @DisplayName("二级菜单两套写法都写：父键 Shell 子键 + 集中键")
    void writesBothCascadeFlavours() {
        String script = ContextMenuIntegration.installScript(
                ContextMenuIntegration.PDF_TO_OFFICE,
                "\"C:\\Tools\\大力丸.exe\"", "C:\\Tools\\大力丸.exe,0", true);

        assertTrue(script.contains("$item = \"$base\\Shell\\DaliwanPdfToWord\""));
        assertTrue(script.contains("$item = \"HKCU:\\Software\\Classes\\DaliwanMenu\\$ext"
                + "\\shell\\DaliwanPdfToWord\""));
    }

    @Test
    @DisplayName("卸载脚本删掉一级菜单、集中键与旧键")
    void uninstallScriptRemovesOwnKeys() {
        String script = ContextMenuIntegration.uninstallScript(ContextMenuIntegration.ALL);
        assertTrue(script.contains("Remove-Item -Path $base -Recurse -Force"));
        assertTrue(script.contains("SystemFileAssociations\\$ext\\shell\\Daliwan\""));
        assertTrue(script.contains("Software\\Classes\\DaliwanMenu\\$ext\""));
        assertTrue(script.contains("$old = \"HKCU:\\Software\\Classes\\SystemFileAssociations\\$ext"
                + "\\shell\\DaliwanToPdf\""));
        assertTrue(script.contains("Remove-Item -Path $root -Force"), "集中键空了连外壳一起删");
    }

    @Test
    @DisplayName("reg 兜底内容按 .reg 语法逐项列出")
    void regFallbackListsAllKeys() {
        String reg = ContextMenuIntegration.regInstall(
                List.of(ContextMenuIntegration.TO_PDF),
                "\"C:\\Tools\\大力丸.exe\"", "C:\\Tools\\大力丸.exe,0", true);
        assertTrue(reg.startsWith("Windows Registry Editor Version 5.00"));
        assertTrue(reg.contains("[HKEY_CURRENT_USER\\Software\\Classes\\SystemFileAssociations\\.docx\\shell\\Daliwan]"));
        assertTrue(reg.contains("\"MUIVerb\"=\"大力丸\""));
        assertTrue(reg.contains("\"SubCommands\"=\"\""));
        assertTrue(reg.contains("\"ExtendedSubCommandsKey\"=\"Software\\\\Classes\\\\DaliwanMenu\\\\.docx\""));
        assertTrue(reg.contains("[HKEY_CURRENT_USER\\Software\\Classes\\SystemFileAssociations\\.docx"
                + "\\shell\\Daliwan\\Shell\\DaliwanToPdf\\command]"));
        assertTrue(reg.contains("[HKEY_CURRENT_USER\\Software\\Classes\\DaliwanMenu\\.docx"
                + "\\shell\\DaliwanToPdf\\command]"));
        assertTrue(reg.contains("\"MultiSelectModel\"=\"Document\""));
    }

    @Test
    @DisplayName("reg 卸载把一级菜单与集中键都删掉")
    void regUninstallListsEveryVerb() {
        String reg = ContextMenuIntegration.regUninstall(ContextMenuIntegration.ALL);
        assertTrue(reg.contains("[-HKEY_CURRENT_USER\\Software\\Classes\\SystemFileAssociations\\.pdf\\shell\\Daliwan]"));
        assertTrue(reg.contains("[-HKEY_CURRENT_USER\\Software\\Classes\\SystemFileAssociations\\.docx\\shell\\Daliwan]"));
        assertTrue(reg.contains("[-HKEY_CURRENT_USER\\Software\\Classes\\DaliwanMenu\\.pdf]"));
    }

    @Test
    @DisplayName("菜单名与命令行开关一一对应，改文字不会改错命令")
    void verbTextMatchesFlag() {
        assertEquals("转 Word", ContextMenuIntegration.PDF_TO_OFFICE.get(0).menuText());
        assertEquals("--to-docx", ContextMenuIntegration.PDF_TO_OFFICE.get(0).flag());
        assertEquals("转 Excel", ContextMenuIntegration.PDF_TO_OFFICE.get(1).menuText());
        assertEquals("--to-xlsx", ContextMenuIntegration.PDF_TO_OFFICE.get(1).flag());
        assertEquals("转 PPT", ContextMenuIntegration.PDF_TO_OFFICE.get(2).menuText());
        assertEquals("--to-pptx", ContextMenuIntegration.PDF_TO_OFFICE.get(2).flag());
        assertEquals("提取文本", ContextMenuIntegration.PDF_TO_OFFICE.get(3).menuText());
        assertEquals("--to-txt", ContextMenuIntegration.PDF_TO_OFFICE.get(3).flag());
        assertEquals("大力丸", ContextMenuIntegration.MENU_TITLE);
    }

    @Test
    @DisplayName("二级菜单名不再带「大力丸 ·」前缀，一级入口才是大力丸")
    void submenuTextIsShort() {
        for (ContextMenuIntegration.MenuVerb verb : ContextMenuIntegration.ALL) {
            assertFalse(verb.menuText().contains("大力丸"), "二级菜单名不该重复一级名字：" + verb.menuText());
        }
        assertEquals("转 PDF / 转 Word / 转 Excel / 转 PPT / 提取文本",
                ContextMenuIntegration.submenuSummary(ContextMenuIntegration.ALL));
    }

    @Test
    @DisplayName("同一扩展名上的动作会被聚合到一起")
    void groupsVerbsByExtension() {
        var grouped = ContextMenuIntegration.groupByExtension(ContextMenuIntegration.PDF_TO_OFFICE);
        assertEquals(1, grouped.size(), "四个动作共用一个 .pdf 入口");
        assertEquals(4, grouped.get(".pdf").size());
    }

    private static String psTitle() {
        return "'" + ContextMenuIntegration.MENU_TITLE + "'";
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = text.indexOf(needle);
        while (index >= 0) {
            count++;
            index = text.indexOf(needle, index + needle.length());
        }
        return count;
    }
}
