import com.daliwan.tool.ui.MainView;
import com.daliwan.tool.ui.ThemeManager;
import javafx.application.Platform;
import javafx.scene.Scene;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 临时探针：把启动过程拆成「工具包初始化 / 主界面构建 / 场景与样式 / 首页」四段计时。
 *
 * <p>用法：java -cp target/daliwan_tool.jar;target/tools StartupProbe
 */
public class StartupProbe {

    public static void main(String[] args) throws Exception {
        long t0 = System.nanoTime();

        CountDownLatch toolkitReady = new CountDownLatch(1);
        Platform.startup(toolkitReady::countDown);
        toolkitReady.await();
        long t1 = System.nanoTime();

        AtomicReference<MainView> viewRef = new AtomicReference<>();
        CountDownLatch built = new CountDownLatch(1);
        Platform.runLater(() -> {
            viewRef.set(new MainView());
            built.countDown();
        });
        built.await();
        long t2 = System.nanoTime();

        AtomicReference<Scene> sceneRef = new AtomicReference<>();
        CountDownLatch styled = new CountDownLatch(1);
        Platform.runLater(() -> {
            Scene scene = new Scene(viewRef.get(), 1280, 800);
            ThemeManager.install(scene, viewRef.get().theme());
            sceneRef.set(scene);
            styled.countDown();
        });
        styled.await();
        long t3 = System.nanoTime();

        CountDownLatch selected = new CountDownLatch(1);
        Platform.runLater(() -> {
            viewRef.get().selectFirst();
            selected.countDown();
        });
        selected.await();
        long t4 = System.nanoTime();

        System.out.printf("toolkit  %5.0f ms%n", (t1 - t0) / 1e6);
        System.out.printf("mainview %5.0f ms%n", (t2 - t1) / 1e6);
        System.out.printf("scene    %5.0f ms%n", (t3 - t2) / 1e6);
        System.out.printf("first    %5.0f ms%n", (t4 - t3) / 1e6);
        System.out.printf("total    %5.0f ms%n", (t4 - t0) / 1e6);
        Platform.exit();
    }
}
