import com.daliwan.tool.core.JsonTreeNode;
import com.daliwan.tool.ui.JsonPane;
import com.daliwan.tool.ui.MainView;
import com.daliwan.tool.ui.ThemeManager;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * 界面自检：逐个打开页面并截图（临时验证工具，不参与打包）。
 * 用法：java UiCheck <输出目录>
 */
public class UiCheck {

    private static final String PDF_LOG = """
            [2020-01-10 15:00:00] [DEBUG] getUserList.debug(159) - ==> Preparing: \
            select user_id, user_name from t_user where delete_flag = ? and status = ? \
            order by created_date desc
            [2020-01-10 15:00:00] [DEBUG] getUserList.debug(159) - ==> Parameters: N(String), 01(String)""";

    private static final String SAMPLE = """
            {"code":0,"message":"ok","data":{"total":2,"page":1,"list":[\
            {"id":1001,"name":"张三","tags":["admin","dev"],"active":true,"score":98.5,"remark":null},\
            {"id":1002,"name":"李四","tags":[],"active":false,"score":76,"remark":"试用期"}]}}""";

    /** 表格视图用：接口常见的包裹结构，真正的数组藏在 data.list 里，验证自动定位。 */
    private static final String TABLE_SAMPLE = """
            {"code":0,"message":"ok","data":{"total":3,"page":1,"list":[\
            {"id":1001,"name":"张三","tags":["admin","dev"],"active":true,"score":98.5},\
            {"id":1002,"name":"李四","tags":[],"active":false,"score":76,"remark":"试用期"},\
            {"id":1003,"name":"王五","active":true,"owner":{"team":"平台组"}}]}}""";

    /** 画一张带内容的 PNG，用来验证预览与编码。 */
    private static byte[] sampleImage() throws Exception {
        java.awt.image.BufferedImage image =
                new java.awt.image.BufferedImage(320, 200, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = image.createGraphics();
        g.setPaint(new java.awt.GradientPaint(0, 0, new java.awt.Color(0x2F6FED),
                320, 200, new java.awt.Color(0x9FE1CB)));
        g.fillRect(0, 0, 320, 200);
        g.setColor(java.awt.Color.WHITE);
        g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 30));
        g.drawString("大力丸", 82, 112);
        g.dispose();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    public static void main(String[] args) throws Exception {
        String outDir = args.length > 0 ? args[0] : "build/verify";
        new File(outDir).mkdirs();
        byte[] sample = sampleImage();

        CountDownLatch latch = new CountDownLatch(1);
        Platform.startup(() -> {
            try {
                Stage stage = new Stage();
                MainView root = new MainView();
                Scene scene = new Scene(root, 1200, 800);
                ThemeManager.install(scene, root.theme());
                stage.setScene(scene);
                stage.show();
                root.selectFirst();

                List<Runnable> steps = new ArrayList<>();
                steps.add(() -> {
                    root.showPage("JSON 工具");
                    JsonPane pane = find(scene.getRoot(), JsonPane.class);
                    if (pane != null) {
                        pane.setContent(SAMPLE);
                        pane.buildNow();
                    }
                    System.out.println("[check] json pane = " + (pane != null));
                });
                steps.add(() -> shot(scene, outDir + "/json-tree.png"));

                // 树视图删除：等价于右键「删除此节点」，走同一段逻辑（跳过确认框）
                steps.add(() -> {
                    JsonPane pane = find(scene.getRoot(), JsonPane.class);
                    if (pane != null) {
                        boolean removed = pane.deletePathNow(java.util.List.of(2, 2, 0));
                        String content = pane.content().replace("\n", "").replace(" ", "");
                        System.out.println("[check] delete ok=" + removed
                                + " has1001=" + content.contains("1001")
                                + " has1002=" + content.contains("1002"));
                    }
                });
                steps.add(() -> shot(scene, outDir + "/json-tree-deleted.png"));

                // 右键菜单项：根节点不能删，普通节点可以
                steps.add(() -> {
                    JsonPane pane = find(scene.getRoot(), JsonPane.class);
                    TreeView<?> tree = find(scene.getRoot(), TreeView.class);
                    if (pane != null && tree != null && tree.getRoot() != null) {
                        TreeItem<?> rootItem = tree.getRoot();
                        System.out.println("[check] menu root="
                                + pane.menuLabels((JsonTreeNode) rootItem.getValue()));
                        TreeItem<?> node = rootItem.getChildren().get(2)
                                .getChildren().get(2).getChildren().get(0);
                        System.out.println("[check] menu node="
                                + pane.menuLabels((JsonTreeNode) node.getValue()));
                    }
                });

                steps.add(() -> {
                    root.showPage("JSON 工具");
                    JsonPane pane = find(scene.getRoot(), JsonPane.class);
                    if (pane != null) {
                        pane.setContent(TABLE_SAMPLE);
                        pane.tableNow();
                    }
                });
                // 转换在后台线程，行数要等下一步（700ms 后）才有值
                steps.add(() -> {
                    shot(scene, outDir + "/json-table.png");
                    JsonPane pane = find(scene.getRoot(), JsonPane.class);
                    if (pane != null) {
                        System.out.println("[check] table rows=" + pane.tableRowCount()
                                + " columns=" + pane.tableColumns()
                                + " source=" + pane.tableSource());
                    }
                });

                steps.add(() -> {
                    root.showPage("Base64");
                    com.daliwan.tool.ui.Base64Pane page =
                            find(scene.getRoot(), com.daliwan.tool.ui.Base64Pane.class);
                    if (page != null) {
                        page.showImageModeNow();
                    }
                    com.daliwan.tool.ui.ImageBase64Pane pane =
                            find(scene.getRoot(), com.daliwan.tool.ui.ImageBase64Pane.class);
                    if (pane != null) {
                        pane.encodeBytes(sample, "sample.png");
                        System.out.println("[check] img-base64 output=" + pane.outputText().length()
                                + " chars, " + pane.statusText()
                                + ", imageMode=" + (page != null && page.imageMode()));
                    }
                });
                steps.add(() -> shot(scene, outDir + "/img-base64.png"));

                steps.add(() -> {
                    com.daliwan.tool.ui.ImageBase64Pane pane =
                            find(scene.getRoot(), com.daliwan.tool.ui.ImageBase64Pane.class);
                    if (pane != null) {
                        pane.decodeTextNow(pane.outputText());
                        System.out.println("[check] decoded=" + pane.decodedSize()
                                + " bytes, ext=" + pane.decodedExtension());
                    }
                });
                steps.add(() -> shot(scene, outDir + "/img-base64-decode.png"));

                steps.add(() -> {
                    com.daliwan.tool.ui.Base64Pane page =
                            find(scene.getRoot(), com.daliwan.tool.ui.Base64Pane.class);
                    if (page != null) {
                        System.out.println("[check] base64 text="
                                + page.encodeNow("大力丸 本地工具箱"));
                    }
                });
                steps.add(() -> shot(scene, outDir + "/base64-text.png"));

                steps.add(() -> root.showPage("UUID · 随机串"));
                steps.add(() -> shot(scene, outDir + "/random-uuid.png"));

                steps.add(() -> {
                    root.showPage("图片信息");
                });
                steps.add(() -> shot(scene, outDir + "/img-meta.png"));

                steps.add(() -> root.showPage("Office 转 PDF"));
                steps.add(() -> shot(scene, outDir + "/office-to-pdf.png"));

                steps.add(() -> {
                    root.showPage("MyBatis SQL");
                    com.daliwan.tool.ui.SqlPane pane = find(scene.getRoot(), com.daliwan.tool.ui.SqlPane.class);
                    if (pane != null) {
                        pane.setLog(PDF_LOG);
                    }
                    System.out.println("[check] sql pane = " + (pane != null));
                });
                steps.add(() -> shot(scene, outDir + "/mybatis-sql.png"));

                steps.add(() -> {
                    System.out.println("[done]");
                    Platform.exit();
                    latch.countDown();
                });

                runSteps(steps, 0);
            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.exit();
                latch.countDown();
            }
        });
        latch.await();
        System.exit(0);
    }

    private static void runSteps(List<Runnable> steps, int index) {
        if (index >= steps.size()) {
            return;
        }
        steps.get(index).run();
        PauseTransition pause = new PauseTransition(Duration.millis(700));
        pause.setOnFinished(e -> runSteps(steps, index + 1));
        pause.play();
    }

    private static void shot(Scene scene, String file) {
        try {
            WritableImage snap = scene.snapshot(null);
            PixelReader reader = snap.getPixelReader();
            int w = (int) snap.getWidth();
            int h = (int) snap.getHeight();
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    out.setRGB(x, y, reader.getArgb(x, y));
                }
            }
            ImageIO.write(out, "png", new File(file));
            System.out.println("[shot] " + file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static <T extends Node> T find(Node node, Class<T> type) {
        if (type.isInstance(node)) {
            return type.cast(node);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                T found = find(child, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
