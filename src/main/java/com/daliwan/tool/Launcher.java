package com.daliwan.tool;

import com.daliwan.tool.core.cli.PdfCommand;

/**
 * 独立启动类。
 *
 * <p>非模块化（classpath）方式运行时，若主类直接继承 {@code Application}，
 * JVM 启动会报 “JavaFX runtime components are missing”。用一个普通类转一道，
 * 即可通过 {@code java -cp ... Launcher} 正常启动。
 *
 * <p>这里也是右键菜单的入口：带 {@code --to-pdf} 参数时只做一次转换就退出，
 * 不拉起 JavaFX。
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        if (PdfCommand.matches(args)) {
            PdfCommand.run(args);
            return;
        }
        DaliwanApplication.main(args);
    }
}
