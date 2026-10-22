package com.daliwan.tool;

import com.daliwan.tool.core.cli.OfficeCommand;
import com.daliwan.tool.core.cli.PdfCommand;
import com.daliwan.tool.core.cli.UpdateCommand;

/**
 * 独立启动类。
 *
 * <p>非模块化（classpath）方式运行时，若主类直接继承 {@code Application}，
 * JVM 启动会报 “JavaFX runtime components are missing”。用一个普通类转一道，
 * 即可通过 {@code java -cp ... Launcher} 正常启动。
 *
 * <p>这里也是「跑完就退出」的命令行入口：带转换开关（{@code --to-pdf}、{@code --to-docx} …）
 * 时只做一次转换，带 {@code --update-block on|off|status} 时只动一次更新开关，
 * 两者都不拉起 JavaFX。
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        if (PdfCommand.matches(args)) {
            PdfCommand.run(args);
            return;
        }
        if (OfficeCommand.matches(args)) {
            OfficeCommand.run(args);
            return;
        }
        if (UpdateCommand.matches(args)) {
            UpdateCommand.run(args);
            return;
        }
        DaliwanApplication.main(args);
    }
}
