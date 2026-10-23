package com.daliwan.tool.ui;

import com.daliwan.tool.core.win.WindowsUpdateGuard;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/**
 * Windows 更新开关页：一键阻止自动更新，也一键恢复。
 *
 * <p>这一页是本项目唯一会改动系统设置的功能，所以把话说在前面：
 * <ul>
 *   <li>需要管理员权限——点按钮会弹 UAC，同意了才生效，程序不会偷偷提权；</li>
 *   <li>阻止之后系统不再下载安装任何更新，**包含安全补丁**，风险由使用者承担；</li>
 *   <li>只动三处（更新服务、更新策略、更新相关的计划任务），随时可以一键恢复；</li>
 *   <li>Windows 大版本升级可能把这些改回去，界面上会显示「只关了一半」，再点一次即可。</li>
 * </ul>
 *
 * <p>改系统这件事本身在 {@code core.win.WindowsUpdateGuard} 里，
 * 这一页只负责取值、调用和把结果说清楚。
 */
public class UpdateGuardPane extends VBox {

    private static final String INTRO =
            "阻止后 Windows 将不再自动下载和安装更新，包括安全补丁——长期关闭会让系统暴露在已知漏洞里，"
                    + "建议偶尔手动检查一次。本功能只改三处：Windows Update 相关服务的启动类型、"
                    + "「不自动更新」策略、以及更新相关的计划任务（含微软的自我修复任务，漏掉它几天后会被改回去）。"
                    + "随时可以点「恢复自动更新」还原，不会删文件、不影响已装的更新。";

    private static final String ELEVATION_NOTE =
            "改这些需要管理员权限：点按钮后系统会弹 UAC 让你确认，同意才会生效；"
                    + "不弹、或在 UAC 里点「否」，则什么都不会改。";

    private final Label stateTitle = new Label("读取中…");
    private final Label stateDetail = new Label();
    private final Label statusLabel = new Label();
    private final Button blockButton = new Button("阻止自动更新");
    private final Button allowButton = new Button("恢复自动更新");
    private final Button refreshButton = new Button("刷新状态");

    /** 是否有一次操作正在跑（查询或改设置）。 */
    private boolean busy;

    public UpdateGuardPane() {
        setSpacing(12);
        setPadding(new Insets(18));

        boolean supported = WindowsUpdateGuard.isSupported();
        blockButton.getStyleClass().add("primary-button");
        allowButton.getStyleClass().add("ghost-button");
        refreshButton.getStyleClass().add("ghost-button");
        blockButton.setOnAction(event -> run(WindowsUpdateGuard.Action.BLOCK));
        allowButton.setOnAction(event -> run(WindowsUpdateGuard.Action.ALLOW));
        refreshButton.setOnAction(event -> refresh());
        blockButton.setDisable(!supported);
        allowButton.setDisable(!supported);
        refreshButton.setDisable(!supported);

        stateTitle.getStyleClass().add("status");
        stateDetail.getStyleClass().add("status-warn");
        statusLabel.getStyleClass().add("status");

        getChildren().addAll(noteCard(), stateCard(), buildActions(), buildFooter());

        if (supported) {
            setStatus("正在读取当前状态…", false);
            refresh();
        } else {
            stateTitle.setText("不可用");
            stateDetail.setText("这个功能只在 Windows 10 / 11 上能用");
            setStatus("当前系统不是 Windows，更新开关不可用", true);
        }
    }

    /** 说明卡片：风险、作用范围与权限，全部写在按钮之前。 */
    private VBox noteCard() {
        VBox card = new VBox(6);
        card.getStyleClass().add("card");

        Label title = new Label("关于这个开关");
        title.getStyleClass().add("card-title");

        card.getChildren().addAll(title, flow(INTRO, "note-text"),
                flow(ELEVATION_NOTE, "note-text-dim"));
        return card;
    }

    /** 当前状态卡片。 */
    private VBox stateCard() {
        VBox card = new VBox(6);
        card.getStyleClass().add("card");

        Label title = new Label("当前状态");
        title.getStyleClass().add("card-title");

        stateDetail.setWrapText(false);
        card.getChildren().addAll(title, stateTitle, stateDetail);
        return card;
    }

    private HBox buildActions() {
        Region spacer = UiKit.spacer();
        HBox bar = new HBox(8, blockButton, allowButton, spacer, refreshButton);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private HBox buildFooter() {
        statusLabel.setWrapText(true);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        HBox bar = new HBox(8, statusLabel);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    /** 只读刷新：不需要管理员，慢一点也无妨。 */
    private void refresh() {
        if (busy) {
            return;
        }
        busy = true;
        setButtonsDisabled(true);
        Thread worker = new Thread(() -> {
            WindowsUpdateGuard.State state = WindowsUpdateGuard.state();
            Platform.runLater(() -> {
                renderState(state);
                setStatus("状态已刷新", false);
                finishing();
            });
        }, "update-guard-query");
        worker.setDaemon(true);
        worker.start();
    }

    /** 阻止或恢复；没有管理员权限时弹 UAC，由提权后的新进程完成实际改动。 */
    private void run(WindowsUpdateGuard.Action action) {
        if (busy || action == null) {
            return;
        }
        boolean block = action == WindowsUpdateGuard.Action.BLOCK;
        busy = true;
        setButtonsDisabled(true);
        setStatus(block ? "正在阻止自动更新…" : "正在恢复自动更新…", false);

        Thread worker = new Thread(() -> {
            WindowsUpdateGuard.Outcome outcome;
            if (WindowsUpdateGuard.isElevated()) {
                outcome = WindowsUpdateGuard.apply(action);
            } else {
                setStatusLater("等待管理员授权（UAC 弹窗）…", false);
                boolean launched = WindowsUpdateGuard.relaunchElevated(action);
                outcome = launched
                        ? new WindowsUpdateGuard.Outcome(true, block
                                ? "已阻止自动更新" : "已恢复自动更新")
                        : new WindowsUpdateGuard.Outcome(false,
                                "没能拿到管理员权限（UAC 被取消或已被系统策略禁止）");
            }
            WindowsUpdateGuard.State state = WindowsUpdateGuard.state();
            Platform.runLater(() -> {
                renderState(state);
                setStatus(outcome.message(), !outcome.ok());
                finishing();
            });
        }, "update-guard-apply");
        worker.setDaemon(true);
        worker.start();
    }

    /** 把查询结果画到状态卡片上。 */
    private void renderState(WindowsUpdateGuard.State state) {
        stateTitle.setText(state.summary());
        stateTitle.getStyleClass().setAll(switch (state.mode()) {
            case BLOCKED -> "status";
            case ALLOWED -> "status";
            case PARTIAL -> "status-warn";
            case UNKNOWN -> "status-warn";
        });
        stateDetail.setText(detailOf(state));
    }

    /** 状态明细：服务启动类型、策略值、计划任务情况，各一项，供排障用。 */
    private String detailOf(WindowsUpdateGuard.State state) {
        if (state.services().isEmpty()) {
            return "没读到服务状态";
        }
        StringBuilder text = new StringBuilder();
        String start = state.services().get(WindowsUpdateGuard.WUAUSERV);
        text.append("更新服务 wuauserv：").append(start == null ? "未知" : start);
        text.append("　·　策略 NoAutoUpdate：")
                .append(state.noAutoUpdate() == null ? "未设置" : (state.noAutoUpdate() ? "1（已关闭自动更新）" : "0"));
        if (!state.tasks().isEmpty()) {
            long disabled = state.tasks().values().stream()
                    .filter(value -> "disabled".equalsIgnoreCase(value)).count();
            text.append("　·　更新计划任务 ").append(disabled).append("/").append(state.tasks().size()).append(" 已停用");
        }
        return text.toString();
    }

    private void setStatusLater(String message, boolean error) {
        Platform.runLater(() -> setStatus(message, error));
    }

    private void finishing() {
        busy = false;
        setButtonsDisabled(false);
    }

    private void setButtonsDisabled(boolean disabled) {
        boolean supported = WindowsUpdateGuard.isSupported();
        blockButton.setDisable(disabled || !supported);
        allowButton.setDisable(disabled || !supported);
        refreshButton.setDisable(disabled || !supported);
    }

    private void setStatus(String message, boolean error) {
        UiKit.setStatus(statusLabel, message, error);
    }

    /**
     * 会换行的说明文字。
     *
     * <p>用 {@link TextFlow} 而不是带 {@code wrapText} 的 {@link Label}：
     * 后者在 VBox 里首次布局只按单行算高度，长文会被裁掉。
     * Text 的颜色必须显式给（{@code -fx-fill}），否则深色主题下看不到。
     */
    private static TextFlow flow(String text, String styleClass) {
        Text node = new Text(text);
        node.getStyleClass().add(styleClass);
        TextFlow flow = new TextFlow(node);
        flow.getStyleClass().add("about-flow");
        return flow;
    }
}
