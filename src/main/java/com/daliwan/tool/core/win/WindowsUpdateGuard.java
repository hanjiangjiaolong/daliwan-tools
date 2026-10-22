package com.daliwan.tool.core.win;

import com.daliwan.tool.core.shell.AppLauncher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Windows 10 / 11 自动更新的总开关：一键阻止，也一键恢复。
 *
 * <p>目标很具体：让系统**不再自己下载和安装更新**，直到你主动恢复。
 * 微软把更新拆成了好几个互相掩护的部件，只关其中一个过几天就会被改回来，
 * 所以这里同时动三处，缺一不可：
 * <ol>
 *   <li><b>服务</b>：{@code wuauserv}（Windows Update）、{@code UsoSvc}（更新编排）
 *       设为禁用；{@code DoSvc}（传递优化）、{@code BITS} 降到手动——
 *       这两个还承担应用商店等正常下载，直接禁掉会连累别的功能，所以只降级不禁用；</li>
 *   <li><b>策略</b>：{@code HKLM\...\WindowsUpdate\AU!NoAutoUpdate = 1}，
 *       让"自动更新"这一项在设置里显示为已关闭；</li>
 *   <li><b>计划任务</b>：更新编排、升级助手，尤其是 {@code WaaSMedic\PerformRemediation}——
 *       它是微软留的"自我修复"任务，专门把被禁用的更新组件改回去，只关服务必被它翻盘。</li>
 * </ol>
 *
 * <p>写入 HKLM、改服务的启动类型都需要管理员权限。本类不偷偷提权：
 * 界面层先问 {@link #isElevated()}，没权限就走 {@link #relaunchElevated(Action)}
 * 弹一次 UAC，用户同意后才动系统。查询（{@link #state()}）不需要管理员。
 *
 * <p>和项目里其它 core 类一样的规矩：不引用 JavaFX。脚本生成与状态解析都是
 * 纯字符串处理，可以直接单测；真正执行的那一小段（起 PowerShell 进程）
 * 只在 Windows 上跑，测试里不碰。
 */
public final class WindowsUpdateGuard {

    /** 要做什么。 */
    public enum Action {
        /** 阻止自动更新。 */
        BLOCK,
        /** 恢复自动更新。 */
        ALLOW
    }

    /** 当前处于哪种状态。 */
    public enum Mode {
        /** 服务已禁用，且策略或计划任务至少一处生效。 */
        BLOCKED,
        /** 服务未禁用，也没有 NoAutoUpdate 策略。 */
        ALLOWED,
        /** 只关了一半，通常是因为被系统改回去了一部分。 */
        PARTIAL,
        /** 查不到（非 Windows、PowerShell 不可用、输出解析不出来）。 */
        UNKNOWN
    }

    /**
     * 一次查询结果。
     *
     * @param services      服务名 → 启动类型（{@code Disabled} / {@code Manual} / {@code Automatic}…，PowerShell 的英文枚举）
     * @param noAutoUpdate  策略 {@code NoAutoUpdate} 的值；键不存在时为 null
     * @param tasks         计划任务短名 → 状态（{@code Disabled} / {@code Ready} / {@code missing}）
     */
    public record State(Map<String, String> services, Boolean noAutoUpdate, Map<String, String> tasks) {

        /** 综合判断当前是阻止、放行还是只做了一半。 */
        public Mode mode() {
            if (services.isEmpty()) {
                return Mode.UNKNOWN;
            }
            String start = services.getOrDefault(WUAUSERV, "").toLowerCase(Locale.ROOT);
            boolean serviceOff = "disabled".equals(start);
            boolean policyOff = Boolean.TRUE.equals(noAutoUpdate);
            boolean tasksOff = !tasks.isEmpty() && tasks.values().stream()
                    .allMatch(value -> "disabled".equalsIgnoreCase(value) || "missing".equalsIgnoreCase(value));
            if (serviceOff && (policyOff || tasksOff)) {
                return Mode.BLOCKED;
            }
            return serviceOff || policyOff ? Mode.PARTIAL : Mode.ALLOWED;
        }

        /** 给界面用的一句话状态。 */
        public String summary() {
            String start = services.get(WUAUSERV);
            if (start == null) {
                return "没查到 Windows Update 服务的状态";
            }
            return switch (mode()) {
                case BLOCKED -> "已阻止自动更新（更新服务已禁用）";
                case ALLOWED -> "允许自动更新（更新服务启动类型为 " + start + "）";
                case PARTIAL -> "只关了一半（更新服务 " + start + "，建议再点一次）";
                case UNKNOWN -> "状态未知";
            };
        }
    }

    /** 一次操作的结果。 */
    public record Outcome(boolean ok, String message) {
    }

    /** 更新主服务；它的启动类型是判断整体状态的主要依据。 */
    public static final String WUAUSERV = "wuauserv";

    /** 需要调整为「禁用」的服务；顺序即脚本里的执行顺序。 */
    private static final List<String> DISABLED_SERVICES = List.of(WUAUSERV, "UsoSvc");

    /** 需要降级为「手动」的服务（还承担应用商店等正常下载，不能直接禁）。 */
    private static final Map<String, String> DEGRADED_BLOCK = Map.of("DoSvc", "demand", "BITS", "demand");

    /** 恢复时还原的启动类型：BITS / DoSvc 在 Windows 上默认是「自动（延迟启动）」。 */
    private static final Map<String, String> RESTORED_ALLOW =
            Map.of("DoSvc", "delayed-auto", "BITS", "delayed-auto");

    /** 更新相关的计划任务；WaaSMedic 是微软的自我修复任务，漏掉它过几天就会被改回去。 */
    private static final List<String> TASKS = List.of(
            "\\Microsoft\\Windows\\UpdateOrchestrator\\Schedule Scan",
            "\\Microsoft\\Windows\\WindowsUpdate\\Automatic App Update",
            "\\Microsoft\\Windows\\UpdateAssistant\\UpdateAssistant",
            "\\Microsoft\\Windows\\UpdateAssistant\\UpdateAssistantAllUsersRun",
            "\\Microsoft\\Windows\\WaaSMedic\\PerformRemediation");

    /** 组策略写入位置。 */
    private static final String POLICY_KEY =
            "HKLM:\\SOFTWARE\\Policies\\Microsoft\\Windows\\WindowsUpdate\\AU";

    /** 命令行开关，自提权时要把同样的意图传给新的进程。 */
    public static final String FLAG = "--update-block";

    private static final long TIMEOUT_SECONDS = 120;

    private WindowsUpdateGuard() {
    }

    /** 只在 Windows 上可用；其它系统一律返回 false，界面据此禁用按钮。 */
    public static boolean isSupported() {
        return AppLauncher.isWindows();
    }

    /**
     * 当前进程是不是管理员。
     *
     * <p>用 {@code net session} 判断：管理员返回 0，普通用户返回 2。
     * 这是不依赖任何第三方库的经典做法。
     */
    public static boolean isElevated() {
        if (!isSupported()) {
            return false;
        }
        try {
            Process process = new ProcessBuilder("cmd.exe", "/c", "net session >nul 2>&1")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    /**
     * 生成阻止 / 恢复的 PowerShell 脚本。
     *
     * <p>脚本本身不提权，由调用方保证进程已是管理员。故意只输出 ASCII 标记行
     * （{@code SERVICE|...}、{@code POLICY|...}、{@code TASK|...}），
     * 这样 Java 侧解析不受系统语言影响。
     */
    public static String script(Action action) {
        boolean block = action == Action.BLOCK;
        StringBuilder script = new StringBuilder();
        script.append("$ErrorActionPreference = 'SilentlyContinue'\r\n");
        script.append("[Console]::OutputEncoding = [System.Text.Encoding]::UTF8\r\n");

        for (String service : DISABLED_SERVICES) {
            if (block) {
                script.append("Stop-Service -Name '").append(service).append("' -Force\r\n");
            }
            appendServiceConfig(script, service, block ? "disabled" : "demand");
        }
        Map<String, String> degraded = block ? DEGRADED_BLOCK : RESTORED_ALLOW;
        for (Map.Entry<String, String> entry : degraded.entrySet()) {
            appendServiceConfig(script, entry.getKey(), entry.getValue());
        }

        script.append("$policy = ").append(psString(POLICY_KEY)).append("\r\n");
        if (block) {
            script.append("if (-not (Test-Path $policy)) { New-Item -Path $policy -Force | Out-Null }\r\n");
            script.append("New-ItemProperty -Path $policy -Name 'NoAutoUpdate' -Value 1 ")
                    .append("-PropertyType DWord -Force | Out-Null\r\n");
            script.append("New-ItemProperty -Path $policy -Name 'AUOptions' -Value 2 ")
                    .append("-PropertyType DWord -Force | Out-Null\r\n");
        } else {
            script.append("Remove-ItemProperty -Path $policy -Name 'NoAutoUpdate' ").append("-Force\r\n");
            script.append("Remove-ItemProperty -Path $policy -Name 'AUOptions' ").append("-Force\r\n");
        }
        script.append("$value = Get-ItemProperty -Path $policy -Name 'NoAutoUpdate'\r\n");
        script.append("if ($null -eq $value) { Write-Output 'POLICY|NoAutoUpdate|absent' } ")
                .append("else { Write-Output \"POLICY|NoAutoUpdate|$($value.NoAutoUpdate)\" }\r\n");

        appendTasks(script, block);
        script.append("Write-Output 'DONE|").append(block ? "block" : "allow").append("'\r\n");
        return script.toString();
    }

    /** 生成只读的查询脚本；不需要管理员。 */
    public static String queryScript() {
        StringBuilder script = new StringBuilder();
        script.append("$ErrorActionPreference = 'SilentlyContinue'\r\n");
        script.append("[Console]::OutputEncoding = [System.Text.Encoding]::UTF8\r\n");
        List<String> names = new ArrayList<>();
        names.addAll(DISABLED_SERVICES);
        names.addAll(DEGRADED_BLOCK.keySet());
        for (String service : names) {
            script.append("$service = Get-Service -Name '").append(service).append("'\r\n");
            script.append("if ($null -eq $service) { Write-Output 'SERVICE|").append(service)
                    .append("|missing' } else { Write-Output \"SERVICE|").append(service)
                    .append("|$($service.StartType)\" }\r\n");
        }
        script.append("$policy = ").append(psString(POLICY_KEY)).append("\r\n");
        script.append("$value = Get-ItemProperty -Path $policy -Name 'NoAutoUpdate'\r\n");
        script.append("if ($null -eq $value) { Write-Output 'POLICY|NoAutoUpdate|absent' } ")
                .append("else { Write-Output \"POLICY|NoAutoUpdate|$($value.NoAutoUpdate)\" }\r\n");
        appendTasks(script, null);
        script.append("Write-Output 'END|query'\r\n");
        return script.toString();
    }

    /**
     * 解析脚本输出的标记行。
     *
     * <p>只认 {@code 类型|名称|值} 三段式，其它行（警告、空行）一律跳过，
     * 所以中文 Windows 上那些本地化的报错不会污染解析结果。
     */
    public static State parseState(String output) {
        Map<String, String> services = new LinkedHashMap<>();
        Map<String, String> tasks = new LinkedHashMap<>();
        Boolean noAutoUpdate = null;
        if (output != null) {
            for (String line : output.split("\\R")) {
                String[] parts = line.trim().split("\\|", 3);
                if (parts.length < 3) {
                    continue;
                }
                switch (parts[0].toUpperCase(Locale.ROOT)) {
                    case "SERVICE" -> services.put(parts[1], parts[2]);
                    case "TASK" -> tasks.put(parts[1], parts[2]);
                    case "POLICY" -> {
                        if ("NoAutoUpdate".equalsIgnoreCase(parts[1])) {
                            String raw = parts[2].trim();
                            // absent / missing 表示这个键压根不存在，保持 null：
                            // 「没设置过」和「设成了 0」是两回事，不能都记成 false
                            if (!"absent".equalsIgnoreCase(raw) && !"missing".equalsIgnoreCase(raw)) {
                                noAutoUpdate = "1".equals(raw) || "true".equalsIgnoreCase(raw);
                            }
                        }
                    }
                    default -> {
                        // 其它标记（DONE / END）对状态没有意义
                    }
                }
            }
        }
        return new State(Map.copyOf(services), noAutoUpdate, Map.copyOf(tasks));
    }

    /**
     * 查询当前状态；非 Windows 或查询失败时返回 {@link Mode#UNKNOWN} 的结果。
     *
     * <p>只读，不需要管理员权限。
     */
    public static State state() {
        if (!isSupported()) {
            return new State(Map.of(), null, Map.of());
        }
        return parseState(runPowerShell(queryScript()));
    }

    /**
     * 执行阻止或恢复；调用前请确认 {@link #isElevated()} 为真，否则一律失败。
     */
    public static Outcome apply(Action action) {
        if (!isSupported()) {
            return new Outcome(false, "这个功能只在 Windows 上能用");
        }
        String output = runPowerShell(script(action));
        if (output.isBlank()) {
            return new Outcome(false, "没有拿到执行结果，可能 UAC 被取消或 PowerShell 被策略拦下");
        }
        if (output.contains("DONE|")) {
            return new Outcome(true, action == Action.BLOCK
                    ? "已阻止自动更新：服务已禁用、策略已写入、更新计划任务已停用"
                    : "已恢复自动更新：服务还原为手动、策略已清除、计划任务已启用");
        }
        return new Outcome(false, "执行未完成：" + firstMeaningfulLine(output));
    }

    /**
     * 以管理员身份重新启动本程序，并把这次的意图（{@link #FLAG} + 参数）带过去。
     *
     * <p>靠 {@code Start-Process -Verb RunAs} 触发 UAC；用户在 UAC 里点「否」时返回 false。
     * 新的进程会重新走一遍 {@link com.daliwan.tool.core.cli.UpdateCommand}，
     * 那时它已经是管理员，直接执行即可。
     */
    public static boolean relaunchElevated(Action action) {
        if (!isSupported()) {
            return false;
        }
        String executable = currentExecutable();
        String prefix = currentPrefix();
        if (executable.isEmpty()) {
            return false;
        }
        String arguments = (prefix.isEmpty() ? "" : prefix + " ") + FLAG + " " + argumentOf(action);
        // -Wait：一直等到提权出来的那个进程干完活为止，界面才能立刻读到新状态
        String command = "Start-Process -FilePath " + psString(executable)
                + " -ArgumentList " + psString(arguments) + " -Verb RunAs -Wait";
        try {
            Process process = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-NonInteractive", "-Command", command)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    /** 命令行参数写法：{@code on} / {@code off}。 */
    public static String argumentOf(Action action) {
        return action == Action.BLOCK ? "on" : "off";
    }

    /** 反查命令行参数；认不出来返回 null。 */
    public static Action actionOf(String argument) {
        if (argument == null) {
            return null;
        }
        return switch (argument.trim().toLowerCase(Locale.ROOT)) {
            case "on", "block", "disable" -> Action.BLOCK;
            case "off", "allow", "enable" -> Action.ALLOW;
            default -> null;
        };
    }

    /**
     * 当前程序的可执行文件：优先用 jpackage 注入的路径，其次 {@code javaw.exe}。
     *
     * <p>开发期（IDE / {@code java -jar}）也要能用，否则按钮点了没反应。
     */
    private static String currentExecutable() {
        Optional<AppLauncher.Spec> spec = AppLauncher.resolve();
        if (spec.isPresent()) {
            return spec.get().executable();
        }
        String javaHome = System.getProperty("java.home", "");
        if (javaHome.isEmpty()) {
            return "";
        }
        return javaHome + "\\bin\\javaw.exe";
    }

    /** 可执行文件之后、业务参数之前的固定参数（{@code -jar xxx.jar} 或 {@code -cp ...}）。 */
    private static String currentPrefix() {
        Optional<AppLauncher.Spec> spec = AppLauncher.resolve();
        if (spec.isPresent()) {
            return String.join(" ", spec.get().prefix());
        }
        String classpath = System.getProperty("java.class.path", "");
        if (classpath.isEmpty()) {
            return "";
        }
        return "-cp \"" + classpath + "\" com.daliwan.tool.Launcher";
    }

    private static void appendServiceConfig(StringBuilder script, String service, String startType) {
        script.append("sc.exe config ").append(service).append(" start= ").append(startType).append("\r\n");
        script.append("Write-Output \"SERVICE|").append(service).append("|")
                .append(startType).append("\"\r\n");
        script.append("$state = (Get-Service -Name '").append(service).append("').StartType\r\n");
        script.append("Write-Output \"SERVICE|").append(service).append("|$state\"\r\n");
    }

    /**
     * 追加计划任务的启用 / 停用段落。
     *
     * @param block null 表示只查询状态，不改任何东西
     */
    private static void appendTasks(StringBuilder script, Boolean block) {
        script.append("$tasks = @(");
        for (int i = 0; i < TASKS.size(); i++) {
            if (i > 0) {
                script.append(", ");
            }
            script.append(psString(TASKS.get(i)));
        }
        script.append(")\r\n");
        script.append("foreach ($full in $tasks) {\r\n");
        script.append("  $cut = $full.LastIndexOf('\\')\r\n");
        script.append("  $path = $full.Substring(0, $cut + 1)\r\n");
        script.append("  $name = $full.Substring($cut + 1)\r\n");
        script.append("  $task = Get-ScheduledTask -TaskPath $path -TaskName $name\r\n");
        script.append("  if ($null -eq $task) { Write-Output \"TASK|$name|missing\"; continue }\r\n");
        if (block == null) {
            script.append("  Write-Output \"TASK|$name|$($task.State)\"\r\n");
        } else if (block) {
            script.append("  Disable-ScheduledTask -InputObject $task | Out-Null\r\n");
            script.append("  Write-Output \"TASK|$name|Disabled\"\r\n");
        } else {
            script.append("  Enable-ScheduledTask -InputObject $task | Out-Null\r\n");
            script.append("  Write-Output \"TASK|$name|Ready\"\r\n");
        }
        script.append("}\r\n");
    }

    /**
     * 跑一段 PowerShell 并返回标准输出。
     *
     * <p>脚本用 {@code -EncodedCommand} 传（UTF-16LE + Base64），
     * 这样中文、单引号、换行都不用在命令行里层层转义。
     */
    public static String runPowerShell(String script) {
        try {
            String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
            Process process = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded)
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "";
            }
            try (var input = process.getInputStream()) {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return "";
        }
    }

    /** PowerShell 单引号字符串：内部的单引号写两遍。 */
    private static String psString(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    /** 取输出里第一行像话的内容，别把整屏错误直接甩给用户。 */
    private static String firstMeaningfulLine(String output) {
        for (String line : output.split("\\R")) {
            String text = line.trim();
            if (!text.isEmpty()) {
                return text.length() > 120 ? text.substring(0, 120) + "…" : text;
            }
        }
        return "（无输出）";
    }
}
