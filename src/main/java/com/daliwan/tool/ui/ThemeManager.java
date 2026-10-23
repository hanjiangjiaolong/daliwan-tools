package com.daliwan.tool.ui;

import com.daliwan.tool.core.ToolSettings;
import com.daliwan.tool.util.SystemTheme;
import javafx.application.Platform;
import javafx.scene.Scene;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * 主题切换：把「结构样式」与「配色」拆成两层。
 *
 * <p>{@code app.css} 只定义布局与形态，颜色一律引用 {@code -c-*} 语义变量；
 * {@code theme-dark.css} / {@code theme-light.css} 负责给这些变量赋值。
 * 换主题时替换样式表即可，不需要重建界面，页面里也不该出现写死的颜色。
 *
 * <p>偏好有三种：跟随系统（默认）、浅色、深色。跟随系统时实际配色由
 * {@link SystemTheme} 探测，JavaFX 25 的系统偏好监听会在系统切换明暗时实时回调，
 * 无需重启，也不用等窗口重新聚焦。
 */
public final class ThemeManager {

    // 路径跟资源目录 src/main/resources/com/daliwan/tool/styles/ 一一对应，
    // 改包名时这里必须同步，否则样式表静默 404、界面退回无样式状态
    private static final String BASE = "/com/daliwan/tool/styles/app.css";
    private static final String DARK = "/com/daliwan/tool/styles/theme-dark.css";
    private static final String LIGHT = "/com/daliwan/tool/styles/theme-light.css";

    /** 两次系统配色探测之间的最小间隔，避免焦点频繁变化时反复探测。 */
    private static final long DETECT_INTERVAL_MILLIS = 1500L;

    private static final List<Runnable> LISTENERS = new ArrayList<>();

    /** 系统配色监听只注册一次。 */
    private static boolean schemeListenerInstalled;

    private static Scene scene;
    /** 上次真正发起探测的时间戳。 */
    private static volatile long lastDetectMillis;
    /** 用户偏好：system / light / dark。 */
    private static String preference = ToolSettings.THEME_SYSTEM;
    /** 实际生效的主题：light / dark。 */
    private static String current = ToolSettings.THEME_LIGHT;
    /** 最近一次探测到的系统配色，作为「跟随系统」时的首帧取值。 */
    private static volatile String lastDetected = ToolSettings.THEME_LIGHT;

    private ThemeManager() {
    }

    /**
     * 绑定场景并应用保存的主题偏好。
     *
     * <p>「跟随系统」时不在这里同步探测：探测要起一个外部进程，最坏情况要等满超时，
     * 而 {@code install()} 是在窗口 show 之前调用的，堵在这里就是启动时的白屏。
     * 改成先用上次探测的结果（首次为浅色）立即渲染，再让后台线程校准一次。
     */
    public static void install(Scene target, String themePreference) {
        scene = target;
        preference = ToolSettings.isValidTheme(themePreference)
                ? themePreference : ToolSettings.THEME_SYSTEM;
        apply(resolve(preference));
        installColorSchemeListener();
        if (ToolSettings.THEME_SYSTEM.equals(preference)) {
            // 启动这一次不做去抖，让配色尽快校准到位
            lastDetectMillis = 0L;
            refreshFromSystem();
        }
    }

    /**
     * 监听系统配色变化（JavaFX 25 的系统偏好 API）。
     *
     * <p>用户在系统设置里切换明暗时这里会实时回调，主题立即跟上——
     * 不再依赖「窗口重新聚焦时探测一次」：切换发生在后台时也能即时生效。
     * toolkit 未启动（独立短进程、单测）或 API 不可用时静默跳过，
     * 那些场景本来也不显示主界面。
     */
    private static void installColorSchemeListener() {
        if (schemeListenerInstalled) {
            return;
        }
        schemeListenerInstalled = true;
        try {
            Platform.getPreferences().colorSchemeProperty().addListener((observable, old, now) -> {
                String resolved = SystemTheme.themeOfColorScheme(now);
                if (resolved == null) {
                    return;
                }
                lastDetected = resolved;
                if (ToolSettings.THEME_SYSTEM.equals(preference) && !resolved.equals(current)) {
                    apply(resolved);
                }
            });
        } catch (RuntimeException | LinkageError ignored) {
            // 拿不到系统偏好就退回原来的探测路径
            schemeListenerInstalled = false;
        }
    }

    /** 用户偏好：{@link ToolSettings#THEME_SYSTEM}、{@link ToolSettings#THEME_DARK} 或 {@link ToolSettings#THEME_LIGHT}。 */
    public static String preference() {
        return preference;
    }

    /** 当前实际生效的主题名（只会是 light 或 dark）。 */
    public static String current() {
        return current;
    }

    /**
     * 按「跟随系统 → 浅色 → 深色 → 跟随系统」循环切换，返回切换后的偏好。
     */
    public static String cycle() {
        String next = switch (preference) {
            case ToolSettings.THEME_SYSTEM -> ToolSettings.THEME_LIGHT;
            case ToolSettings.THEME_LIGHT -> ToolSettings.THEME_DARK;
            default -> ToolSettings.THEME_SYSTEM;
        };
        preference = next;
        apply(resolve(next));
        if (ToolSettings.THEME_SYSTEM.equals(next)) {
            // 切回「跟随系统」时也重新探测一次，用上最新的系统配色
            lastDetectMillis = 0L;
            refreshFromSystem();
        }
        return preference;
    }

    /**
     * 重新读取系统配色，仅在「跟随系统」且实际配色发生变化时刷新。
     *
     * <p>探测要起一个外部进程，因此放到守护线程里做，结果再回到 FX 线程应用，
     * 避免切换窗口时界面出现卡顿。
     *
     * <p>窗口焦点在拖拽、Alt+Tab、弹出菜单等场景下会短时间内来回切换，
     * 每次都起进程会让机器白白忙一阵，因此加了 1.5 秒的去抖：
     * 间隔不足时直接跳过，反正配色不会在这么短的时间里连变两次。
     */
    public static void refreshFromSystem() {
        if (!ToolSettings.THEME_SYSTEM.equals(preference)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastDetectMillis < DETECT_INTERVAL_MILLIS) {
            return;
        }
        lastDetectMillis = now;
        Thread worker = new Thread(() -> {
            String resolved = SystemTheme.detect();
            lastDetected = resolved;
            Platform.runLater(() -> {
                // 探测期间用户可能已经手动改了主题，再确认一次
                if (ToolSettings.THEME_SYSTEM.equals(preference) && !resolved.equals(current)) {
                    apply(resolved);
                }
            });
        }, "system-theme-detect");
        worker.setDaemon(true);
        worker.start();
    }

    /** 主题变化时回调（用于刷新图标等跟主题相关的图形）。 */
    public static void onChange(Runnable listener) {
        LISTENERS.add(listener);
    }

    /**
     * 把偏好解析成实际配色。
     *
     * <p>「跟随系统」取最近一次探测结果：真正的探测在后台线程做，
     * 这里只负责给首帧一个立即可用的值。
     */
    private static String resolve(String themePreference) {
        return switch (themePreference) {
            case ToolSettings.THEME_LIGHT -> ToolSettings.THEME_LIGHT;
            case ToolSettings.THEME_DARK -> ToolSettings.THEME_DARK;
            default -> lastDetected;
        };
    }

    private static void apply(String theme) {
        current = theme;
        // 记一份给独立进程看（右键菜单转 PDF 的结果卡片要跟着主题配色）
        SystemTheme.remember(theme);
        if (scene != null) {
            scene.getStylesheets().clear();
            add(scene, BASE);
            add(scene, ToolSettings.THEME_LIGHT.equals(theme) ? LIGHT : DARK);
        }
        for (Runnable listener : List.copyOf(LISTENERS)) {
            listener.run();
        }
    }

    /**
     * 三个样式表的 classpath 路径，供测试断言资源确实存在。
     *
     * <p>加载失败只会往 stderr 打一行，界面看起来就是「样式全没了」但不报错，
     * 改包名时最容易在这里漏改，所以让测试兜住。
     */
    static List<String> resourcePaths() {
        return List.of(BASE, DARK, LIGHT);
    }

    private static void add(Scene target, String resource) {
        URL url = ThemeManager.class.getResource(resource);
        if (url != null) {
            target.getStylesheets().add(url.toExternalForm());
        } else {
            System.err.println("样式表缺失：" + resource);
        }
    }
}
