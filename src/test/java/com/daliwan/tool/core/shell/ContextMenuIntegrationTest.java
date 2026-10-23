package com.daliwan.tool.core.shell;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextMenuIntegrationTest {

    private static final String PREFIX = "\"C:\\Tools\\大力丸.exe\"";
    private static final String ICON = "C:\\Tools\\大力丸.exe,0";

    @Test
    @DisplayName("级联父键落在当前用户的 SystemFileAssociations 下")
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
                ContextMenuIntegration.childKeyPath(ContextMenuIntegration.PDF_TO_OFFICE.get(0), ".pdf"));
    }

    @Test
    @DisplayName("平铺项直接挂在 shell 下，键名就是菜单项自己的 key")
    void flatKeySitsDirectlyUnderShell() {
        assertEquals("HKEY_CURRENT_USER\\Software\\Classes\\SystemFileAssociations\\.docx\\shell\\DaliwanToPdf",
                ContextMenuIntegration.flatKeyPath(ContextMenuIntegration.TO_PDF, ".docx"));
    }

    @Test
    @DisplayName("旧版集中键的路径留着卸载时对照用")
    void storeKeyIsPerExtension() {
        assertEquals("HKEY_CURRENT_USER\\Software\\Classes\\DaliwanMenu\\.pdf\\shell\\DaliwanPdfToText",
                ContextMenuIntegration.storeKeyPath(ContextMenuIntegration.PDF_TO_OFFICE.get(3), ".pdf"));
    }

    @Test
    @DisplayName("只有一个动作就平铺，多个动作才收成二级")
    void cascadeOnlyWhenSeveralActions() {
        assertFalse(ContextMenuIntegration.usesCascade(".docx", List.of(ContextMenuIntegration.TO_PDF)),
                ".docx 只能转 PDF，不该多一层子菜单");
        assertTrue(ContextMenuIntegration.usesCascade(".pdf", ContextMenuIntegration.PDF_TO_OFFICE),
                ".pdf 有四个动作，收成二级");
        assertFalse(ContextMenuIntegration.usesCascade(".pdf", ContextMenuIntegration.PDF_TO_OFFICE.subList(0, 1)),
                "只装一个动作时也不该收成二级");
    }

    @Test
    @DisplayName("平铺时菜单名带上品牌前缀，二级里则不重复")
    void flatTextCarriesBrand() {
        assertEquals("大力丸 · 转 PDF", ContextMenuIntegration.flatMenuText(ContextMenuIntegration.TO_PDF));
        assertEquals("转 Word", ContextMenuIntegration.PDF_TO_OFFICE.get(0).menuText());
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
    @DisplayName("单动作扩展名写成平铺项：名字带前缀、直接带 command")
    void singleActionStaysFlat() {
        String script = ContextMenuIntegration.installScript(
                List.of(ContextMenuIntegration.TO_PDF), PREFIX, ICON, true);

        assertTrue(script.contains("SystemFileAssociations\\$ext\\shell\\DaliwanToPdf"),
                "平铺就在 shell 下，不再套一层 Daliwan");
        assertTrue(script.contains("'大力丸 · 转 PDF'"));
        assertFalse(script.contains("'SubCommands'"), "只有一项时不需要子菜单标记");
        assertFalse(script.contains("New-ItemProperty -Path $base"), "平铺时不往一级入口写任何值");
        assertTrue(script.contains(PREFIX + " --to-pdf \"%1\""));
        for (String extension : ContextMenuIntegration.EXTENSIONS) {
            assertTrue(script.contains("'" + extension + "'"), "缺少扩展名：" + extension);
        }
    }

    @Test
    @DisplayName("多动作扩展名收成二级菜单，父键默认值留空")
    void installScriptBuildsCascade() {
        String script = ContextMenuIntegration.installScript(
                ContextMenuIntegration.PDF_TO_OFFICE, PREFIX, ICON, true);

        assertTrue(script.contains("SystemFileAssociations\\$ext\\shell\\Daliwan"));
        assertTrue(script.contains("'MUIVerb'"), "一级菜单名写在 MUIVerb 上");
        assertTrue(script.contains(psTitle()), "一级菜单名是「大力丸」");
        assertFalse(script.contains("New-ItemProperty -Path $base -Name '(default)'"),
                "父键写了默认值就会被当成普通菜单项，子菜单出不来");
        assertTrue(script.contains("[Microsoft.Win32.Registry]::CurrentUser.OpenSubKey"),
                "只能用 .NET 接口删默认值，Remove-ItemProperty 删不掉");
        assertTrue(script.contains("'SubCommands' -Value ''"), "SubCommands 置空才是子菜单");
        assertFalse(script.contains("'ExtendedSubCommandsKey'"),
                "再写一份集中键，资源管理器会把同一批命令枚举两遍，子菜单里每项重复一次");
        assertFalse(script.contains("'Software\\Classes\\DaliwanMenu"));
        assertTrue(script.contains("SHChangeNotify"));
        assertTrue(script.contains("'MultiSelectModel'"));
    }

    @Test
    @DisplayName("收成二级时先把平铺键清掉，避免右键里出现两套菜单")
    void installScriptCleansFlatKeysWhenCascading() {
        String script = ContextMenuIntegration.installScript(
                ContextMenuIntegration.PDF_TO_OFFICE, PREFIX, ICON, true);

        assertTrue(script.contains("$old = @('DaliwanToPdf','DaliwanPdfToWord'"),
                "升级上来的平铺键要在写入二级菜单之前清掉");
        assertTrue(script.contains("$p = \"HKCU:\\Software\\Classes\\SystemFileAssociations"
                + "\\$ext\\shell\\$k\""));
        assertTrue(script.indexOf("$old = @(") < script.indexOf("$base = \"HKCU:\\Software\\Classes"
                + "\\SystemFileAssociations\\$ext\\shell\\Daliwan\""),
                "清理必须发生在写入之前");
    }

    @Test
    @DisplayName("平铺时不删自己的键，也不碰别的扩展名")
    void installScriptKeepsOwnFlatKey() {
        String script = ContextMenuIntegration.installScript(
                List.of(ContextMenuIntegration.TO_PDF), PREFIX, ICON, true);

        assertFalse(script.contains("$old = @('DaliwanToPdf'"),
                "平铺形态下 DaliwanToPdf 正是要写的键，不能当成旧键删掉");
        assertTrue(script.contains("$old = @('DaliwanPdfToWord'"),
                "同扩展名上其它动作的残留仍然要清");
    }

    @Test
    @DisplayName("装 PDF 组不会顺手删掉 Office 组的平铺键")
    void installingPdfGroupLeavesOfficeGroupAlone() {
        String script = ContextMenuIntegration.installScript(
                ContextMenuIntegration.PDF_TO_OFFICE, PREFIX, ICON, true);

        // 两个开关是分开安装的：PDF 组只处理 .pdf，不能遍历到 .xlsx / .docx 上去
        assertFalse(script.contains("'.xlsx'"), "安装 PDF 组时不该出现 Office 扩展名");
        assertFalse(script.contains("'.docx'"), "安装 PDF 组时不该出现 Office 扩展名");
        assertTrue(script.contains("'.pdf'"));
    }

    @Test
    @DisplayName("没有自带图标时脚本不写 Icon 值")
    void installScriptSkipsIconWhenUnavailable() {
        String script = ContextMenuIntegration.installScript(
                List.of(ContextMenuIntegration.TO_PDF), "\"java.exe\" -jar app.jar", "java.exe,0", false);
        assertFalse(script.contains("'Icon'"));
    }

    @Test
    @DisplayName("PDF 那一组四个菜单项各写各的开关，互不串台")
    void pdfVerbsUseTheirOwnFlags() {
        String script = ContextMenuIntegration.installScript(
                ContextMenuIntegration.PDF_TO_OFFICE, PREFIX, ICON, true);

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
    @DisplayName("二级菜单只写父键 Shell 子键一份，避免子菜单重复")
    void writesOnlyOneCascadeFlavour() {
        String script = ContextMenuIntegration.installScript(
                ContextMenuIntegration.PDF_TO_OFFICE, PREFIX, ICON, true);

        assertTrue(script.contains("$item = \"$base\\Shell\\DaliwanPdfToWord\""));
        assertFalse(script.contains("HKCU:\\Software\\Classes\\DaliwanMenu\\$ext"),
                "集中键不再写入：两套并存会让子菜单每项出现两次");
        // 每个动作只建一次键
        assertEquals(4, countOccurrences(script, "$item = \"$base\\Shell\\"));
        assertEquals(4, countOccurrences(script, "$run = \"$item\\command\""));
    }

    @Test
    @DisplayName("卸载脚本删掉一级菜单、集中键、平铺键与旧键")
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
    @DisplayName("reg 兜底：单动作写平铺项")
    void regFallbackWritesFlatItem() {
        String reg = ContextMenuIntegration.regInstall(
                List.of(ContextMenuIntegration.TO_PDF), PREFIX, ICON, true);
        assertTrue(reg.startsWith("Windows Registry Editor Version 5.00"));
        assertTrue(reg.contains("[HKEY_CURRENT_USER\\Software\\Classes\\SystemFileAssociations\\.docx"
                + "\\shell\\DaliwanToPdf]"));
        assertTrue(reg.contains("@=\"大力丸 · 转 PDF\""));
        assertTrue(reg.contains("[HKEY_CURRENT_USER\\Software\\Classes\\SystemFileAssociations\\.docx"
                + "\\shell\\DaliwanToPdf\\command]"));
        assertTrue(reg.contains("\"MultiSelectModel\"=\"Document\""));
        assertFalse(reg.contains("\"SubCommands\""), "单动作不写子菜单标记");
    }

    @Test
    @DisplayName("reg 兜底：多动作写级联父键，默认值为空")
    void regFallbackWritesCascade() {
        String reg = ContextMenuIntegration.regInstall(
                ContextMenuIntegration.PDF_TO_OFFICE, PREFIX, ICON, true);
        assertTrue(reg.contains("[HKEY_CURRENT_USER\\Software\\Classes\\SystemFileAssociations\\.pdf"
                + "\\shell\\Daliwan]"));
        assertTrue(reg.contains("\"MUIVerb\"=\"大力丸\""));
        assertTrue(reg.contains("\"SubCommands\"=\"\""));
        assertFalse(reg.contains("\"ExtendedSubCommandsKey\""), "集中键不再写入，避免子菜单重复");
        assertTrue(reg.contains("[HKEY_CURRENT_USER\\Software\\Classes\\SystemFileAssociations\\.pdf"
                + "\\shell\\Daliwan]\r\n@=\"\""), "父键默认值为空，否则不会展开子菜单");
        assertTrue(reg.contains("[HKEY_CURRENT_USER\\Software\\Classes\\SystemFileAssociations\\.pdf"
                + "\\shell\\Daliwan\\Shell\\DaliwanPdfToWord\\command]"));
        assertFalse(reg.contains("DaliwanMenu"), "reg 兜底也不再写集中键");
    }

    @Test
    @DisplayName("reg 卸载把级联键、集中键、平铺键都删掉")
    void regUninstallListsEveryVerb() {
        String reg = ContextMenuIntegration.regUninstall(ContextMenuIntegration.ALL);
        assertTrue(reg.contains("[-HKEY_CURRENT_USER\\Software\\Classes\\SystemFileAssociations\\.pdf\\shell\\Daliwan]"));
        assertTrue(reg.contains("[-HKEY_CURRENT_USER\\Software\\Classes\\SystemFileAssociations\\.docx\\shell\\Daliwan]"));
        assertTrue(reg.contains("[-HKEY_CURRENT_USER\\Software\\Classes\\DaliwanMenu\\.pdf]"));
        assertTrue(reg.contains("[-HKEY_CURRENT_USER\\Software\\Classes\\SystemFileAssociations\\.docx"
                + "\\shell\\DaliwanToPdf]"));
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
