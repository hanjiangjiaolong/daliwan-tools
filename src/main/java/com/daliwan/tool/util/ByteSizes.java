package com.daliwan.tool.util;

import java.util.Locale;

/**
 * 字节数的人类可读表示。
 *
 * <p>状态栏、预览信息、结果提示里都要显示文件大小，各自实现过三份（单位档位与
 * 小数位数还不一致），统一放这里：1024 进制，不足一档就留在当前档。
 */
public final class ByteSizes {

    private ByteSizes() {
    }

    /**
     * 格式化字节数，如 {@code 812 B}、{@code 15.4 KB}、{@code 2.31 MB}、{@code 1.05 GB}。
     *
     * <p>KB 保留一位小数（这个量级看整数就够了），MB / GB 保留两位——
     * 几十兆的文件差 0.1 MB 也是十几万字节，一位小数会把它们显示成同一个数。
     */
    public static String format(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.ROOT, "%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format(Locale.ROOT, "%.2f MB", mb);
        }
        return String.format(Locale.ROOT, "%.2f GB", mb / 1024.0);
    }
}
