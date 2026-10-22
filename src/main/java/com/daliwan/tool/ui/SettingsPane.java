package com.daliwan.tool.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 设置页：在右侧内容区内直接展示，不弹独立窗口。
 *
 * <p>两类开关放在一起：上半部分是导航项（决定左侧显示哪些功能），
 * 下半部分是系统集成（例如资源管理器右键菜单），后者不影响导航。
 * 保存后由调用方负责写配置、必要时同步系统状态。
 *
 * <p>整页是一个 {@link StackPane}：底层是正常内容，上面叠一层默认隐藏的确认层
 * （「还原默认值」用），这样确认框能跟着这套配色走，也不必另开窗口。
 */
public class SettingsPane extends StackPane {

    /**
     * 一个可配置的导航项。
     *
     * @param id      稳定标识
     * @param group   分组名
     * @param title   显示名称
     * @param hint    说明
     * @param visible 当前是否显示
     */
    public record Item(String id, String group, String title, String hint, boolean visible) {
    }

    /**
     * 一个系统集成开关。
     *
     * @param id        稳定标识
     * @param group     分组名
     * @param title     显示名称
     * @param hint      说明
     * @param selected  当前是否开启
     * @param available 当前环境是否支持（不支持时置灰）
     * @param note      补充说明，可为空
     */
    public record Option(String id, String group, String title, String hint,
                         boolean selected, boolean available, String note) {
    }

    private final Supplier<List<Item>> source;
    private final Supplier<List<Option>> optionSource;
    private final Consumer<Boolean> onFinish;
    private final VBox page = new VBox();
    private final VBox body = new VBox(2);
    /** 页内确认层：默认隐藏，需要时盖住整页。 */
    private final StackPane overlay = new StackPane();
    private final Map<String, ToggleSwitch> switches = new LinkedHashMap<>();
    private final Map<String, ToggleSwitch> optionSwitches = new LinkedHashMap<>();
    private final Label pathLabel = new Label();
    /** 配置文件路径，还原默认值后要再显示出来。 */
    private String configPath = "";

    /**
     * @param source   提供当前导航配置（每次进入设置页时调用，保证状态最新）
     * @param onFinish 结束回调：true 表示保存，false 表示取消或返回
     */
    public SettingsPane(Supplier<List<Item>> source, Consumer<Boolean> onFinish) {
        this(source, List::of, onFinish);
    }

    /**
     * @param source       提供当前导航配置
     * @param optionSource 提供系统集成开关
     * @param onFinish     结束回调：true 表示保存，false 表示取消或返回
     */
    public SettingsPane(Supplier<List<Item>> source, Supplier<List<Option>> optionSource,
                        Consumer<Boolean> onFinish) {
        this.source = source;
        this.optionSource = optionSource;
        this.onFinish = onFinish;

        getStyleClass().add("settings-page");
        page.setSpacing(0);

        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("plain-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        page.getChildren().addAll(scroll, buildFooter());
        body.setPadding(new Insets(16, 22, 18, 22));

        overlay.getStyleClass().add("confirm-scrim");
        hideConfirm();
        // 点遮罩空白处等于取消；卡片自己会把点击吃掉
        overlay.setOnMouseClicked(event -> {
            if (event.getTarget() == overlay) {
                hideConfirm();
            }
        });

        getChildren().addAll(page, overlay);
        addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (overlay.isVisible() && event.getCode() == KeyCode.ESCAPE) {
                hideConfirm();
                event.consume();
            }
        });
    }

    // ---------------------------------------------------------------- 布局

    private HBox buildFooter() {
        pathLabel.getStyleClass().add("settings-path");
        pathLabel.setWrapText(true);
        HBox.setHgrow(pathLabel, Priority.ALWAYS);

        Button resetButton = new Button("恢复默认");
        resetButton.getStyleClass().add("ghost-button");
        resetButton.setTooltip(new Tooltip("导航项全部显示、系统集成两项关闭；保存后生效，不影响主题"));
        resetButton.setOnAction(e -> showResetConfirm());

        Button cancelButton = new Button("取消");
        cancelButton.getStyleClass().add("ghost-button");
        cancelButton.setOnAction(e -> onFinish.accept(false));

        Button saveButton = new Button("保存");
        saveButton.getStyleClass().add("primary-button");
        saveButton.setOnAction(e -> {
            if (!switches.isEmpty() && switches.values().stream().noneMatch(ToggleSwitch::isSelected)) {
                pathLabel.setText("至少要保留一项功能");
                pathLabel.getStyleClass().setAll("settings-path", "status-error");
                return;
            }
            onFinish.accept(true);
        });

        HBox footer = new HBox(10, pathLabel, resetButton, cancelButton, saveButton);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.getStyleClass().add("settings-footer");
        return footer;
    }

    /** 起一个新分组：往页面里加组标题与卡片，返回卡片容器供追加行。 */
    private VBox startGroup(String name, boolean first) {
        Label group = new Label(name);
        group.getStyleClass().add("settings-group");
        VBox.setMargin(group, new Insets(first ? 4 : 12, 0, 4, 8));
        body.getChildren().add(group);

        VBox card = new VBox();
        card.getStyleClass().add("settings-card");
        body.getChildren().add(card);
        return card;
    }

    // ---------------------------------------------------------------- 数据

    /** 重新读取配置并重建列表，每次进入设置页时调用。 */
    public void reload(String configPath) {
        // 上次可能停在确认层上（比如从侧栏直接切走了页面），重新进入时先收起来
        hideConfirm();
        body.getChildren().clear();
        switches.clear();
        optionSwitches.clear();

        Label intro = new Label("关掉的页面不会消失，会收进左侧底部的「更多」里，随时还能点开；"
                + "底部的「恢复默认」可以一键全部打开。");
        intro.getStyleClass().add("hint");
        VBox.setMargin(intro, new Insets(0, 0, 6, 8));
        body.getChildren().add(intro);

        String lastGroup = null;
        VBox card = null;
        for (Item item : source.get()) {
            if (!item.group().equals(lastGroup)) {
                card = startGroup(item.group(), lastGroup == null);
                lastGroup = item.group();
            }
            card.getChildren().add(buildRow(item, card.getChildren().isEmpty()));
        }

        lastGroup = null;
        card = null;
        for (Option option : optionSource.get()) {
            if (!option.group().equals(lastGroup)) {
                card = startGroup(option.group(), lastGroup == null && switches.isEmpty());
                lastGroup = option.group();
            }
            card.getChildren().add(buildOptionRow(option, card.getChildren().isEmpty()));
        }

        // 还原默认值在页脚，跟「取消 / 保存」放在一起，这里不再单独起一组；
        // 「关于」改成顶部按钮的弹窗（AboutDialog），不再占设置页的空间
        this.configPath = configPath;
        pathLabel.setText("配置文件：" + configPath);
        pathLabel.getStyleClass().setAll("settings-path");
    }

    private HBox buildRow(Item item, boolean first) {
        Label name = new Label(item.title());
        name.getStyleClass().add("settings-row-title");
        Label hint = new Label(item.hint());
        hint.getStyleClass().add("settings-row-hint");
        VBox text = new VBox(1, name, hint);
        HBox.setHgrow(text, Priority.ALWAYS);

        ToggleSwitch toggle = new ToggleSwitch(item.visible());
        switches.put(item.id(), toggle);

        HBox row = new HBox(12, text, toggle);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("settings-row");
        if (!first) {
            row.getStyleClass().add("settings-row-separated");
        }
        return row;
    }

    private HBox buildOptionRow(Option option, boolean first) {
        Label name = new Label(option.title());
        name.getStyleClass().add("settings-row-title");
        Label hint = new Label(option.hint());
        hint.getStyleClass().add("settings-row-hint");
        hint.setWrapText(true);

        VBox text = new VBox(1, name, hint);
        if (option.note() != null && !option.note().isBlank()) {
            Label note = new Label(option.note());
            note.getStyleClass().add("settings-row-hint");
            note.setWrapText(true);
            text.getChildren().add(note);
        }
        HBox.setHgrow(text, Priority.ALWAYS);

        ToggleSwitch toggle = new ToggleSwitch(option.selected());
        toggle.setSelected(option.selected());
        toggle.setDisable(!option.available());
        optionSwitches.put(option.id(), toggle);

        HBox row = new HBox(12, text, toggle);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("settings-row");
        if (!first) {
            row.getStyleClass().add("settings-row-separated");
        }
        if (!option.available()) {
            row.setOpacity(0.55);
        }
        return row;
    }

    /**
     * 把所有开关恢复成初始状态：导航项全开、系统集成全关。
     *
     * <p>只改界面，不落盘——和手动拨开关等价，用户仍可以用「取消」退出，
     * 也可以在保存前挑几项再拨回去。写配置与注册表统一走保存那条路径，
     * 这样「开启过右键菜单」这类需要清理的操作不会被绕过。
     */
    public void resetToDefaults() {
        switches.values().forEach(toggle -> toggle.setSelected(true));
        optionSwitches.values().forEach(toggle -> toggle.setSelected(false));
        pathLabel.setText("已还原为默认值，点「保存」后生效 · 配置文件：" + configPath);
        pathLabel.getStyleClass().setAll("settings-path");
    }

    /**
     * 页内确认层。
     *
     * <p>盖在设置页上的一层遮罩 + 卡片：配色与按钮直接复用页面样式，
     * 也不额外多一个窗口（系统 {@code Alert} 是灰底系统字体，和自绘界面放一起很跳）。
     */
    private void showResetConfirm() {
        Label title = new Label("还原默认值");
        title.getStyleClass().add("confirm-title");

        Label message = new Label(
                "导航项会全部显示，两项系统集成开关都会关闭——如果之前开着右键菜单，"
                        + "保存时会一并清掉注册表里对应的项。\n\n"
                        + "这一步只改界面上的开关，点「保存」才真正写入，点「取消」则一切照旧。"
                        + "主题不在这里，不受影响。");
        message.getStyleClass().add("confirm-text");
        message.setWrapText(true);

        Button cancel = new Button("取消");
        cancel.getStyleClass().add("ghost-button");
        cancel.setOnAction(e -> hideConfirm());

        Button confirm = new Button("恢复默认");
        confirm.getStyleClass().add("primary-button");
        confirm.setOnAction(e -> {
            hideConfirm();
            resetToDefaults();
        });

        HBox actions = new HBox(8, cancel, confirm);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox card = new VBox(9, title, message, actions);
        card.getStyleClass().add("confirm-card");
        card.setMaxWidth(408);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        card.setPadding(new Insets(16, 18, 14, 18));
        // 卡片自己的点击不能当成「点了遮罩」
        card.setOnMouseClicked(event -> event.consume());

        overlay.getChildren().setAll(card);
        overlay.setVisible(true);
        overlay.setManaged(true);
        // 焦点落在「取消」上：一次改十几项，误按回车不该生效
        cancel.requestFocus();
    }

    private void hideConfirm() {
        overlay.setVisible(false);
        overlay.setManaged(false);
        overlay.getChildren().clear();
    }

    /** 当前各导航开关状态。 */
    public List<Item> snapshot() {
        List<Item> originals = source.get();
        List<Item> result = new ArrayList<>(originals.size());
        for (Item item : originals) {
            ToggleSwitch toggle = switches.get(item.id());
            boolean visible = toggle == null ? item.visible() : toggle.isSelected();
            result.add(new Item(item.id(), item.group(), item.title(), item.hint(), visible));
        }
        return result;
    }

    /** 当前各系统集成开关状态。 */
    public Map<String, Boolean> optionSnapshot() {
        Map<String, Boolean> values = new LinkedHashMap<>();
        optionSwitches.forEach((id, toggle) -> values.put(id, toggle.isSelected()));
        return values;
    }
}
