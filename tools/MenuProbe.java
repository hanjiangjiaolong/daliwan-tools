package com.daliwan.tool.core.shell;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 右键菜单自检（tools，不参与打包）。
 *
 * <p>用法：
 * <pre>
 *   java -cp "target/daliwan_tool.jar;target/tools" com.daliwan.tool.core.shell.MenuProbe print
 *   java -cp "target/daliwan_tool.jar;target/tools" com.daliwan.tool.core.shell.MenuProbe install
 *   java -cp "target/daliwan_tool.jar;target/tools" com.daliwan.tool.core.shell.MenuProbe uninstall
 * </pre>
 *
 * <p>install 走的是打包后的 exe 路径，写出来的注册表跟用户在设置页里开开关的结果一致，
 * 这样不用点界面也能验证资源管理器里的两级菜单。
 */
public final class MenuProbe {

    private static final String EXE = "D:\\workspace2\\daliwan\\daliwan_tool\\dist\\daliwan_tool\\大力丸.exe";

    private MenuProbe() {
    }

    public static void main(String[] args) throws Exception {
        String mode = args.length == 0 ? "print" : args[0];
        String prefix = "\"" + EXE + "\"";
        String icon = EXE + ",0";
        List<ContextMenuIntegration.MenuVerb> verbs = ContextMenuIntegration.ALL;

        switch (mode) {
            case "print" -> System.out.println(
                    ContextMenuIntegration.installScript(verbs, prefix, icon, true));
            case "install" -> {
                if (!Files.exists(Path.of(EXE))) {
                    System.out.println("[probe] 找不到 exe：" + EXE);
                    return;
                }
                ContextMenuIntegration.runPowerShell(
                        ContextMenuIntegration.installScript(verbs, prefix, icon, true));
                System.out.println("[probe] 已写入注册表（HKCU）");
            }
            case "uninstall" -> {
                ContextMenuIntegration.runPowerShell(
                        ContextMenuIntegration.uninstallScript(verbs));
                System.out.println("[probe] 已清除注册表");
            }
            default -> System.out.println("[probe] 未知模式：" + mode);
        }
    }
}
