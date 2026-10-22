package com.daliwan.tool.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

/**
 * 界面偏好设置：记录哪些功能在左侧导航中隐藏。
 *
 * <p>配置存放于用户目录下的纯文本文件，方便备份与手工修改：
 * <pre>
 * Windows: C:\Users\&lt;用户名&gt;\.daliwan\settings.properties
 * </pre>
 * 只记录「被隐藏」的项，新增功能默认可见，不会因为升级而消失。
 */
public final class ToolSettings {

    /** 默认配置文件位置。 */
    public static final Path DEFAULT_FILE = Path.of(
            System.getProperty("user.home"), ".daliwan", "settings.properties");

    /** 旧版配置位置（更名前的 .dev-toolbox），仅用于一次性迁移读取。 */
    public static final Path LEGACY_FILE = Path.of(
            System.getProperty("user.home"), ".dev-toolbox", "settings.properties");

    private static final String HIDDEN_KEY = "nav.hidden";
    private static final String THEME_KEY = "ui.theme";
    private static final String THEME_EXPLICIT_KEY = "ui.theme.explicit";
    private static final String SHELL_PDF_KEY = "shell.pdf.menu";
    private static final String SHELL_PDF_COMMAND_KEY = "shell.pdf.menu.command";
    private static final String SHELL_OFFICE_KEY = "shell.office.menu";
    private static final String SHELL_OFFICE_COMMAND_KEY = "shell.office.menu.command";
    private static final String WIN11_CLASSIC_KEY = "shell.win11.classic";

    /** 主题名：跟随系统 / 深色 / 浅色。 */
    public static final String THEME_SYSTEM = "system";
    public static final String THEME_DARK = "dark";
    public static final String THEME_LIGHT = "light";

    private final Path file;
    private final Set<String> hidden = new LinkedHashSet<>();
    private String theme = THEME_SYSTEM;
    /** 是否为用户手动选择的主题；旧版本存下的 dark 只是当年的默认值，不算选择。 */
    private boolean themeExplicit;
    /** 是否把「大力丸 · 转 PDF」写进资源管理器右键菜单。 */
    private boolean shellPdfMenu;
    /** 上次写进注册表的命令行；程序目录被挪动后据此判断要不要重写。 */
    private String shellPdfCommand = "";
    /** 是否已在 PDF 文件上注册「转 Word / Excel / PPT / 提取文本」。 */
    private boolean shellOfficeMenu;
    /** PDF 那一组菜单上次写入的命令行，同样用于路径变化检测。 */
    private String shellOfficeCommand = "";
    /** 是否把 Win11 的右键菜单换回经典样式，让第三方菜单项直接显示。 */
    private boolean win11ClassicMenu;

    private ToolSettings(Path file) {
        this.file = file;
    }

    /** 主题名是否为可识别的取值。 */
    public static boolean isValidTheme(String theme) {
        return THEME_SYSTEM.equals(theme) || THEME_DARK.equals(theme) || THEME_LIGHT.equals(theme);
    }

    /** 从默认位置加载；新位置没有而旧位置有时，读旧文件实现无缝迁移（保存仍写新位置）。 */
    public static ToolSettings load() {
        if (!Files.isRegularFile(DEFAULT_FILE) && Files.isRegularFile(LEGACY_FILE)) {
            return load(LEGACY_FILE, DEFAULT_FILE);
        }
        return load(DEFAULT_FILE);
    }

    /** 从指定位置加载（便于测试）。 */
    static ToolSettings load(Path path) {
        return load(path, path);
    }

    /** 从 source 读取、保存到 target（用于旧配置迁移）。 */
    private static ToolSettings load(Path source, Path target) {
        ToolSettings settings = new ToolSettings(target);
        if (!Files.isRegularFile(source)) {
            return settings;
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(source)) {
            properties.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return settings;
        }
        String value = properties.getProperty(HIDDEN_KEY, "");
        for (String id : value.split(",")) {
            if (!id.isBlank()) {
                settings.hidden.add(id.trim());
            }
        }
        String theme = properties.getProperty(THEME_KEY, "").trim();
        boolean explicit = "true".equalsIgnoreCase(properties.getProperty(THEME_EXPLICIT_KEY, "false").trim());
        // 只有用户手动选过的主题才沿用；旧配置里的 dark 是历史默认值，按「跟随系统」处理
        if (explicit && isValidTheme(theme)) {
            settings.theme = theme;
            settings.themeExplicit = true;
        }
        settings.shellPdfMenu = "true".equalsIgnoreCase(
                properties.getProperty(SHELL_PDF_KEY, "false").trim());
        settings.shellPdfCommand = properties.getProperty(SHELL_PDF_COMMAND_KEY, "").trim();
        settings.shellOfficeMenu = "true".equalsIgnoreCase(
                properties.getProperty(SHELL_OFFICE_KEY, "false").trim());
        settings.shellOfficeCommand = properties.getProperty(SHELL_OFFICE_COMMAND_KEY, "").trim();
        settings.win11ClassicMenu = "true".equalsIgnoreCase(
                properties.getProperty(WIN11_CLASSIC_KEY, "false").trim());
        return settings;
    }

    /** 当前主题偏好：{@link #THEME_SYSTEM}、{@link #THEME_DARK} 或 {@link #THEME_LIGHT}。 */
    public String theme() {
        return theme;
    }

    /** 是否由用户手动指定，否则表示跟随系统。 */
    public boolean themeExplicit() {
        return themeExplicit;
    }

    /** 切换主题，下次启动生效于读取阶段，本次由调用方负责换样式表。 */
    public void setTheme(String theme) {
        if (isValidTheme(theme)) {
            this.theme = theme;
            this.themeExplicit = true;
        }
    }

    /** 该功能是否显示在导航中。 */
    public boolean isVisible(String id) {
        return !hidden.contains(id);
    }

    /** 设置某个功能的可见性。 */
    public void setVisible(String id, boolean visible) {
        if (visible) {
            hidden.remove(id);
        } else {
            hidden.add(id);
        }
    }

    /** 当前被隐藏的功能标识。 */
    public Set<String> hiddenIds() {
        return Set.copyOf(hidden);
    }

    /** 是否已在资源管理器右键菜单里注册「转 PDF」。 */
    public boolean shellPdfMenu() {
        return shellPdfMenu;
    }

    /** 记录右键菜单开关；真正的注册表写入由界面层负责。 */
    public void setShellPdfMenu(boolean enabled) {
        this.shellPdfMenu = enabled;
    }

    /** 最后一次成功写入注册表的命令行，空串表示没有记录。 */
    public String shellPdfCommand() {
        return shellPdfCommand;
    }

    public void setShellPdfCommand(String command) {
        this.shellPdfCommand = command == null ? "" : command;
    }

    /** 是否已在 PDF 文件的右键菜单里注册「转 Word / Excel / PPT / 提取文本」。 */
    public boolean shellOfficeMenu() {
        return shellOfficeMenu;
    }

    /** 记录 PDF 那一组右键菜单开关；真正的注册表写入由界面层负责。 */
    public void setShellOfficeMenu(boolean enabled) {
        this.shellOfficeMenu = enabled;
    }

    /** PDF 那组菜单最后一次成功写入的命令行，空串表示没有记录。 */
    public String shellOfficeCommand() {
        return shellOfficeCommand;
    }

    public void setShellOfficeCommand(String command) {
        this.shellOfficeCommand = command == null ? "" : command;
    }

    /** 是否已把 Win11 的新版右键菜单换回经典样式。 */
    public boolean win11ClassicMenu() {
        return win11ClassicMenu;
    }

    /** 记录经典菜单开关；注册表写入由界面层负责。 */
    public void setWin11ClassicMenu(boolean enabled) {
        this.win11ClassicMenu = enabled;
    }

    /** 配置文件位置，用于界面上提示。 */
    public Path file() {
        return file;
    }

    /** 写入磁盘；父目录不存在时自动创建。 */
    public void save() throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Properties properties = new Properties();
        properties.setProperty(HIDDEN_KEY, String.join(",", hidden));
        properties.setProperty(THEME_KEY, theme);
        // 没手动选过就不写 explicit，下次启动继续跟随系统
        if (themeExplicit) {
            properties.setProperty(THEME_EXPLICIT_KEY, "true");
        }
        properties.setProperty(SHELL_PDF_KEY, Boolean.toString(shellPdfMenu));
        properties.setProperty(SHELL_PDF_COMMAND_KEY, shellPdfCommand);
        properties.setProperty(SHELL_OFFICE_KEY, Boolean.toString(shellOfficeMenu));
        properties.setProperty(SHELL_OFFICE_COMMAND_KEY, shellOfficeCommand);
        properties.setProperty(WIN11_CLASSIC_KEY, Boolean.toString(win11ClassicMenu));
        try (Writer writer = new OutputStreamWriter(
                Files.newOutputStream(file), StandardCharsets.UTF_8)) {
            properties.store(writer, "大力丸 settings - hidden navigation entries");
        }
    }
}
