package com.daliwan.tool.ui;

import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;

/**
 * 给长文档的滚动条滑块兜一个最小长度。
 *
 * <p>滚动条滑块的长度是按「一屏占全文的比例」算的：一份 1 万行的 JSON 全文高约 16 万像素，
 * 视口只有 580，滑块就是 {@code 580 / 165000 × 580 ≈ 2 px}——轨道还在，滑块却细到看不见，
 * 用户看到的就是「右边根本没有滚动条」。比例本身没错，错在几像素的滑块既没法看也没法拖。
 *
 * <p>JavaFX 没有「滑块最小长度」这个属性：{@code ScrollBarSkin} 在 {@code layoutChildren}
 * 里按 {@code visibleAmount / (max - min)} 算完就直接 {@code resize}，thumb 上的
 * {@code -fx-min-height} 不会被采纳（实测设 28px 仍然是 3px）。唯一能影响它的就是
 * {@code visibleAmount}——而这个值只参与滑块长度的计算，不参与 {@code value} 的映射，
 * 所以把它抬到「至少撑满 MIN_THUMB 像素」是安全的：拖滑块、点轨道、滚轮的行为都不变，
 * 只是滑块比真实比例粗一些。
 *
 * <p>宿主（RichTextFX 的虚拟滚动面板、TableView 的 VirtualFlow）每次布局都会重新
 * {@code setVisibleAmount}，因此不能只设一次，也不能用 {@code bind}（宿主再设会抛异常）。
 * 这里挂一个自纠正的监听器：值被改小就抬回来，改到下限就不再触发。
 */
final class ScrollBars {

    /** 滑块最小长度（像素）。 */
    private static final double MIN_THUMB = 28;

    /** 每帧重试上限：皮肤没建好时下一帧再来，但不允许无限循环。 */
    private static final int MAX_PASSES = 30;

    private static final String MARKED = "daliwan.min-thumb";

    private ScrollBars() {
    }

    /**
     * 给容器里的垂直滚动条装上最小滑块。
     *
     * <p>滚动条要等宿主皮肤建好才进场景树，而皮肤又要等控件第一次参与布局——表格、树视图
     * 初始都是隐藏的，切过去才建皮肤。所以这里不能只查一次：除了逐帧重试，还要在
     * 「进了场景 / 变可见 / 挂上父容器」这几个时机重新来过，否则预算会在皮肤建好前耗光。
     *
     * @param container 装着滚动条的容器（代码框、表格、树）
     */
    static void installMinThumb(Node container) {
        if (Boolean.TRUE.equals(container.getProperties().get(MARKED))) {
            return;
        }
        container.getProperties().put(MARKED, Boolean.TRUE);
        int[] passes = {0};
        Runnable pass = new Runnable() {
            @Override
            public void run() {
                if (container.getScene() == null || ++passes[0] > MAX_PASSES) {
                    return;
                }
                if (!scan(container)) {
                    Platform.runLater(this);
                }
            }
        };
        Runnable restart = () -> {
            passes[0] = 0;
            Platform.runLater(pass);
        };
        container.sceneProperty().addListener((observable, old, value) -> {
            if (value != null) {
                restart.run();
            }
        });
        container.parentProperty().addListener((observable, old, value) -> {
            if (value != null) {
                restart.run();
            }
        });
        container.visibleProperty().addListener((observable, old, value) -> {
            if (value) {
                restart.run();
            }
        });
        Platform.runLater(pass);
    }

    /** 给容器里所有垂直滚动条抬一次滑块；找到任何一条就算成功。 */
    private static boolean scan(Node container) {
        boolean found = false;
        for (Node node : container.lookupAll(".scroll-bar")) {
            if (node instanceof ScrollBar bar && bar.getOrientation() == Orientation.VERTICAL) {
                keepMinThumb(bar);
                found = true;
            }
        }
        return found;
    }

    /** 抬一次，并挂上自纠正监听：宿主每次改回真实比例，这里再抬回来。 */
    private static void keepMinThumb(ScrollBar bar) {
        if (Boolean.TRUE.equals(bar.getProperties().get(MARKED))) {
            return;
        }
        bar.getProperties().put(MARKED, Boolean.TRUE);
        Runnable enforce = () -> {
            double track = bar.getHeight();
            if (track <= 0 || bar.getMax() <= bar.getMin()) {
                return;
            }
            double min = bar.getMax() * MIN_THUMB / track;
            if (bar.getVisibleAmount() < min) {
                bar.setVisibleAmount(min);
            }
        };
        bar.heightProperty().addListener((observable, old, now) -> enforce.run());
        bar.maxProperty().addListener((observable, old, now) -> enforce.run());
        bar.visibleAmountProperty().addListener((observable, old, now) -> enforce.run());
        enforce.run();
    }
}
