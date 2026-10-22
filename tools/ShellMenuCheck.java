import com.daliwan.tool.core.shell.AppLauncher;
import com.daliwan.tool.core.shell.ContextMenuIntegration;

/**
 * 右键菜单注册/注销的临时验证工具（不参与打包）。
 *
 * <p>用法：{@code java ShellMenuCheck install|remove|info}
 */
public class ShellMenuCheck {

    public static void main(String[] args) throws Exception {
        String action = args.length > 0 ? args[0] : "info";
        System.out.println("[supported] " + ContextMenuIntegration.isSupported());
        System.out.println("[launcher] " + AppLauncher.resolve()
                .map(spec -> spec.quotedPrefix() + "   ownIcon=" + spec.ownIcon())
                .orElse("未识别"));
        System.out.println("[command] " + safeCommand());
        switch (action) {
            case "install" -> {
                long startedAt = System.currentTimeMillis();
                ContextMenuIntegration.install();
                System.out.println("[ok] installed in " + (System.currentTimeMillis() - startedAt) + " ms");
            }
            case "remove" -> {
                long startedAt = System.currentTimeMillis();
                ContextMenuIntegration.uninstall();
                System.out.println("[ok] removed in " + (System.currentTimeMillis() - startedAt) + " ms");
            }
            default -> System.out.println("[info] 只打印环境，不改注册表");
        }
    }

    private static String safeCommand() {
        try {
            return ContextMenuIntegration.currentCommand();
        } catch (Exception e) {
            return "（不可用：" + e.getMessage() + "）";
        }
    }
}
