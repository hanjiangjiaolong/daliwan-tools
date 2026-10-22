package com.daliwan.tool.util;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 输出文件命名的小工具。
 *
 * <p>界面上多处都要「在同一个目录里生成一个不重名的输出文件」，
 * 各自手写时容易踩同一个坑：先 {@code name.toLowerCase().endsWith(".pdf")}
 * 判断后缀（大小写不敏感），再用 {@code name.replace(".pdf", ...)} 拼接
 * （大小写敏感）。用户输入 {@code 报告.PDF} 时前者通过、后者不命中，
 * 算出来的目标路径每次循环都一样，{@code Files.exists} 恒为真——
 * 界面线程直接死循环。统一走这里就不会有这个问题。
 */
public final class FileNames {

    /** 重名时追加序号的最大尝试次数，超出后仍用最后一个候选（此时基本已无重名）。 */
    private static final int MAX_ATTEMPTS = 999;

    private FileNames() {
    }

    /** 去掉最后一个扩展名，如 {@code 合同.pdf} → {@code 合同}。 */
    public static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    /** 扩展名（含点，小写），没有扩展名时返回空串。 */
    public static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(dot).toLowerCase(java.util.Locale.ROOT) : "";
    }

    /**
     * 在指定目录里找一个不与现有文件重名的路径：原名可用就直接用，
     * 否则在基名后追加 {@code (1)}、{@code (2)}……直到不冲突。
     *
     * @param directory 目标目录
     * @param fileName  期望的文件名，如 {@code 合并结果.pdf}
     */
    public static Path uniqueTarget(Path directory, String fileName) {
        Path candidate = directory.resolve(fileName);
        if (!Files.exists(candidate)) {
            return candidate;
        }
        String base = baseName(fileName);
        String ext = extension(fileName);
        for (int index = 1; index <= MAX_ATTEMPTS; index++) {
            candidate = directory.resolve(base + "(" + index + ")" + ext);
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        return candidate;
    }
}
