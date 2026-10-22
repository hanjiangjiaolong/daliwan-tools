package com.daliwan.tool.core.shell;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Win11 经典右键菜单的纯逻辑测试：不触碰注册表，只校验路径、脚本与版本号解析。
 */
class Win11MenuTest {

    @Test
    void keyPathPointsAtInprocServer32() {
        assertEquals("HKEY_CURRENT_USER\\Software\\Classes\\CLSID\\"
                        + Win11Menu.CLSID + "\\InprocServer32",
                Win11Menu.keyPath());
        assertTrue(Win11Menu.keyPath().contains("{86ca1aa0-34aa-4e8b-a509-50c905bae2a2}"));
    }

    @Test
    void parsesWindows11BuildNumbers() {
        assertTrue(Win11Menu.parseBuild("22631") >= 22000);
        assertTrue(Win11Menu.parseBuild("26100\r\n") >= 22000);
        assertTrue(Win11Menu.parseBuild("26300") >= 22000);
    }

    @Test
    void windows10BuildIsBelowThreshold() {
        assertTrue(Win11Menu.parseBuild("19045") < 22000);
        assertTrue(Win11Menu.parseBuild("17763") < 22000);
    }

    @Test
    void toleratesBomAndBlankLines() {
        assertEquals(22621, Win11Menu.parseBuild("\uFEFF22621\r\n"));
        assertEquals(22621, Win11Menu.parseBuild("\r\n\r\n22621"));
    }

    @Test
    void unknownOutputIsZero() {
        assertEquals(0, Win11Menu.parseBuild(null));
        assertEquals(0, Win11Menu.parseBuild(""));
        assertEquals(0, Win11Menu.parseBuild("Get-ItemProperty : Cannot find path"));
        // 报错文本里带数字时不能当成构建号，否则 Win10 会被误判成 Win11
        assertEquals(0, Win11Menu.parseBuild("Cannot find path 22631 because it does not exist"));
    }

    @Test
    void convertsRegistryPathToPowerShellProvider() {
        assertEquals("HKCU:\\Software\\Classes\\CLSID",
                Win11Menu.toPowerShellPath("HKEY_CURRENT_USER\\Software\\Classes\\CLSID"));
        assertEquals("HKLM:\\SOFTWARE\\Microsoft",
                Win11Menu.toPowerShellPath("HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft"));
    }

    @Test
    void installScriptWritesEmptyDefaultValue() {
        String script = Win11Menu.installScript();
        assertTrue(script.contains("HKCU:\\Software\\Classes\\CLSID"));
        assertTrue(script.contains("New-Item"));
        // 关键是这个空值：外壳据此认为新版菜单被替换掉了
        assertTrue(script.contains("-Name '(default)' -Value ''"));
        assertFalse(script.contains("DaliwanToPdf"));
    }

    @Test
    void uninstallScriptRemovesWholeClsidTree() {
        String script = Win11Menu.uninstallScript();
        assertTrue(script.contains("Remove-Item"));
        assertTrue(script.contains("-Recurse"));
        assertFalse(script.contains("InprocServer32"));
        assertTrue(script.contains(Win11Menu.CLSID));
    }

    @Test
    void restartScriptRelaunchesExplorer() {
        String script = Win11Menu.restartScript();
        assertTrue(script.contains("Stop-Process -Name explorer"));
        assertTrue(script.contains("Start-Process explorer.exe"));
    }

    @Test
    void installAndUninstallAreMirrors() {
        assertTrue(Win11Menu.installScript().contains(Win11Menu.CLSID));
        assertTrue(Win11Menu.uninstallScript().contains(Win11Menu.CLSID));
    }
}
