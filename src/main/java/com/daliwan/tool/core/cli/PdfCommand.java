package com.daliwan.tool.core.cli;

import com.daliwan.tool.core.doc.ConversionEngine;
import com.daliwan.tool.core.doc.ConversionException;
import com.daliwan.tool.core.doc.EngineResolver;
import com.daliwan.tool.core.ipc.InstanceBridge;
import com.daliwan.tool.core.shell.ContextMenuIntegration;
import com.daliwan.tool.util.FileNames;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 右键菜单的入口：{@code 大力丸.exe --to-pdf "D:\a.pptx"}。
 *
 * <p>这条路径刻意不启动 JavaFX：一次转换不值得把整个主界面拉起来，
 * 尤其是多选文件时资源管理器会接连调好几次。
 *
 * <p>主窗口开着时请求会并到那个实例上，但只是省掉一次启动——那边同样在后台线程里转，
 * 不切页、不前置、不抢焦点，转完仍是用独立的结果卡片。用户点了右键就该像程序没开一样：
 * 文件转好了，别的事情一概不动。
 *
 * <p>日志追加在 {@code ~/.daliwan/convert.log}，出问题时不用靠猜。
 */
public final class PdfCommand {

    /** 命令行开关，与注册表里写的保持一致。 */
    public static final String FLAG = ContextMenuIntegration.FLAG;

    private static final Path LOG_FILE = Path.of(
            System.getProperty("user.home"), ".daliwan", "convert.log");
    private static final long LOG_LIMIT = 256 * 1024;

    private PdfCommand() {
    }

    /** 命令行里是否包含转 PDF 开关。 */
    public static boolean matches(String[] args) {
        if (args == null) {
            return false;
        }
        for (String argument : args) {
            if (FLAG.equals(argument)) {
                return true;
            }
        }
        return false;
    }

    /** 执行一次右键菜单转换，结束后不返回 JavaFX。 */
    public static void run(String[] args) {
        List<Path> files = collect(args);
        if (files.isEmpty()) {
            PdfResultDialog.show(List.of(), "没有收到要转换的文件");
            return;
        }
        // 主窗口开着时只借用它的进程，本体该干什么还干什么，界面不动
        if (InstanceBridge.forward(FLAG, files)) {
            log("[delegate] " + files.size() + " 个文件交给已运行的实例（后台转换）");
            return;
        }
        convert(files);
    }

    /**
     * 已运行的实例收到请求时调用：开一条后台线程转完再弹卡片，立刻返回。
     *
     * <p>必须在调用线程之外执行——监听线程要马上回去接下一个连接，
     * 否则多选文件时后面的请求会连不上。
     */
    public static void convertAsync(List<Path> files) {
        if (files == null || files.isEmpty()) {
            return;
        }
        Thread worker = new Thread(() -> convert(files), "pdf-convert");
        worker.setDaemon(true);
        worker.start();
    }

    /** 逐个文件转换并弹一张结果卡片；不碰任何窗口状态。 */
    private static void convert(List<Path> files) {
        List<Outcome> outcomes = new ArrayList<>(files.size());
        for (Path file : files) {
            long startedAt = System.currentTimeMillis();
            try {
                if (!Files.isRegularFile(file)) {
                    throw new ConversionException("文件不存在或已被移动");
                }
                ConversionEngine engine = EngineResolver.resolve(file);
                Path target = targetOf(file);
                engine.convert(file, target);
                outcomes.add(new Outcome(file, target, null));
                log("[ok] " + file + " -> " + target + " (" + engine.name() + ", "
                        + (System.currentTimeMillis() - startedAt) + " ms)");
            } catch (Exception e) {
                String message = e.getMessage() == null || e.getMessage().isBlank()
                        ? e.getClass().getSimpleName()
                        : e.getMessage();
                outcomes.add(new Outcome(file, null, message));
                log("[fail] " + file + " : " + message.replace('\n', ' '));
            }
        }
        PdfResultDialog.show(outcomes, null);
    }

    /** 从命令行里挑出文件参数：跳过开关本身和以 {@code --} 开头的其它参数。 */
    public static List<Path> collect(String[] args) {
        List<Path> files = new ArrayList<>();
        if (args == null) {
            return files;
        }
        for (String argument : args) {
            if (argument == null || argument.isBlank() || argument.startsWith("--")) {
                continue;
            }
            Path path = Path.of(argument);
            if (!files.contains(path)) {
                files.add(path);
            }
        }
        return files;
    }

    /** 输出到源文件同目录，同名不覆盖。 */
    private static Path targetOf(Path source) {
        Path absolute = source.toAbsolutePath();
        Path directory = absolute.getParent();
        String base = FileNames.baseName(absolute.getFileName().toString());
        return FileNames.uniqueTarget(directory, base + ".pdf");
    }

    /** 追加一行日志；超过上限先裁剪，避免无限制增长。 */
    private static synchronized void log(String line) {
        try {
            Path parent = LOG_FILE.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (Files.isRegularFile(LOG_FILE) && Files.size(LOG_FILE) > LOG_LIMIT) {
                Files.delete(LOG_FILE);
            }
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            Files.writeString(LOG_FILE, stamp + " " + line + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // 日志写不了不影响转换
        }
    }

    /**
     * 单个文件的转换结果。
     *
     * @param source 源文件
     * @param target 生成的 PDF，失败时为 null
     * @param error  失败原因，成功时为 null
     */
    public record Outcome(Path source, Path target, String error) {
        public boolean ok() {
            return error == null;
        }
    }
}
