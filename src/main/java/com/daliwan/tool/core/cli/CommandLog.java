package com.daliwan.tool.core.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 命令行入口共用的日志。
 *
 * <p>右键菜单、更新开关这些「跑完就退出」的入口没有界面，出问题时唯一线索就是日志，
 * 所以每个入口都往 {@code ~/.daliwan/} 下追加一行。三处写法原本是复制粘贴出来的，
 * 统一收到这里，顺带把「超上限先裁剪」的行为也固定下来。
 */
public final class CommandLog {

    /** 单个日志文件的上限；超了直接删掉重来，避免无限制增长。 */
    private static final long LIMIT = 256 * 1024;

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private CommandLog() {
    }

    /**
     * 追加一行；写不了就算了——日志不是主功能，绝不能因为它影响转换或开关操作。
     *
     * @param file 日志文件
     * @param line 内容，换行会被压成空格，保证一行一条
     */
    public static synchronized void append(Path file, String line) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (Files.isRegularFile(file) && Files.size(file) > LIMIT) {
                Files.delete(file);
            }
            String stamp = LocalDateTime.now().format(STAMP);
            String text = line == null ? "" : line.replace('\n', ' ').replace('\r', ' ');
            Files.writeString(file, stamp + " " + text + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // 日志写不了不影响主流程
        }
    }
}
