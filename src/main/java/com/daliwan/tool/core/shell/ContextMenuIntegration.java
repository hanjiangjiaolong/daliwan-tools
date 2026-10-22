package com.daliwan.tool.core.shell;

import com.daliwan.tool.core.doc.PdfToOfficeConverter;
import com.daliwan.tool.core.doc.StirlingEngine;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 把大力丸的转换动作写进资源管理器右键菜单。
 *
 * <p>注册位置是 {@code HKEY_CURRENT_USER\Software\Classes\SystemFileAssociations\<扩展名>\shell}，
 * 只挂在「按扩展名关联」这一层，不动各文件类型的默认打开方式，也不需要管理员权限；
 * 关掉开关时按同一个键整棵删除，不留残余。
 *
 * <p>菜单项是「动词」（verb）驱动的：一个动词 = 一个注册表子键 + 一条命令行 + 一组扩展名。
 * 现在是两类共五项——Office 文件上出现「转 PDF」，PDF 上出现「转 Word / 转 Excel /
 * 转 PPT / 提取文本」。加新格式只要往 {@link #PDF_TO_OFFICE} 里加一项，
 * 注册表脚本、卸载脚本、兜底的 .reg 都会自动跟上。
 *
 * <p>写注册表优先走 PowerShell：一次进程调用就能写完十几种扩展名，中文菜单名不受
 * 控制台代码页影响，还能顺手调用 {@code SHChangeNotify} 让资源管理器立刻刷新缓存。
 * PowerShell 被策略挡住时退回 {@code reg import}（先写一份临时 .reg）。
 */
public final class ContextMenuIntegration {

    /**
     * 一个右键菜单项。
     *
     * @param key        注册表子键名，改它等于换一棵键（旧键要另行清理）
     * @param menuText   菜单上显示的文字
     * @param flag       交给程序的命令行开关，与命令行解析保持一致
     * @param extensions 挂这个菜单的文件扩展名
     */
    public record MenuVerb(String key, String menuText, String flag, List<String> extensions) {
    }

    /** Office → PDF：挂在 Word / Excel / PPT 等文件上。 */
    public static final MenuVerb TO_PDF = new MenuVerb("DaliwanToPdf", "大力丸 · 转 PDF",
            "--to-pdf", List.copyOf(StirlingEngine.extensions()));

    /** PDF → Office：都挂在 .pdf 上，一个目标一项，用户要什么点什么。 */
    public static final List<MenuVerb> PDF_TO_OFFICE = List.of(
            new MenuVerb("DaliwanPdfToWord", "大力丸 · 转 Word",
                    PdfToOfficeConverter.Target.WORD.flag(), List.of(".pdf")),
            new MenuVerb("DaliwanPdfToExcel", "大力丸 · 转 Excel",
                    PdfToOfficeConverter.Target.EXCEL.flag(), List.of(".pdf")),
            new MenuVerb("DaliwanPdfToSlides", "大力丸 · 转 PPT",
                    PdfToOfficeConverter.Target.SLIDES.flag(), List.of(".pdf")),
            new MenuVerb("DaliwanPdfToText", "大力丸 · 提取文本",
                    PdfToOfficeConverter.Target.TEXT.flag(), List.of(".pdf")));

    /** 全部菜单项：一个开关全开时用得到。 */
    public static final List<MenuVerb> ALL;

    static {
        List<MenuVerb> all = new ArrayList<>();
        all.add(TO_PDF);
        all.addAll(PDF_TO_OFFICE);
        ALL = List.copyOf(all);
    }

    /** 右键菜单项标识（Office 转 PDF 这一个），保留给旧配置与文档引用。 */
    public static final String VERB = TO_PDF.key();

    /** 右键菜单上显示的文字（Office 转 PDF）。 */
    public static final String MENU_TEXT = TO_PDF.menuText();

    /** 交给程序的命令行开关（Office 转 PDF）。 */
    public static final String FLAG = TO_PDF.flag();

    /** 挂「转 PDF」的扩展名，跟内置引擎的能力范围保持一致。 */
    public static final List<String> EXTENSIONS = TO_PDF.extensions();

    private static final String REG_HEADER = "Windows Registry Editor Version 5.00";

    /** SHCNE_ASSOCCHANGED：告诉外壳「文件关联变了，重读菜单」。 */
    private static final int SHCNE_ASSOCCHANGED = 0x08000000;

    /** 注册表写入的默认超时：十几个键加一次外壳刷新，正常几百毫秒。 */
    private static final long DEFAULT_TIMEOUT_MS = 20_000;

    private ContextMenuIntegration() {
    }

    /** 当前这份程序能否被右键菜单调用（打包版或 java -jar 形态都可以）。 */
    public static boolean isSupported() {
        return AppLauncher.resolve().isPresent();
    }

    /** 注册表键路径，便于排查与文档说明。 */
    public static String keyPath(String extension) {
        return keyPath(TO_PDF, extension);
    }

    /** 指定菜单项的注册表键路径。 */
    public static String keyPath(MenuVerb verb, String extension) {
        String normalized = extension.startsWith(".") ? extension : "." + extension;
        return "HKEY_CURRENT_USER\\Software\\Classes\\SystemFileAssociations\\"
                + normalized + "\\shell\\" + verb.key();
    }

    /** 右键菜单实际执行的命令行模板（Office 转 PDF）。 */
    public static String commandValue(String quotedPrefix) {
        return commandValue(TO_PDF, quotedPrefix);
    }

    /** 指定菜单项实际执行的命令行模板。 */
    public static String commandValue(MenuVerb verb, String quotedPrefix) {
        return quotedPrefix + " " + verb.flag() + " \"%1\"";
    }

    /** 当前程序写进注册表的命令行（Office 转 PDF），用于判断路径是否已经变了。 */
    public static String currentCommand() throws IOException {
        return currentCommand(TO_PDF);
    }

    /** 当前程序写进注册表的命令行，用于判断路径是否已经变了。 */
    public static String currentCommand(MenuVerb verb) throws IOException {
        AppLauncher.Spec spec = AppLauncher.resolve()
                .orElseThrow(() -> new IOException("无法确定程序启动路径，右键菜单不可用"));
        return commandValue(verb, spec.quotedPrefix());
    }

    /** 写入给定菜单项。 */
    public static void install(Collection<MenuVerb> verbs) throws IOException {
        if (verbs == null || verbs.isEmpty()) {
            return;
        }
        AppLauncher.Spec spec = AppLauncher.resolve()
                .orElseThrow(() -> new IOException("无法确定程序启动路径，右键菜单不可用"));
        String icon = spec.executable() + ",0";

        try {
            runPowerShell(installScript(verbs, spec.quotedPrefix(), icon, spec.ownIcon()));
        } catch (IOException powershellError) {
            // 少数机器禁用了 PowerShell：退回 reg import，只是没法立刻刷新外壳缓存
            try {
                importReg(regInstall(verbs, spec.quotedPrefix(), icon, spec.ownIcon()));
                notifyShell();
            } catch (IOException regError) {
                throw new IOException("写入右键菜单失败：" + regError.getMessage()
                        + "（PowerShell：" + powershellError.getMessage() + "）", regError);
            }
        }
    }

    /** 删除给定菜单项，只删本程序建的那一层键。 */
    public static void uninstall(Collection<MenuVerb> verbs) throws IOException {
        if (verbs == null || verbs.isEmpty()) {
            return;
        }
        try {
            runPowerShell(uninstallScript(verbs));
        } catch (IOException powershellError) {
            try {
                importReg(regUninstall(verbs));
                notifyShell();
            } catch (IOException regError) {
                throw new IOException("移除右键菜单失败：" + regError.getMessage()
                        + "（PowerShell：" + powershellError.getMessage() + "）", regError);
            }
        }
    }

    // ---------------------------------------------------------------- 脚本生成

    /** PowerShell 安装脚本：建键、写值、通知外壳，一次进程调用完成。 */
    static String installScript(Collection<MenuVerb> verbs, String quotedPrefix,
                                String icon, boolean withIcon) {
        StringBuilder script = new StringBuilder();
        script.append("$ErrorActionPreference = 'Stop'\r\n");
        for (MenuVerb verb : verbs) {
            script.append("$menu = ").append(psString(verb.menuText())).append("\r\n");
            script.append("$cmd = ").append(psString(commandValue(verb, quotedPrefix)))
                    .append("\r\n");
            script.append("$exts = ").append(psArray(verb.extensions())).append("\r\n");
            script.append("foreach ($ext in $exts) {\r\n");
            script.append("  $base = \"HKCU:\\Software\\Classes\\SystemFileAssociations\\$ext\\shell\\")
                    .append(verb.key()).append("\"\r\n");
            script.append("  New-Item -Path $base -Force | Out-Null\r\n");
            script.append("  New-ItemProperty -Path $base -Name '(default)' -Value $menu ")
                    .append("-PropertyType String -Force | Out-Null\r\n");
            if (withIcon) {
                script.append("  New-ItemProperty -Path $base -Name 'Icon' -Value ")
                        .append(psString(icon)).append(" -PropertyType String -Force | Out-Null\r\n");
            }
            script.append("  New-ItemProperty -Path $base -Name 'MultiSelectModel' -Value 'Document' ")
                    .append("-PropertyType String -Force | Out-Null\r\n");
            script.append("  New-Item -Path \"$base\\command\" -Force | Out-Null\r\n");
            script.append("  New-ItemProperty -Path \"$base\\command\" -Name '(default)' -Value $cmd ")
                    .append("-PropertyType String -Force | Out-Null\r\n");
            script.append("}\r\n");
        }
        script.append(notifySnippet());
        return script.toString();
    }

    /** PowerShell 卸载脚本：只删本程序建的那一层键。 */
    static String uninstallScript(Collection<MenuVerb> verbs) {
        StringBuilder script = new StringBuilder();
        script.append("$ErrorActionPreference = 'Stop'\r\n");
        for (MenuVerb verb : verbs) {
            script.append("$exts = ").append(psArray(verb.extensions())).append("\r\n");
            script.append("foreach ($ext in $exts) {\r\n");
            script.append("  $base = \"HKCU:\\Software\\Classes\\SystemFileAssociations\\$ext\\shell\\")
                    .append(verb.key()).append("\"\r\n");
            script.append("  if (Test-Path $base) { Remove-Item -Path $base -Recurse -Force }\r\n");
            script.append("}\r\n");
        }
        script.append(notifySnippet());
        return script.toString();
    }

    /** 通知外壳刷新关联缓存，否则右键菜单可能要等重启才出现。 */
    private static String notifySnippet() {
        return "$sig = '[DllImport(\"shell32.dll\")] public static extern void SHChangeNotify(int e, int f, IntPtr a, IntPtr b);'\r\n"
                + "Add-Type -MemberDefinition $sig -Name Shell32 -Namespace Win32 -ErrorAction SilentlyContinue\r\n"
                + "[Win32.Shell32]::SHChangeNotify(" + SHCNE_ASSOCCHANGED + ", 0, [IntPtr]::Zero, [IntPtr]::Zero)\r\n";
    }

    /** .reg 安装内容（PowerShell 不可用时的兜底）。 */
    static String regInstall(Collection<MenuVerb> verbs, String quotedPrefix,
                             String icon, boolean withIcon) {
        StringBuilder reg = new StringBuilder(REG_HEADER).append("\r\n\r\n");
        for (MenuVerb verb : verbs) {
            for (String extension : verb.extensions()) {
                String key = keyPath(verb, extension);
                reg.append('[').append(key).append("]\r\n");
                reg.append("@=\"").append(escape(verb.menuText())).append("\"\r\n");
                if (withIcon) {
                    reg.append("\"Icon\"=\"").append(escape(icon)).append("\"\r\n");
                }
                reg.append("\"MultiSelectModel\"=\"Document\"\r\n");
                reg.append('[').append(key).append("\\command]\r\n");
                reg.append("@=\"").append(escape(commandValue(verb, quotedPrefix)))
                        .append("\"\r\n\r\n");
            }
        }
        return reg.toString();
    }

    /** .reg 卸载内容：减号前缀表示删除整棵键。 */
    static String regUninstall(Collection<MenuVerb> verbs) {
        StringBuilder reg = new StringBuilder(REG_HEADER).append("\r\n\r\n");
        for (MenuVerb verb : verbs) {
            for (String extension : verb.extensions()) {
                reg.append("[-").append(keyPath(verb, extension)).append("]\r\n");
            }
        }
        return reg.toString();
    }

    private static String psString(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static String psArray(List<String> values) {
        List<String> quoted = new ArrayList<>(values.size());
        for (String value : values) {
            quoted.add(psString(value));
        }
        return "@(" + String.join(",", quoted) + ")";
    }

    // ---------------------------------------------------------------- 执行

    static void runPowerShell(String script) throws IOException {
        runPowerShell(script, DEFAULT_TIMEOUT_MS);
    }

    static void runPowerShell(String script, long timeoutMs) throws IOException {
        runPowerShellForOutput(script, timeoutMs);
    }

    /**
     * 执行脚本并返回标准输出。
     *
     * <p>超时上限由调用方决定：写十几条注册表可以等久一点，读一个版本号必须快。
     */
    static String runPowerShellForOutput(String script, long timeoutMs) throws IOException {
        String encoded = Base64.getEncoder()
                .encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded)
                .redirectErrorStream(true)
                .start();
        String output = decode(process.getInputStream().readAllBytes());
        try {
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IOException("PowerShell 执行超时");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("PowerShell 执行被中断", e);
        }
        if (process.exitValue() != 0) {
            throw new IOException(firstLine(output == null ? "" : output));
        }
        return output == null ? "" : output;
    }

    private static void importReg(String content) throws IOException {
        Path file = Files.createTempFile("daliwan-menu-", ".reg");
        try {
            // reg import 对非 ASCII 内容要求 Unicode 文件：带 BOM 的 UTF-16LE
            byte[] body = content.getBytes(StandardCharsets.UTF_16LE);
            byte[] withBom = new byte[body.length + 2];
            withBom[0] = (byte) 0xFF;
            withBom[1] = (byte) 0xFE;
            System.arraycopy(body, 0, withBom, 2, body.length);
            Files.write(file, withBom);

            Process process = new ProcessBuilder("reg.exe", "import", file.toString())
                    .redirectErrorStream(true)
                    .start();
            String output = decode(process.getInputStream().readAllBytes());
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("写入右键菜单超时");
            }
            if (process.exitValue() != 0) {
                throw new IOException(firstLine(output));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("写入右键菜单被中断", e);
        } finally {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // 临时文件删不掉不影响结果
            }
        }
    }

    private static void notifyShell() {
        String script = notifySnippet();
        String encoded = Base64.getEncoder()
                .encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        try {
            Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded)
                    .redirectErrorStream(true)
                    .start();
            process.getInputStream().readAllBytes();
            process.waitFor(6, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // 刷新失败只是菜单显示慢一拍，不影响注册结果
        }
    }

    /** .reg 文件里的字符串转义：反斜杠翻倍、双引号加反斜杠。 */
    static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** 控制台输出解码：中文 Windows 是 GBK，个别环境是 UTF-8。 */
    private static String decode(byte[] bytes) {
        try {
            if (Charset.isSupported("GBK")) {
                return new String(bytes, Charset.forName("GBK"));
            }
        } catch (Exception ignored) {
            // 落到 UTF-8
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** 错误输出只留第一行，避免把整段堆栈塞进对话框。 */
    private static String firstLine(String text) {
        for (String line : text.split("\\R")) {
            if (!line.isBlank()) {
                return line.trim();
            }
        }
        return "未知错误";
    }

    /** 供界面展示的扩展名清单，如 {@code .doc .docx …}。 */
    public static String extensionSummary() {
        return extensionSummary(List.of(TO_PDF));
    }

    /** 供界面展示的扩展名清单。 */
    public static String extensionSummary(Collection<MenuVerb> verbs) {
        List<String> extensions = new ArrayList<>();
        for (MenuVerb verb : verbs) {
            for (String extension : verb.extensions()) {
                if (!extensions.contains(extension)) {
                    extensions.add(extension);
                }
            }
        }
        return String.join(" ", extensions.stream().limit(6).toList())
                + (extensions.size() > 6 ? " 等 " + extensions.size() + " 种" : "");
    }

    /** 仅用于单元测试：判断扩展名是否已被「转 PDF」接管。 */
    static boolean covers(String extension) {
        return EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT));
    }
}
