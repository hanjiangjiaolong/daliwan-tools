package com.daliwan.tool.util;

import com.daliwan.tool.core.ToolSettings;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 读取操作系统的明暗配色偏好。
 *
 * <p>Windows 上读注册表 {@code AppsUseLightTheme}（0 为深色、1 为浅色）；
 * macOS 上读 {@code AppleInterfaceStyle}。任一环节失败都退回浅色，
 * 界面最坏情况只是没跟随系统，不应该因为探测失败而让启动卡住或报错。
 *
 * <p>JavaFX 21 没有暴露系统配色（22 才有 {@code Platform.getPreferences()}），
 * 因此这里用一个带超时的外部命令读取，并限制为只在启动与窗口重新聚焦时调用。
 */
public final class SystemTheme {

    /** reg 输出里形如 {@code AppsUseLightTheme    REG_DWORD    0x0}。 */
    private static final Pattern REG_VALUE =
            Pattern.compile("AppsUseLightTheme\\s+REG_DWORD\\s+0x([0-9a-fA-F]+)");

    /** 探测超时：外部进程卡住时不能拖着界面。 */
    private static final long TIMEOUT_MS = 1500;

    /** 探测结果的落盘缓存：给不需要起进程的短命进程（如转 PDF 的结果卡片）读。 */
    private static final Path CACHE_FILE = Path.of(
            System.getProperty("user.home"), ".daliwan", "theme.cache");

    private SystemTheme() {
    }

    /**
     * 最近一次实际生效的配色。
     *
     * <p>主界面启动时会探测一次并写在这里，右菜单转 PDF 这类独立的短进程
     * 直接读文件即可，不用再起一次 {@code reg}。
     */
    public static String cached() {
        try {
            if (Files.isRegularFile(CACHE_FILE)) {
                String value = Files.readString(CACHE_FILE, StandardCharsets.UTF_8).trim();
                if (ToolSettings.THEME_DARK.equals(value) || ToolSettings.THEME_LIGHT.equals(value)) {
                    return value;
                }
            }
        } catch (IOException ignored) {
            // 读不到就按浅色处理
        }
        return ToolSettings.THEME_LIGHT;
    }

    /** 记录实际生效的配色；写盘失败不影响功能。 */
    public static void remember(String theme) {
        try {
            if (CACHE_FILE.getParent() != null) {
                Files.createDirectories(CACHE_FILE.getParent());
            }
            Files.writeString(CACHE_FILE, theme, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // 缓存写不进去只是下次颜色可能不准
        }
    }

    /** 当前系统配色对应的主题名：{@link ToolSettings#THEME_DARK} 或 {@link ToolSettings#THEME_LIGHT}。 */
    public static String detect() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("win")) {
                String theme = parseWindowsOutput(run("reg", "query",
                        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                        "/v", "AppsUseLightTheme"));
                return theme != null ? theme : ToolSettings.THEME_LIGHT;
            }
            if (os.contains("mac")) {
                String output = run("defaults", "read", "-g", "AppleInterfaceStyle");
                // 深色模式下输出 "Dark"；浅色模式下键不存在，进程退出码非 0
                return output.toLowerCase(Locale.ROOT).contains("dark")
                        ? ToolSettings.THEME_DARK : ToolSettings.THEME_LIGHT;
            }
        } catch (Exception ignored) {
            // 探测失败按浅色处理
        }
        return ToolSettings.THEME_LIGHT;
    }

    /**
     * 解析 reg 命令输出，识别不出时返回 {@code null}。
     *
     * <p>单独抽出来便于测试：中文 Windows 的输出里有本地化表头，
     * 但键名与 {@code REG_DWORD} 始终是 ASCII。
     */
    static String parseWindowsOutput(String output) {
        Matcher matcher = REG_VALUE.matcher(output == null ? "" : output);
        if (!matcher.find()) {
            return null;
        }
        return "0".equals(matcher.group(1)) ? ToolSettings.THEME_DARK : ToolSettings.THEME_LIGHT;
    }

    /** 执行外部命令并返回输出；超时或启动失败时返回空串。 */
    private static String run(String... command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output;
            try (InputStream in = process.getInputStream()) {
                // 只需匹配 ASCII 片段，用 Latin-1 避免中文系统下的解码异常
                output = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
            }
            if (!process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return "";
            }
            return output;
        } catch (Exception e) {
            return "";
        }
    }
}
