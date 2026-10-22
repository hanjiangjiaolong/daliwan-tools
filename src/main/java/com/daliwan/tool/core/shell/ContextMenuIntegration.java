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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 把大力丸的转换动作写进资源管理器右键菜单。
 *
 * <p>注册位置是 {@code HKEY_CURRENT_USER\Software\Classes\SystemFileAssociations\<扩展名>\shell}，
 * 只挂在「按扩展名关联」这一层，不动各文件类型的默认打开方式，也不需要管理员权限；
 * 关掉开关时按同一个键整棵删除，不留残余。
 *
 * <p>菜单是**两级**的：不管什么文件类型，右键里只出现一个「{@value #MENU_TITLE}」入口，
 * 展开后才是具体动作——PDF 上四项（转 Word / 转 Excel / 转 PPT / 提取文本），
 * Office 文件上一项（转 PDF）。一个类型能转的格式越多，越不该把它们平铺在右键上。
 *
 * <p>级联菜单在 Windows 上有两种写法，各有一半机器只认其中一种，所以两种都写：
 * <ol>
 *   <li>{@code SubCommands} 置空 + 父键自带 {@code Shell} 子键：全部落在 HKCU 内，
 *       不需要管理员，也是这里的主写法；</li>
 *   <li>{@code ExtendedSubCommandsKey} 指向 {@code Software\Classes\DaliwanMenu\<扩展名>}
 *       这个集中键：老式写法，同样只要 HKCU。</li>
 * </ol>
 * 两套指向同一批命令，资源管理器认哪套都能展开出一样的子菜单。
 *
 * <p>写注册表优先走 PowerShell：一次进程调用就能写完十几种扩展名，中文菜单名不受
 * 控制台代码页影响，还能顺手调用 {@code SHChangeNotify} 让资源管理器立刻刷新缓存。
 * PowerShell 被策略挡住时退回 {@code reg import}（先写一份临时 .reg）。
 */
public final class ContextMenuIntegration {

    /**
     * 一个右键菜单项。
     *
     * @param key        注册表子键名（二级菜单里的键名），改它等于换一棵键
     * @param menuText   二级菜单上显示的文字，如「转 Word」
     * @param flag       交给程序的命令行开关，与命令行解析保持一致
     * @param extensions 挂这个菜单的文件扩展名
     */
    public record MenuVerb(String key, String menuText, String flag, List<String> extensions) {
    }

    /** Office → PDF：挂在 Word / Excel / PPT 等文件上。 */
    public static final MenuVerb TO_PDF = new MenuVerb("DaliwanToPdf", "转 PDF",
            "--to-pdf", List.copyOf(StirlingEngine.extensions()));

    /** PDF → Office：都挂在 .pdf 上，一个目标一项，用户要什么点什么。 */
    public static final List<MenuVerb> PDF_TO_OFFICE = List.of(
            new MenuVerb("DaliwanPdfToWord", "转 Word",
                    PdfToOfficeConverter.Target.WORD.flag(), List.of(".pdf")),
            new MenuVerb("DaliwanPdfToExcel", "转 Excel",
                    PdfToOfficeConverter.Target.EXCEL.flag(), List.of(".pdf")),
            new MenuVerb("DaliwanPdfToSlides", "转 PPT",
                    PdfToOfficeConverter.Target.SLIDES.flag(), List.of(".pdf")),
            new MenuVerb("DaliwanPdfToText", "提取文本",
                    PdfToOfficeConverter.Target.TEXT.flag(), List.of(".pdf")));

    /** 全部菜单项：一个开关全开时用得到。 */
    public static final List<MenuVerb> ALL;

    static {
        List<MenuVerb> all = new ArrayList<>();
        all.add(TO_PDF);
        all.addAll(PDF_TO_OFFICE);
        ALL = List.copyOf(all);
    }

    /** 一级菜单上显示的文字：所有文件类型共用这一个入口。 */
    public static final String MENU_TITLE = "大力丸";

    /** 一级菜单的注册表子键名。 */
    public static final String MENU_KEY = "Daliwan";

    /**
     * 二级命令的集中存放处（{@code ExtendedSubCommandsKey} 指向它），按扩展名分目录，
     * 这样 .pdf 的子菜单里不会出现「转 PDF」这种没意义的项。
     */
    public static final String CASCADE_ROOT = "DaliwanMenu";

    /** 一级菜单项标识，保留给文档与排查引用。 */
    public static final String VERB = MENU_KEY;

    /** 一级菜单上显示的文字。 */
    public static final String MENU_TEXT = MENU_TITLE;

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

    /** 一级菜单的注册表键路径，便于排查与文档说明。 */
    public static String keyPath(String extension) {
        String normalized = normalize(extension);
        return "HKEY_CURRENT_USER\\Software\\Classes\\SystemFileAssociations\\"
                + normalized + "\\shell\\" + MENU_KEY;
    }

    /** 二级菜单项的注册表键路径（父键下的 Shell 子键）。 */
    public static String keyPath(MenuVerb verb, String extension) {
        return keyPath(extension) + "\\Shell\\" + verb.key();
    }

    /** 集中键里同一项的路径（{@code ExtendedSubCommandsKey} 用）。 */
    public static String storeKeyPath(MenuVerb verb, String extension) {
        return "HKEY_CURRENT_USER\\Software\\Classes\\" + CASCADE_ROOT + "\\"
                + normalize(extension) + "\\shell\\" + verb.key();
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

    /** 写入给定菜单项，一级菜单收纳成「大力丸」，具体动作放进二级。 */
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

    /** 删除给定菜单项，连一级入口、集中键一起清掉。 */
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
        script.append(legacyCleanup());
        for (Map.Entry<String, List<MenuVerb>> entry : groupByExtension(verbs).entrySet()) {
            String extension = entry.getKey();
            script.append("$ext = ").append(psString(extension)).append("\r\n");
            String base = "\"HKCU:\\Software\\Classes\\SystemFileAssociations\\$ext\\shell\\"
                    + MENU_KEY + "\"";
            script.append("$base = ").append(base).append("\r\n");
            // 注意：New-Item -Force 在注册表里是「重建」，会把已建好的子菜单冲掉，
            // 所以这里只在键不存在时创建——重复安装时才不会丢掉上一次的动作
            script.append("if (-not (Test-Path $base)) { New-Item -Path $base -Force | Out-Null }\r\n");
            appendValue(script, "$base", "(default)", psString(MENU_TITLE));
            appendValue(script, "$base", "MUIVerb", psString(MENU_TITLE));
            // 空串是关键：告诉资源管理器「这是个子菜单，命令在下面自己找」
            appendValue(script, "$base", "SubCommands", "''");
            appendValue(script, "$base", "ExtendedSubCommandsKey",
                    psString("Software\\Classes\\" + CASCADE_ROOT + "\\" + extension));
            appendValue(script, "$base", "MultiSelectModel", "'Document'");
            if (withIcon) {
                appendValue(script, "$base", "Icon", psString(icon));
            }
            for (MenuVerb verb : entry.getValue()) {
                String sub = "\"$base\\Shell\\" + verb.key() + "\"";
                String store = "\"HKCU:\\Software\\Classes\\" + CASCADE_ROOT
                        + "\\$ext\\shell\\" + verb.key() + "\"";
                // 两套写法各写一份：父键下的 Shell 子键 + ExtendedSubCommandsKey 指向的集中键
                for (String target : List.of(sub, store)) {
                    script.append("$item = ").append(target).append("\r\n");
                    script.append("if (-not (Test-Path $item)) { New-Item -Path $item -Force | Out-Null }\r\n");
                    appendValue(script, "$item", "(default)", psString(verb.menuText()));
                    appendValue(script, "$item", "MultiSelectModel", "'Document'");
                    if (withIcon) {
                        appendValue(script, "$item", "Icon", psString(icon));
                    }
                    script.append("$run = \"$item\\command\"\r\n");
                    script.append("if (-not (Test-Path $run)) { New-Item -Path $run -Force | Out-Null }\r\n");
                    appendValue(script, "$run", "(default)",
                            psString(commandValue(verb, quotedPrefix)));
                }
            }
        }
        script.append(notifySnippet());
        return script.toString();
    }

    /** PowerShell 卸载脚本：一级入口、集中键、以及旧版平铺的键一起删。 */
    static String uninstallScript(Collection<MenuVerb> verbs) {
        StringBuilder script = new StringBuilder();
        script.append("$ErrorActionPreference = 'Stop'\r\n");
        script.append(legacyCleanup());
        for (String extension : groupByExtension(verbs).keySet()) {
            script.append("$ext = ").append(psString(extension)).append("\r\n");
            script.append("$base = \"HKCU:\\Software\\Classes\\SystemFileAssociations\\$ext\\shell\\")
                    .append(MENU_KEY).append("\"\r\n");
            script.append("if (Test-Path $base) { Remove-Item -Path $base -Recurse -Force }\r\n");
            script.append("$store = \"HKCU:\\Software\\Classes\\").append(CASCADE_ROOT)
                    .append("\\$ext\"\r\n");
            script.append("if (Test-Path $store) { Remove-Item -Path $store -Recurse -Force }\r\n");
        }
        // 集中键空了就把外壳也删掉，不留孤儿
        script.append("$root = \"HKCU:\\Software\\Classes\\").append(CASCADE_ROOT).append("\"\r\n");
        script.append("if ((Test-Path $root) -and ((Get-ChildItem $root -ErrorAction SilentlyContinue"
                + " | Measure-Object).Count -eq 0)) { Remove-Item -Path $root -Force }\r\n");
        script.append(notifySnippet());
        return script.toString();
    }

    /**
     * 清理旧版平铺在右键上的键。
     *
     * <p>老版本把每个动作直接挂在 {@code shell\DaliwanToPdf} 这一层，升级后如果不清，
     * 右键里就会同时出现「大力丸 · 转 PDF」和新的「大力丸」子菜单。
     */
    private static String legacyCleanup() {
        StringBuilder script = new StringBuilder();
        for (MenuVerb verb : ALL) {
            script.append("$legacy = @(").append(psArrayBody(verb.extensions())).append(")\r\n");
            script.append("foreach ($ext in $legacy) {\r\n");
            script.append("  $old = \"HKCU:\\Software\\Classes\\SystemFileAssociations\\$ext\\shell\\")
                    .append(verb.key()).append("\"\r\n");
            script.append("  if (Test-Path $old) { Remove-Item -Path $old -Recurse -Force }\r\n");
            script.append("}\r\n");
        }
        return script.toString();
    }

    private static void appendValue(StringBuilder script, String key, String name, String value) {
        script.append("New-ItemProperty -Path ").append(key).append(" -Name ").append(psString(name))
                .append(" -Value ").append(value)
                .append(" -PropertyType String -Force | Out-Null\r\n");
    }

    /** 通知外壳刷新关联缓存，否则右键菜单可能要等重启才出现。 */
    private static String notifySnippet() {
        return "$sig = '[DllImport(\"shell32.dll\")] public static extern void SHChangeNotify(int e, int f, IntPtr a, IntPtr b);'\r\n"
                + "Add-Type -MemberDefinition $sig -Name Shell32 -Namespace Win32 -ErrorAction SilentlyContinue\r\n"
                + "[Win32.Shell32]::SHChangeNotify(" + SHCNE_ASSOCCHANGED + ", 0, [IntPtr]::Zero, [IntPtr]::Zero)\r\n";
    }

    /** .reg 安装内容（PowerShell 不可用时的兜底），结构与 PowerShell 版一致。 */
    static String regInstall(Collection<MenuVerb> verbs, String quotedPrefix,
                             String icon, boolean withIcon) {
        StringBuilder reg = new StringBuilder(REG_HEADER).append("\r\n\r\n");
        for (Map.Entry<String, List<MenuVerb>> entry : groupByExtension(verbs).entrySet()) {
            String extension = entry.getKey();
            String parent = keyPath(extension);
            reg.append('[').append(parent).append("]\r\n");
            reg.append("@=\"").append(escape(MENU_TITLE)).append("\"\r\n");
            reg.append("\"MUIVerb\"=\"").append(escape(MENU_TITLE)).append("\"\r\n");
            reg.append("\"SubCommands\"=\"\"\r\n");
            reg.append("\"ExtendedSubCommandsKey\"=\"")
                    .append(escape("Software\\Classes\\" + CASCADE_ROOT + "\\" + extension))
                    .append("\"\r\n");
            reg.append("\"MultiSelectModel\"=\"Document\"\r\n");
            if (withIcon) {
                reg.append("\"Icon\"=\"").append(escape(icon)).append("\"\r\n");
            }
            reg.append("\r\n");
            for (MenuVerb verb : entry.getValue()) {
                for (String key : List.of(keyPath(verb, extension), storeKeyPath(verb, extension))) {
                    reg.append('[').append(key).append("]\r\n");
                    reg.append("@=\"").append(escape(verb.menuText())).append("\"\r\n");
                    reg.append("\"MultiSelectModel\"=\"Document\"\r\n");
                    if (withIcon) {
                        reg.append("\"Icon\"=\"").append(escape(icon)).append("\"\r\n");
                    }
                    reg.append('[').append(key).append("\\command]\r\n");
                    reg.append("@=\"").append(escape(commandValue(verb, quotedPrefix)))
                            .append("\"\r\n\r\n");
                }
            }
        }
        return reg.toString();
    }

    /** .reg 卸载内容：减号前缀表示删除整棵键。 */
    static String regUninstall(Collection<MenuVerb> verbs) {
        StringBuilder reg = new StringBuilder(REG_HEADER).append("\r\n\r\n");
        for (String extension : groupByExtension(verbs).keySet()) {
            reg.append("[-").append(keyPath(extension)).append("]\r\n");
            reg.append("[-HKEY_CURRENT_USER\\Software\\Classes\\").append(CASCADE_ROOT)
                    .append("\\").append(extension).append("]\r\n");
        }
        return reg.toString();
    }

    /** 按扩展名聚合：同一个扩展名上的若干动作，收进同一个一级菜单。 */
    static Map<String, List<MenuVerb>> groupByExtension(Collection<MenuVerb> verbs) {
        Map<String, List<MenuVerb>> grouped = new LinkedHashMap<>();
        for (MenuVerb verb : verbs) {
            for (String extension : verb.extensions()) {
                grouped.computeIfAbsent(normalize(extension), key -> new ArrayList<>()).add(verb);
            }
        }
        return grouped;
    }

    private static String normalize(String extension) {
        return extension.startsWith(".") ? extension : "." + extension;
    }

    private static String psString(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static String psArray(List<String> values) {
        return "@(" + psArrayBody(values) + ")";
    }

    private static String psArrayBody(List<String> values) {
        List<String> quoted = new ArrayList<>(values.size());
        for (String value : values) {
            quoted.add(psString(value));
        }
        return String.join(",", quoted);
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

    /** 二级菜单长什么样，给界面与文档展示用，如 {@code 转 Word / 转 Excel}。 */
    public static String submenuSummary(Collection<MenuVerb> verbs) {
        return String.join(" / ", verbs.stream().map(MenuVerb::menuText).toList());
    }

    /** 仅用于单元测试：判断扩展名是否已被「转 PDF」接管。 */
    static boolean covers(String extension) {
        return EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT));
    }
}
