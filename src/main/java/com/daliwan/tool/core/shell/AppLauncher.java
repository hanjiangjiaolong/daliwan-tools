package com.daliwan.tool.core.shell;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 解析「当前这份程序」的启动命令，供资源管理器右键菜单调用。
 *
 * <p>两种运行形态要分开处理：
 * <ul>
 *   <li>jpackage 打出来的绿色版：直接就是 {@code 大力丸.exe}，右键菜单指向它；</li>
 *   <li>{@code java -jar} / IDE 里跑：可执行文件是 {@code java.exe}，得把
 *       {@code -jar xxx.jar} 一起写进命令行，否则外壳没有类路径可用。</li>
 * </ul>
 *
 * <p>{@code jpackage.app-path} 由 jpackage 生成的启动器注入，比从进程命令行反推更可靠；
 * 拿不到时再退回 {@link ProcessHandle} 的命令行。
 */
public final class AppLauncher {

    /**
     * 一条启动命令。
     *
     * @param executable 可执行文件（exe）
     * @param prefix     可执行文件之后、业务参数之前的固定参数，如 {@code -jar xxx.jar}
     * @param ownIcon    可执行文件是否带本程序的图标，决定右键菜单是否挂图标
     */
    public record Spec(String executable, List<String> prefix, boolean ownIcon) {

        /** 拼成可直接写进注册表的命令行前缀（含空格的参数才加引号）。 */
        public String quotedPrefix() {
            StringBuilder text = new StringBuilder(quote(executable));
            for (String argument : prefix) {
                text.append(' ').append(quoteIfNeeded(argument));
            }
            return text.toString();
        }
    }

    private static volatile boolean resolved;
    private static volatile Spec cached;

    private AppLauncher() {
    }

    /** 当前运行的启动命令；无法确定（例如运行在 target/classes 又没打 jar）时为空。 */
    public static Optional<Spec> resolve() {
        if (!resolved) {
            synchronized (AppLauncher.class) {
                if (!resolved) {
                    cached = detect();
                    resolved = true;
                }
            }
        }
        return Optional.ofNullable(cached);
    }

    private static Spec detect() {
        if (!isWindows()) {
            return null;
        }
        String packaged = System.getProperty("jpackage.app-path", "").trim();
        if (!packaged.isEmpty()) {
            Path exe = Path.of(packaged);
            if (Files.isRegularFile(exe)) {
                return new Spec(exe.toAbsolutePath().toString(), List.of(), true);
            }
        }

        Optional<String> command = ProcessHandle.current().info().command();
        if (command.isEmpty()) {
            return null;
        }
        Path executable = Path.of(command.get());
        String name = fileName(executable);
        if (name.equals("java.exe") || name.equals("javaw.exe") || name.equals("java")) {
            Path jar = runningJar();
            if (jar == null) {
                return null;
            }
            Path javaw = executable.resolveSibling("javaw.exe");
            if (!Files.isRegularFile(javaw)) {
                javaw = executable;
            }
            List<String> prefix = new ArrayList<>();
            prefix.add("-jar");
            prefix.add(jar.toString());
            return new Spec(javaw.toAbsolutePath().toString(), List.copyOf(prefix), false);
        }
        if (name.endsWith(".exe")) {
            return new Spec(executable.toAbsolutePath().toString(), List.of(), true);
        }
        return null;
    }

    /**
     * 当前代码所在的 jar。
     *
     * <p>IDE 里直接跑时类路径是 {@code target/classes}，此时去同级目录找
     * shade 产出的 {@code daliwan_tool.jar}——开发期也能验证右键菜单，不用先打整包。
     */
    private static Path runningJar() {
        try {
            var source = AppLauncher.class.getProtectionDomain().getCodeSource();
            if (source == null) {
                return null;
            }
            Path location = Path.of(source.getLocation().toURI());
            if (Files.isRegularFile(location)
                    && fileName(location).endsWith(".jar")) {
                return location.toAbsolutePath();
            }
            Path directory = Files.isDirectory(location) ? location : location.getParent();
            if (directory == null) {
                return null;
            }
            for (Path candidate : List.of(
                    directory.resolve("daliwan_tool.jar"),
                    directory.getParent() == null ? directory.resolve("daliwan_tool.jar")
                            : directory.getParent().resolve("daliwan_tool.jar"))) {
                if (Files.isRegularFile(candidate)) {
                    return candidate.toAbsolutePath();
                }
            }
        } catch (Exception ignored) {
            // 拿不到就算了，右键菜单之外的功能不受影响
        }
        return null;
    }

    /** Windows 命令行引用：整体加双引号，内部的双引号做转义。 */
    public static String quote(String value) {
        return '"' + value.replace("\"", "\\\"") + '"';
    }

    /** 只有含空白或引号的参数才需要加引号，避免命令行里出现多余的 "-jar"。 */
    public static String quoteIfNeeded(String value) {
        return value.isEmpty() || value.chars().anyMatch(ch -> ch == ' ' || ch == '\t' || ch == '"')
                ? quote(value)
                : value;
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String fileName(Path path) {
        return path.getFileName() == null
                ? ""
                : path.getFileName().toString().toLowerCase(Locale.ROOT);
    }
}
