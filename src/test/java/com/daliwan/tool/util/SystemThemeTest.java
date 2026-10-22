package com.daliwan.tool.util;

import com.daliwan.tool.core.ToolSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemThemeTest {

    @Test
    @DisplayName("系统配色探测只会返回深色或浅色，且不会抛异常")
    void detectsWithoutThrowing() {
        String theme = SystemTheme.detect();
        assertTrue(ToolSettings.THEME_DARK.equals(theme) || ToolSettings.THEME_LIGHT.equals(theme),
                "探测结果应为 dark 或 light，实际：" + theme);
    }

    @Test
    @DisplayName("解析注册表输出：0 为深色、1 为浅色")
    void parsesRegistryValue() {
        // 中文 Windows 下表头是本地化的，但键名与 REG_DWORD 仍是 ASCII
        String dark = "HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize\r\n"
                + "    AppsUseLightTheme    REG_DWORD    0x0\r\n";
        String light = "    AppsUseLightTheme    REG_DWORD    0x1\r\n";

        assertEquals(ToolSettings.THEME_DARK, SystemTheme.parseWindowsOutput(dark));
        assertEquals(ToolSettings.THEME_LIGHT, SystemTheme.parseWindowsOutput(light));
    }

    @Test
    @DisplayName("解析注册表输出：键不存在时返回 null，由调用方退回浅色")
    void returnsNullWhenKeyMissing() {
        assertNull(SystemTheme.parseWindowsOutput("错误: 系统找不到指定的注册表项或值。"));
        assertNull(SystemTheme.parseWindowsOutput(""));
        assertNull(SystemTheme.parseWindowsOutput(null));
    }
}
