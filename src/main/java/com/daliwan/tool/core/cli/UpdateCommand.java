package com.daliwan.tool.core.cli;

import com.daliwan.tool.core.win.WindowsUpdateGuard;

import java.nio.file.Path;

/**
 * 命令行里的更新开关：{@code 大力丸.exe --update-block on}。
 *
 * <p>这不是给用户手敲用的，主要服务于界面：界面点「阻止自动更新」时，
 * 自己没有管理员权限，于是用 {@link WindowsUpdateGuard#relaunchElevated(Action)}
 * 带这个开关把程序以管理员身份再拉起来一次（UAC 弹窗），真正改系统的是那个新进程。
 * 老进程随后退出，界面重新查询一次状态即可，不需要跨进程传结果。
 *
 * <p>在命令行里 {@code --update-block status} 可以只看不改，不需要管理员。
 */
public final class UpdateCommand {

    private static final Path LOG_FILE = Path.of(
            System.getProperty("user.home"), ".daliwan", "update-guard.log");

    private UpdateCommand() {
    }

    /** 命令行里是否带了 {@code --update-block}。 */
    public static boolean matches(String[] args) {
        return valueOf(args) != null;
    }

    /**
     * 开关后面的取值；没有这个开关、或开关在末尾没有取值时返回 null。
     */
    public static String valueOf(String[] args) {
        if (args == null) {
            return null;
        }
        for (int i = 0; i < args.length; i++) {
            if (WindowsUpdateGuard.FLAG.equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                return args[i + 1];
            }
        }
        return null;
    }

    /** 执行一次；改系统需要管理员，没有权限时弹 UAC 后由新进程继续。 */
    public static void run(String[] args) {
        String value = valueOf(args);
        if (value == null) {
            return;
        }
        if ("status".equalsIgnoreCase(value.trim())) {
            WindowsUpdateGuard.State state = WindowsUpdateGuard.state();
            CommandLog.append(LOG_FILE, "[status] " + state.mode() + " " + state.summary());
            System.out.println(state.summary());
            return;
        }
        WindowsUpdateGuard.Action action = WindowsUpdateGuard.actionOf(value);
        if (action == null) {
            CommandLog.append(LOG_FILE, "[skip] 认不出的取值：" + value);
            return;
        }
        if (!WindowsUpdateGuard.isSupported()) {
            CommandLog.append(LOG_FILE, "[skip] 非 Windows，更新开关不可用");
            return;
        }
        if (!WindowsUpdateGuard.isElevated()) {
            boolean launched = WindowsUpdateGuard.relaunchElevated(action);
            CommandLog.append(LOG_FILE, launched
                    ? "[elevate] 已请求 UAC 提权（" + WindowsUpdateGuard.argumentOf(action) + "）"
                    : "[elevate] 提权失败，多半是 UAC 里点了「否」");
            return;
        }
        WindowsUpdateGuard.Outcome outcome = WindowsUpdateGuard.apply(action);
        CommandLog.append(LOG_FILE, (outcome.ok() ? "[ok] " : "[fail] ")
                + WindowsUpdateGuard.argumentOf(action) + " " + outcome.message());
        System.out.println(outcome.message());
    }
}
