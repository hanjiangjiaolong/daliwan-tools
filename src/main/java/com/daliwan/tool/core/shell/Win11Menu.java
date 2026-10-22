package com.daliwan.tool.core.shell;

import java.io.IOException;
import java.util.Locale;

/**
 * 让 Windows 11 的右键菜单直接显示第三方项。
 *
 * <p>Win11 换了新的精简右键菜单，只认实现了 {@code IExplorerCommand} 的 COM 组件；
 * 传统写在 {@code shell\<verb>\command} 下的菜单项一律被折叠进「显示更多选项」。
 * 做成 COM 组件要另外随包发布一个 native DLL（还得带 MSIX 包标识），代价太大，
 * 标准做法是改回经典菜单：只要在 {@code HKCU\Software\Classes\CLSID} 下建一个指向
 * 空 {@code InprocServer32} 的键，外壳就认为「新版菜单已被替换」，从而恢复 Win10
 * 那套完整菜单。
 *
 * <p>要认清这是<b>绕过准入条件</b>，不是满足它：我们并没有变成新菜单认的那种扩展，
 * 只是把菜单换了个样式，代价因而是全局的——所有程序、所有文件的右键都会跟着变样。
 * 所以界面上的开关默认关闭，勾选后保存时还要再确认一次。
 *
 * <p>键写在当前用户下，不需要管理员权限；删掉这棵键即恢复 Win11 默认外观。
 * 两种状态都要重启一次资源管理器才会生效——外壳只在启动时读这个开关。
 */
public final class Win11Menu {

    /**
     * 外壳用来判断「是否启用新版右键菜单」的 CLSID。
     *
     * <p>这是 Windows 自带组件的标识，不含我们的代码；{@code InprocServer32} 留空表示
     * 「不要加载它」，外壳于是退回经典菜单。
     */
    public static final String CLSID = "{86ca1aa0-34aa-4e8b-a509-50c905bae2a2}";

    /** Win11 的首个构建号（21H2）。 */
    private static final int FIRST_WIN11_BUILD = 22000;

    /** 探测超时：外部进程卡住时不能拖着界面。 */
    private static final long TIMEOUT_MS = 2000;

    /** 只探测一次：版本号在一次运行里不会变。 */
    private static Boolean windows11;

    private Win11Menu() {
    }

    /** 经典菜单开关所在的注册表键。 */
    public static String keyPath() {
        return "HKEY_CURRENT_USER\\Software\\Classes\\CLSID\\" + CLSID + "\\InprocServer32";
    }

    /**
     * 当前是否 Windows 11。
     *
     * <p>不能用 {@code os.name} 判断：Win11 的 {@code ProductName} 至今仍写着
     * "Windows 10"，只有构建号能区分。探测失败按 Win10 处理，避免在旧系统上
     * 显示一个没有意义的开关。
     */
    public static synchronized boolean isWindows11() {
        if (windows11 == null) {
            windows11 = detect();
        }
        return windows11;
    }

    private static boolean detect() {
        if (!AppLauncher.isWindows()) {
            return false;
        }
        String output = queryBuild();
        int build = parseBuild(output);
        return build >= FIRST_WIN11_BUILD;
    }

    /**
     * 解析版本号输出，识别不出时返回 0。
     *
     * <p>要求整行都是数字：PowerShell 报错文本里也可能带一串数字，认错就会在
     * Windows 10 上显示一个没用的开关。单独抽出来便于测试。
     */
    static int parseBuild(String output) {
        if (output == null) {
            return 0;
        }
        for (String line : output.split("\\R")) {
            String text = line.replace("\uFEFF", "").strip();
            if (!text.matches("\\d{4,6}")) {
                continue;
            }
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                // 超长数字串，按识别失败处理
            }
        }
        return 0;
    }

    /** 读出构建号；任何异常都当成「不知道」，返回空串。 */
    private static String queryBuild() {
        String script = "$ErrorActionPreference = 'Stop'\r\n"
                + "(Get-ItemProperty 'HKLM:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion')"
                + ".CurrentBuildNumber\r\n";
        try {
            return ContextMenuIntegration.runPowerShellForOutput(script, TIMEOUT_MS);
        } catch (Exception e) {
            return "";
        }
    }

    /** 开启或关闭经典右键菜单；关闭即删除本类创建的那棵键。 */
    public static void setEnabled(boolean enabled) throws IOException {
        ContextMenuIntegration.runPowerShell(enabled ? installScript() : uninstallScript(), TIMEOUT_MS);
    }

    /**
     * 读注册表判断当前是否已换成经典菜单。
     *
     * <p>以注册表为准而不是只信配置文件：用户可能用别的工具改过，也可能在
     * 系统还原后把键丢了。探测失败返回 {@code null}，表示「不知道」，调用方应保持原状。
     */
    public static Boolean isEnabled() {
        String script = "$ErrorActionPreference = 'Stop'\r\n"
                + "Test-Path " + ps(toPowerShellPath(
                        "HKEY_CURRENT_USER\\Software\\Classes\\CLSID\\" + CLSID)) + "\r\n";
        try {
            String output = ContextMenuIntegration.runPowerShellForOutput(script, TIMEOUT_MS);
            String text = output.replace("\uFEFF", "").strip().toLowerCase(Locale.ROOT);
            if (text.contains("true")) {
                return Boolean.TRUE;
            }
            if (text.contains("false")) {
                return Boolean.FALSE;
            }
        } catch (Exception ignored) {
            // 读不到就交给调用方沿用配置
        }
        return null;
    }

    /**
     * 重启资源管理器，让注册表改动生效。
     *
     * <p>外壳进程被杀掉后 Windows 一般会自己拉起来，但这里再显式启动一次，
     * 免得出现「桌面没了」的空档。已经打开的文件夹窗口会关掉，文件本身不受影响。
     */
    public static void restartExplorer() throws IOException {
        ContextMenuIntegration.runPowerShell(restartScript(), 8000);
    }

    // ---------------------------------------------------------------- 脚本

    /** 建键并把 {@code InprocServer32} 的默认值写成空串。 */
    static String installScript() {
        String path = toPowerShellPath(keyPath());
        return "$ErrorActionPreference = 'Stop'\r\n"
                + "$key = " + ps(path) + "\r\n"
                + "New-Item -Path $key -Force | Out-Null\r\n"
                + "New-ItemProperty -Path $key -Name '(default)' -Value '' -PropertyType String "
                + "-Force | Out-Null\r\n";
    }

    /** 删掉整个 CLSID 分支，恢复 Win11 新版菜单。 */
    static String uninstallScript() {
        String path = toPowerShellPath(
                "HKEY_CURRENT_USER\\Software\\Classes\\CLSID\\" + CLSID);
        return "$ErrorActionPreference = 'Stop'\r\n"
                + "$key = " + ps(path) + "\r\n"
                + "if (Test-Path $key) { Remove-Item $key -Recurse -Force }\r\n";
    }

    /** 结束并重新拉起 explorer.exe。 */
    static String restartScript() {
        return "$ErrorActionPreference = 'Stop'\r\n"
                + "Stop-Process -Name explorer -Force -ErrorAction SilentlyContinue\r\n"
                + "Start-Sleep -Milliseconds 700\r\n"
                + "if (-not (Get-Process -Name explorer -ErrorAction SilentlyContinue)) {\r\n"
                + "  Start-Process explorer.exe\r\n"
                + "}\r\n";
    }

    /** 注册表路径转成 PowerShell 的 Provider 路径：HKEY_CURRENT_USER → HKCU:。 */
    static String toPowerShellPath(String registryPath) {
        String value = registryPath;
        if (value.regionMatches(true, 0, "HKEY_CURRENT_USER\\", 0, 18)) {
            value = "HKCU:\\" + value.substring(18);
        } else if (value.regionMatches(true, 0, "HKEY_LOCAL_MACHINE\\", 0, 19)) {
            value = "HKLM:\\" + value.substring(19);
        }
        return value.replace('/', '\\');
    }

    /** 单引号字符串：PowerShell 里靠重复单引号转义。 */
    private static String ps(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    /** 供界面展示的说明，含当前系统判断结果。 */
    public static String summary() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("win")) {
            return "仅 Windows 可用";
        }
        return isWindows11() ? "Windows 11 已识别" : "当前系统不需要这项设置";
    }
}
