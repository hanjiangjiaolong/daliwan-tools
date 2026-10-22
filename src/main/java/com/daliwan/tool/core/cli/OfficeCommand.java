package com.daliwan.tool.core.cli;

import com.daliwan.tool.core.doc.ConversionException;
import com.daliwan.tool.core.doc.PdfToOfficeConverter;
import com.daliwan.tool.core.ipc.InstanceBridge;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 右键菜单的另一半：{@code 大力丸.exe --to-docx "D:\a.pdf"}。
 *
 * <p>与 {@link PdfCommand} 同构——不走 JavaFX、主窗口开着就并到那个实例、
 * 转完弹同一张结果卡片——只是方向反过来：PDF 进，Word / Excel / PPT / 文本出。
 *
 * <p>日志与 {@link PdfCommand} 共用 {@code ~/.daliwan/convert.log}，
 * 排查时不用分两个文件看。
 */
public final class OfficeCommand {

    private static final Path LOG_FILE = Path.of(
            System.getProperty("user.home"), ".daliwan", "convert.log");

    private OfficeCommand() {
    }

    /** 本类认得的全部开关，顺序与右键菜单上的排列一致。 */
    public static List<String> flags() {
        List<String> values = new ArrayList<>();
        for (PdfToOfficeConverter.Target target : PdfToOfficeConverter.Target.values()) {
            values.add(target.flag());
        }
        return List.copyOf(values);
    }

    /** 命令行里是否包含 PDF 转 Office 的开关。 */
    public static boolean matches(String[] args) {
        return flagOf(args) != null;
    }

    /** 命令行里的第一个 PDF 转 Office 开关；没有则返回 null。 */
    public static String flagOf(String[] args) {
        if (args == null) {
            return null;
        }
        for (String argument : args) {
            PdfToOfficeConverter.Target target = PdfToOfficeConverter.fromFlag(argument);
            if (target != null) {
                return target.flag();
            }
        }
        return null;
    }

    /** 执行一次右键菜单转换，结束后不返回 JavaFX。 */
    public static void run(String[] args) {
        String flag = flagOf(args);
        if (flag == null) {
            PdfResultDialog.show(List.of(), "没有收到要转换的文件");
            return;
        }
        List<Path> files = PdfCommand.collect(args);
        if (files.isEmpty()) {
            PdfResultDialog.show(List.of(), "没有收到要转换的文件");
            return;
        }
        // 主窗口开着时只借用它的进程，本体该干什么还干什么，界面不动
        if (InstanceBridge.forward(flag, files)) {
            log("[delegate] " + files.size() + " 个文件交给已运行的实例（后台转换，" + flag + "）");
            return;
        }
        convert(files, flag);
    }

    /**
     * 已运行的实例收到请求时调用：开一条后台线程转完再弹卡片，立刻返回。
     *
     * <p>必须在调用线程之外执行——监听线程要马上回去接下一个连接，
     * 否则多选文件时后面的请求会连不上。
     */
    public static void convertAsync(InstanceBridge.Request request) {
        if (request == null || request.files() == null || request.files().isEmpty()) {
            return;
        }
        String flag = request.flag();
        List<Path> files = request.files();
        Thread worker = new Thread(() -> convert(files, flag), "pdf-to-office");
        worker.setDaemon(true);
        worker.start();
    }

    /** 逐个文件转换并弹一张结果卡片；不碰任何窗口状态。 */
    private static void convert(List<Path> files, String flag) {
        PdfToOfficeConverter.Target target = PdfToOfficeConverter.fromFlag(flag);
        if (target == null) {
            PdfResultDialog.show(List.of(), "不支持的转换开关：" + flag);
            return;
        }
        List<PdfCommand.Outcome> outcomes = new ArrayList<>(files.size());
        for (Path file : files) {
            long startedAt = System.currentTimeMillis();
            try {
                if (!Files.isRegularFile(file)) {
                    throw new ConversionException("文件不存在或已被移动");
                }
                Path output = PdfToOfficeConverter.targetOf(file, target);
                PdfToOfficeConverter.convert(file, output, target);
                outcomes.add(new PdfCommand.Outcome(file, output, null));
                log("[ok] " + file + " -> " + output + " (" + flag + ", "
                        + (System.currentTimeMillis() - startedAt) + " ms)");
            } catch (Exception e) {
                String message = e.getMessage() == null || e.getMessage().isBlank()
                        ? e.getClass().getSimpleName()
                        : e.getMessage();
                outcomes.add(new PdfCommand.Outcome(file, null, message));
                log("[fail] " + file + " : " + message.replace('\n', ' '));
            }
        }
        PdfResultDialog.show(outcomes, null);
    }

    /** 追加一行日志；超过上限先裁剪，避免无限制增长。 */
    private static synchronized void log(String line) {
        CommandLog.append(LOG_FILE, line);
    }
}
