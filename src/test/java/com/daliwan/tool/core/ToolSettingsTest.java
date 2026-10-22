package com.daliwan.tool.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolSettingsTest {

    @Test
    @DisplayName("配置文件不存在时全部可见")
    void defaultsToEverythingVisible(@TempDir Path dir) {
        ToolSettings settings = ToolSettings.load(dir.resolve("missing.properties"));
        assertTrue(settings.isVisible("hash"));
        assertTrue(settings.isVisible("json"));
        assertTrue(settings.hiddenIds().isEmpty());
    }

    @Test
    @DisplayName("隐藏项保存后重新加载仍然生效")
    void persistsHiddenEntries(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("settings.properties");

        ToolSettings first = ToolSettings.load(file);
        first.setVisible("rsa", false);
        first.setVisible("json", false);
        first.save();

        assertTrue(Files.exists(file));

        ToolSettings reloaded = ToolSettings.load(file);
        assertFalse(reloaded.isVisible("rsa"));
        assertFalse(reloaded.isVisible("json"));
        assertTrue(reloaded.isVisible("hash"));
        assertEquals(2, reloaded.hiddenIds().size());
    }

    @Test
    @DisplayName("重新勾选后从隐藏列表移除")
    void reEnablingRemovesFromHidden(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("settings.properties");
        ToolSettings settings = ToolSettings.load(file);
        settings.setVisible("base64", false);
        settings.save();

        ToolSettings reloaded = ToolSettings.load(file);
        assertFalse(reloaded.isVisible("base64"));
        reloaded.setVisible("base64", true);
        reloaded.save();

        assertTrue(ToolSettings.load(file).isVisible("base64"));
    }

    @Test
    @DisplayName("配置文件为 UTF-8 纯文本，便于手工编辑")
    void writesReadableProperties(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("settings.properties");
        ToolSettings settings = ToolSettings.load(file);
        settings.setVisible("unicode", false);
        settings.save();

        String content = Files.readString(file);
        assertTrue(content.contains("nav.hidden=unicode"), "实际内容：" + content);
    }

    @Test
    @DisplayName("父目录不存在时自动创建")
    void createsParentDirectories(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("nested").resolve("deeper").resolve("settings.properties");
        ToolSettings settings = ToolSettings.load(file);
        settings.setVisible("url", false);
        settings.save();

        assertTrue(Files.exists(file));
    }

    @Test
    @DisplayName("主题默认为跟随系统")
    void defaultsToSystemTheme(@TempDir Path dir) {
        ToolSettings settings = ToolSettings.load(dir.resolve("missing.properties"));
        assertEquals(ToolSettings.THEME_SYSTEM, settings.theme());
        assertFalse(settings.themeExplicit());
    }

    @Test
    @DisplayName("主题选择保存后重新加载仍生效")
    void persistsTheme(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("settings.properties");

        ToolSettings first = ToolSettings.load(file);
        first.setTheme(ToolSettings.THEME_LIGHT);
        first.save();

        ToolSettings reloaded = ToolSettings.load(file);
        assertEquals(ToolSettings.THEME_LIGHT, reloaded.theme());
        assertTrue(reloaded.themeExplicit());
        assertTrue(Files.readString(file).contains("ui.theme=light"));
    }

    @Test
    @DisplayName("旧版本存下的深色当作默认值，改为跟随系统")
    void treatsLegacyDarkAsDefault(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("settings.properties");
        Files.writeString(file, "ui.theme=dark\n");

        assertEquals(ToolSettings.THEME_SYSTEM, ToolSettings.load(file).theme());
    }

    @Test
    @DisplayName("手动选择过的深色在升级后继续生效")
    void keepsExplicitDark(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("settings.properties");
        Files.writeString(file, "ui.theme=dark\nui.theme.explicit=true\n");

        ToolSettings settings = ToolSettings.load(file);
        assertEquals(ToolSettings.THEME_DARK, settings.theme());
        assertTrue(settings.themeExplicit());
    }

    @Test
    @DisplayName("右侧菜单开关默认关闭")
    void shellPdfMenuDefaultsOff(@TempDir Path dir) {
        assertFalse(ToolSettings.load(dir.resolve("missing.properties")).shellPdfMenu());
    }

    @Test
    @DisplayName("右键菜单开关保存后重新加载仍生效")
    void persistsShellPdfMenu(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("settings.properties");

        ToolSettings first = ToolSettings.load(file);
        first.setShellPdfMenu(true);
        first.save();

        assertTrue(ToolSettings.load(file).shellPdfMenu());
        assertTrue(Files.readString(file).contains("shell.pdf.menu=true"));
    }

    @Test
    @DisplayName("旧配置文件没有右键菜单项时按关闭处理")
    void legacyFileWithoutShellKey(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("settings.properties");
        Files.writeString(file, "nav.hidden=rsa\nui.theme=light\nui.theme.explicit=true\n");

        ToolSettings settings = ToolSettings.load(file);
        assertFalse(settings.shellPdfMenu());
        assertFalse(settings.isVisible("rsa"));
    }

    @Test
    @DisplayName("非法主题值被忽略，保留原值")
    void ignoresUnknownTheme(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("settings.properties");
        Files.writeString(file, "ui.theme=neon\nui.theme.explicit=true\n");

        assertEquals(ToolSettings.THEME_SYSTEM, ToolSettings.load(file).theme());

        ToolSettings settings = ToolSettings.load(file);
        settings.setTheme("neon");
        assertEquals(ToolSettings.THEME_SYSTEM, settings.theme(), "未知主题不应被接受");
    }
}
