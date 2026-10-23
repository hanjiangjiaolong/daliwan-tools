package com.daliwan.tool.ui;

import com.daliwan.tool.core.ToolSettings;
import com.daliwan.tool.core.shell.AppLauncher;
import com.daliwan.tool.core.shell.ContextMenuIntegration;
import com.daliwan.tool.core.shell.Win11Menu;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 主界面：左侧分组导航 + 右侧「标题栏 + 内容区」。
 *
 * <p>页面在首次选中时才创建并缓存，避免启动时同时构建全部控件；
 * 导航项可通过「界面设置」按需隐藏，隐藏状态与主题一起保存在用户目录的配置文件中。
 * 被隐藏的页面不会失去入口——它们收在侧栏底部的「更多」按钮里，点开即可进入。
 */
public class MainView extends BorderPane {

    /**
     * 导航项定义。
     *
     * @param id      稳定标识，用于持久化与图标查找，改标题不会影响已保存的设置
     * @param group   分组名称
     * @param title   显示名称
     * @param hint    副标题
     * @param factory 页面构造器，首次访问时调用
     */
    private record NavEntry(String id, String group, String title, String hint, Supplier<Node> factory) {
    }

    /**
     * 按常用性从高到低排列。
     *
     * <p>第一梯队是 JSON、MyBatis 日志与编码转换这类每天都要用的；工具类（二维码、比对、OCR）
     * 与摘要、加密属于「偶尔查一下」，放在中间偏后；文档转换最末。
     * 同组内同样按使用频率排序。
     */
    private static final List<NavEntry> ENTRIES = List.of(
            new NavEntry("json", "JSON", "JSON 工具", "格式化 · 压缩 · 树视图 · 表格", JsonPane::new),
            new NavEntry("sql", "SQL", "MyBatis SQL", "日志拼接 · 自动填参", SqlPane::new),
            new NavEntry("timestamp", "转换", "时间戳", "时间与时间戳互转", TimestampPane::new),
            new NavEntry("base64", "转换", "Base64", "文本 / 图片 ⇄ Base64", Base64Pane::new),
            new NavEntry("url", "转换", "URL 编解码", "百分号编码与还原", UrlCodecPane::new),
            new NavEntry("unicode", "转换", "Unicode", "\\uXXXX 转义与还原", UnicodePane::new),
            new NavEntry("uuid", "生成", "UUID · 随机串", "批量生成标识与口令", RandomPane::new),
            new NavEntry("qrcode", "工具", "二维码", "生成 · 识别", QrCodePane::new),
            new NavEntry("diff", "工具", "文本比对", "左右分屏 · 行内高亮", TextDiffPane::new),
            new NavEntry("img-meta", "工具", "图片信息", "EXIF 查看 · 去除元信息", ImgMetaPane::new),
            new NavEntry("ocr", "工具", "OCR 识别", "图片文字提取", OcrPane::new),
            new NavEntry("hash", "摘要", "哈希摘要", "MD5 · SHA-1/256/384/512", HashPane::new),
            new NavEntry("cipher", "加密", "对称加密", "AES · DES · 3DES", SymmetricCipherPane::new),
            new NavEntry("rsa", "加密", "RSA", "密钥生成 · 加解密 · 签名验签", RsaPane::new),
            new NavEntry("office2pdf", "文档", "Office 转 PDF", "Word · Excel · PPT", DocConvertPane::new),
            new NavEntry("pdf2office", "文档", "PDF 转 Office", "Word · Excel · PPT · 文本", PdfToOfficePane::new),
            new NavEntry("pdf-tools", "文档", "PDF 拆分合并", "按页提取 · 多文件合并", PdfToolsPane::new),
            new NavEntry("img2pdf", "文档", "图片转 PDF", "多图合成一份 PDF", ImageToPdfPane::new),
            new NavEntry("winupdate", "系统", "Windows 更新", "阻止 · 恢复自动更新", UpdateGuardPane::new)
    );

    /** 设置页里「Office 转 PDF 右键菜单」开关的标识。 */
    private static final String SHELL_PDF_OPTION = "shell.pdf";

    /** 设置页里「PDF 转 Office 右键菜单」开关的标识。 */
    private static final String SHELL_OFFICE_OPTION = "shell.office";

    /** 设置页里「Win11 经典右键菜单」开关的标识。 */
    private static final String WIN11_MENU_OPTION = "win11.classic";


    private final ToolSettings settings = ToolSettings.load();
    private final StackPane content = new StackPane();
    private final VBox navBox = new VBox(2);
    private final Map<String, Node> pageCache = new HashMap<>();
    private final ToggleGroup navGroup = new ToggleGroup();

    /** 侧栏底部的「更多」按钮：只在有页面被隐藏时出现。 */
    private final Button moreButton = new Button();

    /** 导航滚动容器，展开「更多」后要把列表滚到底。 */
    private ScrollPane navScroll;

    /**
     * 「更多」当前是否展开。
     *
     * <p>不写进配置：它描述的是这一次浏览时的临时状态，重启后收起来即可。
     */
    private boolean showHidden;

    private final Label pageTitle = new Label();
    private final Label pageSubtitle = new Label();
    private final Button themeButton = new Button();
    private final Button settingsButton = new Button();
    private final Button aboutButton = new Button();
    private final Button backButton = new Button("返回");

    private String currentId;
    private SettingsPane settingsPane;
    private String settingsReturnId;

    public MainView() {
        getStyleClass().add("app-root");
        content.getStyleClass().add("content-area");

        rebuildNav();
        setLeft(buildSidebar());
        setCenter(buildMainArea());

        ThemeManager.onChange(this::refreshThemeIcon);
        refreshThemeIcon();

        // 注册表校准要起 powerShell / reg.exe，一上来就跑会和首屏抢 CPU 与磁盘，
        // 把启动拖慢一截；等窗口画出来、界面空下来了再做，结果一样，只是晚一两秒
        PauseTransition idle = new PauseTransition(Duration.millis(1200));
        idle.setOnFinished(event -> {
            restoreShellIntegration();
            syncWin11MenuState();
        });
        idle.play();
    }

    /**
     * 启动时用注册表状态校准「经典右键菜单」开关。
     *
     * <p>配置文件只是我们的记录，注册表才是真相：用户可能用别的工具改过，也可能
     * 系统还原后键没了。发现不一致就以注册表为准，免得设置页显示的和实际行为相反。
     */
    private void syncWin11MenuState() {
        Thread worker = new Thread(() -> {
            if (!Win11Menu.isWindows11()) {
                return;
            }
            Boolean actual = Win11Menu.isEnabled();
            if (actual == null || actual == settings.win11ClassicMenu()) {
                return;
            }
            Platform.runLater(() -> {
                settings.setWin11ClassicMenu(actual);
                saveQuietly();
                // 设置页可能已经开着：重新读一遍，免得开关停在旧状态
                if (settingsPane != null && content.getChildren().contains(settingsPane)) {
                    settingsPane.reload(settings.file().toString());
                }
            });
        }, "win11-menu-sync");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * 启动时校准右键菜单。
     *
     * <p>用户可能把整个绿色版目录挪了位置，注册表里记的旧路径就失效了。
     * 只要开关是开着的，就把当前路径重新写一遍；路径没变则跳过，不做无谓的注册表操作。
     */
    private void restoreShellIntegration() {
        if (!AppLauncher.isWindows()) {
            return;
        }
        restoreGroup(settings.shellPdfMenu(), List.of(ContextMenuIntegration.TO_PDF),
                settings.shellPdfCommand(), ContextMenuIntegration.TO_PDF,
                command -> {
                    settings.setShellPdfCommand(command);
                    saveQuietly();
                });
        restoreGroup(settings.shellOfficeMenu(), ContextMenuIntegration.PDF_TO_OFFICE,
                settings.shellOfficeCommand(), ContextMenuIntegration.PDF_TO_OFFICE.get(0),
                command -> {
                    settings.setShellOfficeCommand(command);
                    saveQuietly();
                });
    }

    /**
     * 一组菜单项的路径校准。
     *
     * <p>用组内第一项的命令当作「当前路径签名」：五项命令只有开关不同，
     * 路径变了必然一起变，比对一条就够。
     */
    private void restoreGroup(boolean enabled, List<ContextMenuIntegration.MenuVerb> verbs,
                              String recorded, ContextMenuIntegration.MenuVerb sample,
                              Consumer<String> onWritten) {
        if (!enabled) {
            return;
        }
        final String expected;
        try {
            expected = ContextMenuIntegration.currentCommand(sample);
        } catch (IOException e) {
            return;
        }
        if (expected.equals(recorded)) {
            return;
        }
        Thread worker = new Thread(() -> {
            try {
                ContextMenuIntegration.install(verbs);
                Platform.runLater(() -> onWritten.accept(expected));
            } catch (Exception ignored) {
                // 失败不打扰用户，设置页里再开一次会给出具体原因
            }
        }, "shell-menu-refresh");
        worker.setDaemon(true);
        worker.start();
    }

    // ---------------------------------------------------------------- 侧边栏

    private VBox buildSidebar() {
        Label brandTitle = new Label("大力丸");
        brandTitle.getStyleClass().add("brand-title");
        Label brandSubtitle = new Label("大力出奇迹");
        brandSubtitle.getStyleClass().add("brand-subtitle");

        VBox brand = new VBox(2, brandTitle, brandSubtitle);
        brand.getStyleClass().add("brand");

        // 导航项较多时允许滚动，避免底部提示被挤掉
        navScroll = new ScrollPane(navBox);
        navScroll.setFitToWidth(true);
        navScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        navScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        navScroll.getStyleClass().add("nav-scroll");
        VBox.setVgrow(navScroll, Priority.ALWAYS);

        VBox moreBar = new VBox(buildMoreButton());
        moreBar.getStyleClass().add("nav-more-bar");

        Label footer = new Label("本地运行 · 不联网");
        footer.getStyleClass().add("nav-footer");

        VBox sidebar = new VBox(brand, navScroll, moreBar, footer);
        sidebar.getStyleClass().add("sidebar");
        return sidebar;
    }

    /**
     * 侧栏底部的「更多」按钮。
     *
     * <p>页面被隐藏后不能就此没了入口——用户偶尔还要用它，只是不想让它占着导航。
     * 按钮固定在滚动区下方，点开把隐藏项追加到导航末尾，位置不会跟着跳。
     */
    private Button buildMoreButton() {
        moreButton.setGraphic(Icons.boxed("more", 16));
        moreButton.setMaxWidth(Double.MAX_VALUE);
        moreButton.setAlignment(Pos.CENTER_LEFT);
        moreButton.setPadding(new Insets(6, 10, 6, 10));
        moreButton.setFocusTraversable(false);
        moreButton.getStyleClass().add("nav-more");
        moreButton.setTooltip(new Tooltip("被隐藏的页面都收在这里，点开就能用"));
        moreButton.setOnAction(e -> {
            showHidden = !showHidden;
            rebuildNav();
            if (showHidden && navScroll != null) {
                // 展开的项在列表末尾，不滚过去等于没展开
                Platform.runLater(() -> navScroll.setVvalue(1.0));
            }
        });
        return moreButton;
    }

    /** 按当前设置重建导航按钮。 */
    private void rebuildNav() {
        navBox.getChildren().clear();
        navGroup.getToggles().clear();

        String lastGroup = null;
        List<NavEntry> hidden = new ArrayList<>();
        for (NavEntry entry : ENTRIES) {
            if (!settings.isVisible(entry.id())) {
                hidden.add(entry);
                continue;
            }
            if (!entry.group().equals(lastGroup)) {
                Label groupLabel = new Label(entry.group());
                groupLabel.getStyleClass().add("nav-group");
                navBox.getChildren().add(groupLabel);
                lastGroup = entry.group();
            }
            navBox.getChildren().add(createNavButton(entry));
        }

        // 一个可见项都没有时强制展开：否则主区域空着，也没有别处可以点进去
        boolean nothingVisible = navGroup.getToggles().isEmpty();
        boolean expanded = !hidden.isEmpty() && (showHidden || nothingVisible);
        if (expanded) {
            Label groupLabel = new Label("已隐藏");
            groupLabel.getStyleClass().add("nav-group");
            navBox.getChildren().add(groupLabel);
            for (NavEntry entry : hidden) {
                navBox.getChildren().add(createNavButton(entry));
            }
        }
        updateMoreButton(hidden.size(), expanded, nothingVisible);
        syncNavSelection();
    }

    /** 更新「更多」按钮的文字与显隐；没有隐藏项时整个收掉。 */
    private void updateMoreButton(int hiddenCount, boolean expanded, boolean nothingVisible) {
        boolean visible = hiddenCount > 0 && !nothingVisible;
        moreButton.setVisible(visible);
        moreButton.setManaged(visible);
        moreButton.setText(expanded ? "收起" : "更多（" + hiddenCount + "）");
    }

    /**
     * 让导航高亮跟当前页面对上。
     *
     * <p>重建导航会把按钮全换成新的，选中态随之丢失；这里按 {@code currentId} 把对应项
     * 重新点亮。找不到（当前页被隐藏且没展开）就保持无高亮，页面本身不动。
     */
    private void syncNavSelection() {
        if (currentId == null) {
            return;
        }
        for (Toggle toggle : navGroup.getToggles()) {
            ToggleButton button = (ToggleButton) toggle;
            if (((NavEntry) button.getUserData()).id().equals(currentId)) {
                button.setSelected(true);
                return;
            }
        }
    }

    private ToggleButton createNavButton(NavEntry entry) {
        Label title = new Label(entry.title());
        title.getStyleClass().add("nav-title");

        Label hint = new Label(entry.hint());
        hint.getStyleClass().add("nav-hint");

        VBox text = new VBox(1, title, hint);
        text.setAlignment(Pos.CENTER_LEFT);

        HBox graphic = new HBox(10, Icons.boxed(entry.id(), 16), text);
        graphic.setAlignment(Pos.CENTER_LEFT);

        ToggleButton button = new ToggleButton();
        button.setGraphic(graphic);
        button.setToggleGroup(navGroup);
        button.setUserData(entry);
        button.getStyleClass().add("nav-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setPadding(new Insets(6, 10, 6, 10));
        button.setFocusTraversable(false);
        button.setOnAction(e -> select(entry));
        return button;
    }

    // ---------------------------------------------------------------- 主区域

    private BorderPane buildMainArea() {
        BorderPane main = new BorderPane();
        main.setTop(buildHeader());
        main.setCenter(content);
        return main;
    }

    private HBox buildHeader() {
        pageTitle.getStyleClass().add("page-title");
        pageSubtitle.getStyleClass().add("page-subtitle");

        VBox text = new VBox(1, pageTitle, pageSubtitle);
        text.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(text, Priority.ALWAYS);

        Region divider = new Region();
        divider.getStyleClass().add("header-divider");

        themeButton.getStyleClass().add("icon-button");
        themeButton.setFocusTraversable(false);
        themeButton.setOnAction(e -> {
            settings.setTheme(ThemeManager.cycle());
            saveQuietly();
        });

        // 关于放在设置左边：两个都是「程序级」入口，放在一起比和主题按钮混着更直觉
        aboutButton.getStyleClass().add("icon-button");
        aboutButton.setGraphic(Icons.boxed("info", 16));
        aboutButton.setTooltip(new Tooltip("关于大力丸"));
        aboutButton.setFocusTraversable(false);
        aboutButton.setOnAction(e -> openAbout());

        settingsButton.getStyleClass().add("icon-button");
        settingsButton.setGraphic(Icons.boxed("settings", 16));
        settingsButton.setTooltip(new Tooltip("界面设置"));
        settingsButton.setFocusTraversable(false);
        settingsButton.setOnAction(e -> openSettings());

        backButton.getStyleClass().add("ghost-button");
        backButton.setGraphic(Icons.boxed("back", 14));
        backButton.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
        backButton.setGraphicTextGap(4);
        backButton.setVisible(false);
        backButton.setManaged(false);
        backButton.setOnAction(e -> finishSettings(false));

        HBox actions = new HBox(6, backButton, themeButton, aboutButton, settingsButton);
        actions.setAlignment(Pos.CENTER_RIGHT);

        HBox header = new HBox(14, text, divider, actions);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("app-header");
        return header;
    }

    private void refreshThemeIcon() {
        String preference = ThemeManager.preference();
        String icon = switch (preference) {
            case ToolSettings.THEME_SYSTEM -> "auto";
            case ToolSettings.THEME_LIGHT -> "sun";
            default -> "moon";
        };
        themeButton.setGraphic(new StackPane(Icons.of(icon)));
        themeButton.setTooltip(new Tooltip(themeTooltip(preference)));
    }

    /** 主题按钮的提示语：说明当前模式与点击后的去向。 */
    private String themeTooltip(String preference) {
        boolean dark = !ToolSettings.THEME_LIGHT.equals(ThemeManager.current());
        return switch (preference) {
            case ToolSettings.THEME_SYSTEM ->
                    "主题：跟随系统（当前" + (dark ? "深色" : "浅色") + "），点击改为浅色";
            case ToolSettings.THEME_LIGHT -> "主题：浅色，点击改为深色";
            default -> "主题：深色，点击恢复跟随系统";
        };
    }

    private void updateHeader(String title, String subtitle) {
        pageTitle.setText(title);
        pageSubtitle.setText(subtitle);
    }

    // ---------------------------------------------------------------- 页面切换

    /** 上次保存的主题名，供启动时装配样式表。 */
    public String theme() {
        return settings.theme();
    }

    private void select(NavEntry entry) {
        currentId = entry.id();
        if (settingsPane != null) {
            content.getChildren().remove(settingsPane);
        }
        setSettingsMode(false);
        updateHeader(entry.title(), entry.hint());

        Node page = pageCache.computeIfAbsent(entry.id(), key -> entry.factory().get());
        if (!content.getChildren().contains(page)) {
            content.getChildren().add(page);
        }
        for (Node child : content.getChildren()) {
            boolean active = child == page;
            child.setVisible(active);
            child.setManaged(active);
        }
    }

    /** 首次显示时选中第一项。 */
    public void selectFirst() {
        ensureSelection();
    }

    /**
     * 只点亮第一项并写好标题，不构建页面。
     *
     * <p>首页（JSON）首次构建要把 RichTextFX 那一套类初始化起来，三百毫秒起步；
     * 窗口先 show、页面随后再建，用户看到的是「立刻出来的窗口」而不是一段白屏。
     */
    public void primeFirstHeader() {
        if (navGroup.getToggles().isEmpty()) {
            return;
        }
        ToggleButton first = (ToggleButton) navGroup.getToggles().get(0);
        NavEntry entry = (NavEntry) first.getUserData();
        updateHeader(entry.title(), entry.hint());
        first.setSelected(true);
    }

    /** 保证当前选中项仍然可见，否则回退到第一个可见项。 */
    private void ensureSelection() {
        if (currentId != null && settings.isVisible(currentId)) {
            return;
        }
        if (navGroup.getToggles().isEmpty()) {
            currentId = null;
            return;
        }
        ToggleButton first = (ToggleButton) navGroup.getToggles().get(0);
        first.setSelected(true);
        select((NavEntry) first.getUserData());
    }

    /** 按标题切换到指定页面，找不到或已隐藏时忽略。 */
    public void showPage(String title) {
        for (Toggle toggle : navGroup.getToggles()) {
            ToggleButton button = (ToggleButton) toggle;
            NavEntry entry = (NavEntry) button.getUserData();
            if (entry.title().equals(title)) {
                button.setSelected(true);
                select(entry);
                return;
            }
        }
    }

    // ---------------------------------------------------------------- 界面设置

    /** 打开「关于」弹窗；版本、Java、系统这些从这里一键复制给反馈时用。 */
    private void openAbout() {
        AboutDialog.show(UiKit.ownerWindow(this));
    }

    private void openSettings() {
        if (getScene() == null) {
            return;
        }
        if (settingsPane == null) {
            settingsPane = new SettingsPane(this::settingItems, this::settingOptions, this::finishSettings);
        }
        settingsReturnId = currentId;
        settingsPane.reload(settings.file().toString());
        content.getChildren().setAll(settingsPane);
        navGroup.selectToggle(null);
        navGroup.getToggles().forEach(toggle -> toggle.setSelected(false));
        setSettingsMode(true);
    }

    /** 设置页占用顶部栏：显示返回按钮，隐藏设置入口。 */
    private void setSettingsMode(boolean on) {
        backButton.setVisible(on);
        backButton.setManaged(on);
        settingsButton.setVisible(!on);
        settingsButton.setManaged(!on);
        if (on) {
            updateHeader("界面设置", "勾选要显示的功能与系统集成，保存后立即生效");
        }
    }

    /** 提供给设置页的配置项快照。 */
    private List<SettingsPane.Item> settingItems() {
        List<SettingsPane.Item> items = new ArrayList<>(ENTRIES.size());
        for (NavEntry entry : ENTRIES) {
            items.add(new SettingsPane.Item(entry.id(), entry.group(), entry.title(),
                    entry.hint(), settings.isVisible(entry.id())));
        }
        return items;
    }

    /** 提供给设置页的系统集成开关。 */
    private List<SettingsPane.Option> settingOptions() {
        boolean supported = ContextMenuIntegration.isSupported();
        boolean win11 = Win11Menu.isWindows11();
        String devNote = "当前是开发模式运行，右键菜单需要在打包后的大力丸.exe 里启用";
        String win11Hint = win11
                ? "。Win11 下它会收在「显示更多选项」里，按 Shift + 右键可直接看到"
                : "";
        // 只有一个动作就平铺，动作多才收成二级菜单——能少点一下就少点一下
        String pdfNote = supported
                ? "写入当前用户注册表（HKCU），不需要管理员权限；覆盖 "
                        + ContextMenuIntegration.extensionSummary(List.of(ContextMenuIntegration.TO_PDF))
                        + "，右键里直接是「"
                        + ContextMenuIntegration.flatMenuText(ContextMenuIntegration.TO_PDF)
                        + "」（就一个动作，不再套子菜单）" + win11Hint
                : devNote;
        String officeNote = supported
                ? "同样只写当前用户注册表；.pdf 上可转的格式多，收成一个「"
                        + ContextMenuIntegration.MENU_TITLE + "」子菜单，展开后是「"
                        + ContextMenuIntegration.submenuSummary(ContextMenuIntegration.PDF_TO_OFFICE)
                        + "」四项" + win11Hint
                : devNote;

        List<SettingsPane.Option> options = new ArrayList<>(3);
        options.add(new SettingsPane.Option(SHELL_PDF_OPTION, "系统集成",
                "右键菜单：Office 转 PDF",
                "在 Word / Excel / PPT 文件上点右键，直接生成 PDF",
                settings.shellPdfMenu(), supported, pdfNote));
        options.add(new SettingsPane.Option(SHELL_OFFICE_OPTION, "系统集成",
                "右键菜单：PDF 转 Office",
                "在 PDF 上点右键，转成 Word / Excel / PPT，或把文字提出来",
                settings.shellOfficeMenu(), supported, officeNote));
        options.add(new SettingsPane.Option(WIN11_MENU_OPTION, "系统集成",
                "Win11：换回经典右键菜单",
                "让本软件的菜单项直接显示在第一层，代价是把整个系统换回 Win10 的长菜单",
                settings.win11ClassicMenu(), win11,
                win11
                        ? "注意：这是全局改动，所有程序的右键都会变成经典长菜单，不只是本软件。"
                                + "只写当前用户的注册表，关掉即恢复 Win11 默认外观；"
                                + "生效需重启一次资源管理器"
                        : "当前系统不是 Windows 11，不需要这项设置"));
        return options;
    }

    /** 设置页结束：保存则写配置并重建导航，随后回到进入前的页面。 */
    private void finishSettings(boolean save) {
        if (save) {
            settingsPane.snapshot().forEach(item -> settings.setVisible(item.id(), item.visible()));

            boolean pdfWanted = Boolean.TRUE.equals(
                    settingsPane.optionSnapshot().get(SHELL_PDF_OPTION));
            boolean pdfChanged = pdfWanted != settings.shellPdfMenu();
            settings.setShellPdfMenu(pdfWanted);

            boolean officeWanted = Boolean.TRUE.equals(
                    settingsPane.optionSnapshot().get(SHELL_OFFICE_OPTION));
            boolean officeChanged = officeWanted != settings.shellOfficeMenu();
            settings.setShellOfficeMenu(officeWanted);

            boolean win11Wanted = Boolean.TRUE.equals(
                    settingsPane.optionSnapshot().get(WIN11_MENU_OPTION));
            boolean win11Changed = win11Wanted != settings.win11ClassicMenu();
            saveQuietly();
            rebuildNav();
            if (pdfChanged) {
                applyShellMenu(List.of(ContextMenuIntegration.TO_PDF),
                        ContextMenuIntegration.TO_PDF, pdfWanted,
                        command -> {
                            settings.setShellPdfCommand(command);
                            saveQuietly();
                        },
                        value -> {
                            settings.setShellPdfMenu(value);
                            saveQuietly();
                        });
            }
            if (officeChanged) {
                applyShellMenu(ContextMenuIntegration.PDF_TO_OFFICE,
                        ContextMenuIntegration.PDF_TO_OFFICE.get(0), officeWanted,
                        command -> {
                            settings.setShellOfficeCommand(command);
                            saveQuietly();
                        },
                        value -> {
                            settings.setShellOfficeMenu(value);
                            saveQuietly();
                        });
            }
            if (win11Changed) {
                if (win11Wanted && !confirmWin11Classic()) {
                    // 看清代价后放弃：开关回滚成关闭，注册表一个字节都不动
                    settings.setWin11ClassicMenu(false);
                    saveQuietly();
                } else {
                    applyWin11Menu(win11Wanted);
                }
            }
        }
        content.getChildren().remove(settingsPane);
        setSettingsMode(false);

        if (settingsReturnId != null && settings.isVisible(settingsReturnId)) {
            ToggleButton button = findNavButton(settingsReturnId);
            if (button != null) {
                button.setSelected(true);
                select((NavEntry) button.getUserData());
                return;
            }
        }
        ensureSelection();
    }

    /**
     * 在后台写/删右键菜单。
     *
     * <p>写注册表要起 {@code reg import} 和一次外壳刷新，放在界面线程上会有明显停顿。
     * 失败时把开关回滚并说明原因，避免出现「开关是开的、菜单却没有」的假象。
     */
    private void applyShellMenu(List<ContextMenuIntegration.MenuVerb> verbs,
                               ContextMenuIntegration.MenuVerb sample,
                               boolean enable,
                               Consumer<String> onWritten,
                               Consumer<Boolean> onRollback) {
        Thread worker = new Thread(() -> {
            try {
                if (enable) {
                    ContextMenuIntegration.install(verbs);
                    String command = ContextMenuIntegration.currentCommand(sample);
                    Platform.runLater(() -> onWritten.accept(command));
                } else {
                    ContextMenuIntegration.uninstall(verbs);
                    Platform.runLater(() -> onWritten.accept(""));
                }
            } catch (Exception e) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                Platform.runLater(() -> {
                    onRollback.accept(!enable);
                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    alert.setTitle("右键菜单");
                    alert.setHeaderText(enable ? "没能写入右键菜单" : "没能移除右键菜单");
                    alert.setContentText(message);
                    if (getScene() != null) {
                        alert.initOwner(getScene().getWindow());
                    }
                    alert.showAndWait();
                });
            }
        }, "shell-menu-worker");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * 开启经典右键菜单前确认一次。
     *
     * <p>这个开关换掉的是整个系统的菜单样式，不只是本软件那一项；而设置页里它只是
     * 一行小字，很容易被理解成「让我的菜单项显示出来」。动手之前把代价摊开说一遍，
     * 用户点头再写注册表。
     */
    private boolean confirmWin11Classic() {
        ButtonType confirm = new ButtonType("换回经典菜单", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "", confirm, cancel);
        alert.setTitle("右键菜单");
        alert.setHeaderText("这会把整个系统的右键菜单换回 Win10 样式");
        alert.setContentText(
                "影响范围是全局的：所有程序的右键都会变成经典长菜单，不只是本软件，"
                        + "你装的其他右键扩展也会跟着换位置。\n\n"
                        + "只写当前用户的注册表，不需要管理员权限，随时关掉这个开关就能恢复"
                        + " Win11 默认外观。保存后要重启一次资源管理器才会生效，已打开的"
                        + "文件夹窗口会关闭，正在运行的程序不受影响。");
        if (getScene() != null) {
            alert.initOwner(getScene().getWindow());
        }
        // 默认按钮放在「取消」上：影响全局的改动不该一下回车就生效
        Button confirmButton = (Button) alert.getDialogPane().lookupButton(confirm);
        confirmButton.setDefaultButton(false);
        Button cancelButton = (Button) alert.getDialogPane().lookupButton(cancel);
        cancelButton.setDefaultButton(true);
        return alert.showAndWait().filter(confirm::equals).isPresent();
    }

    /**
     * 在后台写/删「经典右键菜单」开关。
     *
     * <p>改注册表只是第一步：外壳只在启动时读这个标记，所以写完还要问一次
     * 要不要重启资源管理器。重启会关掉已打开的文件夹窗口，因此必须由用户点头。
     */
    private void applyWin11Menu(boolean enable) {
        Thread worker = new Thread(() -> {
            try {
                Win11Menu.setEnabled(enable);
                Platform.runLater(() -> {
                    settings.setWin11ClassicMenu(enable);
                    saveQuietly();
                    offerExplorerRestart(enable);
                });
            } catch (Exception e) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                Platform.runLater(() -> {
                    settings.setWin11ClassicMenu(!enable);
                    saveQuietly();
                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    alert.setTitle("右键菜单");
                    alert.setHeaderText(enable ? "没能开启经典右键菜单" : "没能恢复默认右键菜单");
                    alert.setContentText(message);
                    if (getScene() != null) {
                        alert.initOwner(getScene().getWindow());
                    }
                    alert.showAndWait();
                });
            }
        }, "win11-menu-worker");
        worker.setDaemon(true);
        worker.start();
    }

    /** 问用户要不要立刻重启资源管理器，让刚写入的菜单设置生效。 */
    private void offerExplorerRestart(boolean enabled) {
        ButtonType nowButton = new ButtonType("立即重启", ButtonBar.ButtonData.OK_DONE);
        ButtonType laterButton = new ButtonType("稍后手动重启", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "", nowButton, laterButton);
        alert.setTitle("右键菜单");
        alert.setHeaderText(enabled ? "经典右键菜单已开启" : "已恢复 Windows 11 默认右键菜单");
        alert.setContentText("重启一次资源管理器才会生效。"
                + "已打开的文件夹窗口会被关掉，正在运行的程序不受影响。");
        if (getScene() != null) {
            alert.initOwner(getScene().getWindow());
        }
        alert.showAndWait().filter(nowButton::equals).ifPresent(choice -> {
            Thread worker = new Thread(() -> {
                try {
                    Win11Menu.restartExplorer();
                } catch (IOException e) {
                    Platform.runLater(() -> {
                        Alert failure = new Alert(Alert.AlertType.WARNING);
                        failure.setTitle("右键菜单");
                        failure.setHeaderText("没能重启资源管理器");
                        failure.setContentText("请打开任务管理器，结束「Windows 资源管理器」后重新启动它。"
                                + (e.getMessage() == null ? "" : "\n\n" + e.getMessage()));
                        failure.showAndWait();
                    });
                }
            }, "explorer-restart");
            worker.setDaemon(true);
            worker.start();
        });
    }

    // ---------------------------------------------------------------- 导航辅助

    private ToggleButton findNavButton(String id) {
        for (Toggle toggle : navGroup.getToggles()) {
            ToggleButton button = (ToggleButton) toggle;
            NavEntry entry = (NavEntry) button.getUserData();
            if (entry.id().equals(id)) {
                return button;
            }
        }
        return null;
    }

    private void saveQuietly() {
        try {
            settings.save();
        } catch (IOException e) {
            System.err.println("保存界面设置失败：" + e.getMessage());
        }
    }
}
