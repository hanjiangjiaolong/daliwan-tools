package com.daliwan.tool;

import com.daliwan.tool.core.cli.PdfCommand;
import com.daliwan.tool.core.ipc.InstanceBridge;
import com.daliwan.tool.ui.MainView;
import com.daliwan.tool.ui.ThemeManager;
import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.InputStream;
import java.util.List;

/**
 * JavaFX 应用入口。
 *
 * <p>样式分两层：{@code app.css} 负责结构，主题文件负责配色，
 * 由 {@link ThemeManager} 在场景创建时按偏好（默认跟随系统）装配。
 *
 * <p>窗口初始尺寸按屏幕可用区域的比例计算，而不是写死像素：
 * 1366×768 的笔记本不会顶满屏幕，2K/4K 显示器上也不会缩成一小块，
 * 并保证窗口始终不超出可用区域，避免底部被任务栏压住。
 */
public class DaliwanApplication extends Application {

    /** 初始尺寸占屏幕可用区域的比例。高度比例偏保守，窗口上下留出呼吸空间。 */
    private static final double WIDTH_RATIO = 0.62;
    private static final double HEIGHT_RATIO = 0.78;

    /** 初始尺寸的上下限：低于下限界面太挤，高于上限则显得空旷。 */
    private static final double MIN_WIDTH = 880;
    private static final double MIN_HEIGHT = 600;
    private static final double MAX_WIDTH = 1600;
    private static final double MAX_HEIGHT = 900;

    /** 与屏幕可用区域之间至少留出的边距。 */
    private static final double SCREEN_MARGIN = 80;

    /** 与右键菜单通信的本地通道；只在窗口存活期间有效。 */
    private InstanceBridge bridge;

    @Override
    public void start(Stage stage) {
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();

        double width = clamp(screen.getWidth() * WIDTH_RATIO, MIN_WIDTH, MAX_WIDTH);
        double height = clamp(screen.getHeight() * HEIGHT_RATIO, MIN_HEIGHT, MAX_HEIGHT);
        // 小屏幕上比例值仍可能超出可用区域（例如 1024×600），再收一次
        width = Math.min(width, screen.getWidth() - SCREEN_MARGIN);
        height = Math.min(height, screen.getHeight() - SCREEN_MARGIN);

        MainView root = new MainView();
        Scene scene = new Scene(root, width, height);

        ThemeManager.install(scene, root.theme());

        stage.setTitle("大力丸");
        stage.getIcons().addAll(loadAppIcons());
        stage.setScene(scene);
        // 最小尺寸也按屏幕收缩，否则小屏幕上用户没法定到比它还小
        stage.setMinWidth(Math.min(MIN_WIDTH, screen.getWidth() - SCREEN_MARGIN / 2));
        stage.setMinHeight(Math.min(MIN_HEIGHT, screen.getHeight() - SCREEN_MARGIN / 2));
        stage.show();

        root.selectFirst();
        // 窗口开着时，右键请求并到这个实例，但只在后台转：不切页、不前置、不抢焦点，
        // 结果照旧用独立卡片，和程序没开时看到的一模一样
        bridge = InstanceBridge.start(PdfCommand::convertAsync);
    }

    @Override
    public void stop() {
        if (bridge != null) {
            bridge.close();
            bridge = null;
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 加载窗口/任务栏图标。多尺寸都挂上，Windows 会按显示密度挑最清晰的一张。
     * PNG 由 tools/IconGen.java 生成后放入 src/main/resources/icons/。
     */
    private static List<Image> loadAppIcons() {
        List<Image> icons = new java.util.ArrayList<>();
        for (int size : new int[]{16, 24, 32, 48, 64, 128, 256}) {
            String path = "/icons/icon-" + size + ".png";
            try (InputStream in = DaliwanApplication.class.getResourceAsStream(path)) {
                if (in != null) {
                    icons.add(new Image(in));
                }
            } catch (Exception ignored) {
                // 图标缺失不影响主功能，跳过即可
            }
        }
        return icons;
    }

    public static void main(String[] args) {
        // 传类字面量而不是让 launch 反推：后者要用 Class.forName 从调用栈里猜主类，
        // 打进可执行 jar 后容易失败，传类名则没有这个不确定性
        launch(DaliwanApplication.class, args);
    }
}
