package com.daliwan.tool.ui;

import com.daliwan.tool.core.AppInfo;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * 「关于」对话框。
 *
 * <p>内容全部来自 {@link AppInfo}（纯 Java，可单测），这里只负责排版。
 * 比放在设置页底部的好处是：反馈问题时能一键把版本、Java、系统、配置路径整段复制走，
 * 而这些信息一年也看不了两次，不值得占一屏界面。
 *
 * <p>样式沿用 {@link StripMetaDialog} 那套（{@code app-root} + {@code card}），
 * 样式表从主窗口拷贝过来，因此深浅主题自动跟随。
 */
public final class AboutDialog {

    private final Stage stage = new Stage();

    private AboutDialog(Window owner) {
        VBox root = new VBox(13,
                buildHeader(),
                buildEnvCard(),
                buildNoteCard(),
                buildCreditCard(),
                buildFooter());
        root.getStyleClass().add("app-root");
        root.setPadding(new Insets(18, 20, 16, 20));

        // 宽度按内容算过：三列组件清单 + 页脚刚好放得下，再窄就得滚动
        Scene scene = new Scene(root, 580, 620);
        if (owner != null && owner.getScene() != null) {
            scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                stage.close();
            }
        });
        stage.setScene(scene);
        stage.setTitle("关于 " + AppInfo.APP_NAME);
        stage.setMinWidth(460);
        stage.setMinHeight(420);
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.initModality(Modality.APPLICATION_MODAL);
    }

    /** 打开「关于」窗口。 */
    public static void show(Window owner) {
        new AboutDialog(owner).stage.show();
    }

    // ---------------------------------------------------------------- 布局

    private Node buildHeader() {
        ImageView logo = new ImageView(loadLogo());
        logo.setFitWidth(46);
        logo.setFitHeight(46);
        logo.setPreserveRatio(true);

        Label name = new Label(AppInfo.APP_NAME);
        name.getStyleClass().add("about-app-name");
        Label version = new Label("版本 " + AppInfo.version());
        version.getStyleClass().add("about-version");

        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);

        HBox titleRow = new HBox(8, name, version, gap);
        titleRow.setAlignment(Pos.BOTTOM_LEFT);

        Label tagline = new Label(AppInfo.TAGLINE);
        tagline.getStyleClass().add("hint");

        VBox text = new VBox(4, titleRow, tagline);
        text.setAlignment(Pos.CENTER_LEFT);

        HBox header = new HBox(14, logo, text);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private Node buildEnvCard() {
        VBox card = new VBox(7);
        card.getStyleClass().add("card");

        Label title = new Label("运行环境");
        title.getStyleClass().add("card-title");
        card.getChildren().add(title);

        card.getChildren().add(kv("Java", AppInfo.javaRuntime()));
        card.getChildren().add(kv("系统", AppInfo.osDescription()));
        // 路径长且可能含中文，走 TextFlow 换行：Label.setWrapText 在 VBox 里首帧只给单行高
        card.getChildren().add(kv("配置文件", null, AppInfo.configPath()));
        return card;
    }

    private Node buildNoteCard() {
        VBox card = new VBox(6);
        card.getStyleClass().add("card");

        Label title = new Label("关于本程序");
        title.getStyleClass().add("card-title");
        card.getChildren().add(title);

        TextFlow note = wrap("一个纯本地的桌面工具箱：哈希、JSON、编解码、加解密、文档转换、"
                + "图片与 OCR 都在本机完成，不联网、不上传任何数据，也不需要登录。"
                + "配置与日志都写在上面的目录里，删掉即恢复初始状态。");
        card.getChildren().add(note);
        return card;
    }

    private Node buildCreditCard() {
        VBox card = new VBox(6);
        card.getStyleClass().add("card");

        Label title = new Label("第三方组件");
        title.getStyleClass().add("card-title");
        card.getChildren().add(title);

        for (AppInfo.Credit credit : AppInfo.credits()) {
            card.getChildren().add(creditRow(credit));
        }
        return card;
    }

    private Node creditRow(AppInfo.Credit credit) {
        Label name = new Label(credit.name());
        name.getStyleClass().add("about-credit-name");
        // 定宽让用途、许可两列纵向对齐；名字过长时自身截断
        name.setMinWidth(188);
        name.setMaxWidth(188);
        name.setTextOverrun(javafx.scene.control.OverrunStyle.CENTER_ELLIPSIS);

        Label purpose = new Label(credit.purpose());
        purpose.getStyleClass().add("about-credit-use");
        // 用途列吃掉剩余宽度；窗口窄时截断而不是把右端的许可列挤出去
        purpose.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
        HBox.setHgrow(purpose, Priority.ALWAYS);

        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);

        Label license = new Label(credit.license());
        license.getStyleClass().add("about-credit-license");

        HBox row = new HBox(10, name, purpose, gap, license);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Node buildFooter() {
        Button copy = new Button("复制版本信息");
        copy.getStyleClass().add("ghost-button");
        copy.setOnAction(event -> copySummary(copy));

        Button close = new Button("关闭");
        close.getStyleClass().add("primary-button");
        close.setDefaultButton(true);
        close.setOnAction(event -> stage.close());

        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);

        HBox bar = new HBox(10, copy, gap, close);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    // ---------------------------------------------------------------- 细节

    /**
     * 复制排障信息，并把按钮文案短暂改成「已复制」。
     *
     * <p>点完没任何反馈的话，用户不知道到底复制成功没有——剪贴板是看不见的。
     */
    private void copySummary(Button button) {
        ClipboardContent content = new ClipboardContent();
        content.putString(AppInfo.summary());
        Clipboard.getSystemClipboard().setContent(content);

        button.setText("已复制");
        PauseTransition wait = new PauseTransition(Duration.seconds(1.6));
        wait.setOnFinished(event -> button.setText("复制版本信息"));
        wait.play();
    }

    private static HBox kv(String key, String value) {
        return kv(key, value, null);
    }

    /** 键值行；{@code longValue} 非空时改用可换行的文本，用于长路径。 */
    private static HBox kv(String key, String value, String longValue) {
        Label keyLabel = new Label(key);
        keyLabel.getStyleClass().add("about-kv-key");
        keyLabel.setMinWidth(62);

        Node valueNode = longValue == null
                ? valueLabel(value)
                : wrap(longValue);

        HBox row = new HBox(10, keyLabel, valueNode);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(valueNode, Priority.ALWAYS);
        return row;
    }

    private static Label valueLabel(String value) {
        Label label = new Label(value);
        label.getStyleClass().add("about-kv-value");
        return label;
    }

    /**
     * 会换行的说明文字：用 {@link TextFlow} 而不是带 {@code wrapText} 的 {@link Label}，
     * 后者在 VBox 里首次布局只按单行算高度，长文会被裁掉。
     */
    private static TextFlow wrap(String text) {
        Text node = new Text(text);
        node.getStyleClass().add("about-text");
        TextFlow flow = new TextFlow(node);
        flow.getStyleClass().add("about-flow");
        return flow;
    }

    private static Image loadLogo() {
        try (var in = AboutDialog.class.getResourceAsStream("/icons/icon-64.png")) {
            return in == null ? null : new Image(in);
        } catch (java.io.IOException ignored) {
            return null;
        }
    }
}
