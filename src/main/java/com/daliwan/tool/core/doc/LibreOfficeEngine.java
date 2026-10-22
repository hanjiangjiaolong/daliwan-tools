package com.daliwan.tool.core.doc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 通过 LibreOffice 的命令行接口转换。
 *
 * <p>做法是先转到临时目录，再移动到目标路径。这样有两个好处：
 * <ul>
 *   <li>LibreOffice 只会按源文件名输出，不会覆盖同级目录里的同名 PDF</li>
 *   <li>目标路径带 {@code (1)} 之类后缀时也能正确落盘</li>
 * </ul>
 */
public class LibreOfficeEngine implements ConversionEngine {

    private static final int TIMEOUT_SECONDS = 180;

    /** 常见的安装位置，按优先级排列。 */
    private static final String[] CANDIDATES = {
            "C:\\Program Files\\LibreOffice\\program\\soffice.exe",
            "C:\\Program Files (x86)\\LibreOffice\\program\\soffice.exe",
            "D:\\Program Files\\LibreOffice\\program\\soffice.exe",
            "D:\\soft\\LibreOffice\\program\\soffice.exe"
    };

    private final Path executable;

    public LibreOfficeEngine(Path executable) {
        this.executable = executable;
    }

    /** 查找本机可用的 soffice 可执行文件，找不到返回 null。 */
    public static Path findExecutable() {
        for (String candidate : CANDIDATES) {
            Path path = Path.of(candidate);
            if (Files.isExecutable(path)) {
                return path;
            }
        }
        // 便携版：本程序目录下的 LibreOffice
        String home = System.getProperty("toolbox.home", ".");
        Path portable = Path.of(home, "LibreOffice", "program", "soffice.exe");
        if (Files.isExecutable(portable)) {
            return portable;
        }
        return null;
    }

    @Override
    public String name() {
        return "LibreOffice";
    }

    @Override
    public void convert(Path word, Path pdf) throws ConversionException {
        Path workDir;
        try {
            workDir = Files.createTempDirectory("word2pdf-");
        } catch (IOException e) {
            throw new ConversionException("无法创建临时目录：" + e.getMessage(), e);
        }

        try {
            List<String> command = new ArrayList<>();
            command.add(executable.toString());
            command.add("--headless");
            command.add("--norestore");
            command.add("--nolockcheck");
            command.add("--nodefault");
            command.add("--convert-to");
            command.add("pdf");
            command.add("--outdir");
            command.add(workDir.toString());
            command.add(word.toAbsolutePath().toString());

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String output = readAll(process.getInputStream());

            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new ConversionException("转换超时（超过 " + TIMEOUT_SECONDS + " 秒），文件可能过大或已损坏");
            }
            if (process.exitValue() != 0) {
                throw new ConversionException("LibreOffice 退出码 " + process.exitValue()
                        + (output.isBlank() ? "" : "：" + output.strip()));
            }

            Path produced = workDir.resolve(baseName(word) + ".pdf");
            if (!Files.exists(produced)) {
                throw new ConversionException("转换未生成 PDF，请确认源文件可以被 LibreOffice 正常打开。"
                        + (output.isBlank() ? "" : " 引擎输出：" + output.strip()));
            }
            Files.move(produced, pdf, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ConversionException("调用 LibreOffice 失败：" + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConversionException("转换被中断", e);
        } finally {
            deleteQuietly(workDir);
        }
    }

    private static String baseName(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String readAll(InputStream in) {
        try (in) {
            return new String(in.readAllBytes(), Charset.defaultCharset());
        } catch (IOException e) {
            return "";
        }
    }

    private static void deleteQuietly(Path dir) {
        try (var stream = Files.walk(dir)) {
            stream.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // 清理失败不影响结果
                        }
                    });
        } catch (IOException ignored) {
            // 同上
        }
    }
}
