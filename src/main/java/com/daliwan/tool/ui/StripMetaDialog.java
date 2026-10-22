package com.daliwan.tool.ui;

import com.daliwan.tool.core.ImageMetaService;
import com.daliwan.tool.core.ImageMetaStripper;
import com.daliwan.tool.util.FileNames;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * 「去除图片元信息」对话框。
 *
 * <p>三步走：选处理策略 → 选输出位置 → 点开始。默认输出到原图旁边，
 * <b>原始文件一律不改动</b>，避免误操作把素材本身覆盖掉。
 *
 * <p>批量时逐个处理并逐条回显结果，某项失败不影响其余项——隐私清理常常是
 * 拖进来一堆图，其中夹着 HEIC、RAW 这类解不了的格式，整体中断反而更难用。
 */
public final class StripMetaDialog {

    private final Stage stage = new Stage();
    private final List<Path> files;
    private final Consumer<List<Path>> onFinished;

    private final VBox resultBox = new VBox(6);
    private final ScrollPane resultScroll = new ScrollPane(resultBox);
    private final ProgressBar progress = new ProgressBar(0);
    private final Label summary = new Label();
    private final CheckBox keepIcc = new CheckBox("保留 ICC 色彩配置");
    private final ToggleGroup orientationGroup = new ToggleGroup();
    private final RadioButton sameFolder = new RadioButton("保存到原图所在文件夹，文件名加 _clean");
    private final RadioButton customFolder = new RadioButton("保存到指定文件夹");
    private final TextField folderField = new TextField();
    private final Button browseButton = new Button("选择…");
    private final Button openFolderButton = new Button("打开输出目录");
    private final Button startButton = new Button("开始处理");

    private final List<Path> outputs = Collections.synchronizedList(new ArrayList<>());
    private int failed;

    private StripMetaDialog(Window owner, List<Path> files, Consumer<List<Path>> onFinished) {
        this.files = List.copyOf(files);
        this.onFinished = onFinished;

        resultBox.setPadding(new Insets(2, 6, 2, 2));
        resultScroll.setFitToWidth(true);
        resultScroll.getStyleClass().add("plain-scroll");
        resultScroll.setMinHeight(110);
        resultScroll.setPrefHeight(170);
        Label placeholder = UiKit.hint("处理结果会显示在这里");
        placeholder.setPadding(new Insets(6, 0, 0, 2));
        resultBox.getChildren().add(placeholder);

        VBox root = new VBox(12,
                buildHeader(),
                buildFileCard(),
                buildOptionCard(),
                buildOutputCard(),
                resultScroll,
                buildFooter());
        root.getStyleClass().add("app-root");
        root.setPadding(new Insets(16, 18, 14, 18));
        VBox.setVgrow(resultScroll, Priority.ALWAYS);

        Scene scene = new Scene(root, 620, 660);
        if (owner != null && owner.getScene() != null) {
            scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        stage.setScene(scene);
        stage.setTitle("去除图片元信息");
        stage.setMinWidth(560);
        stage.setMinHeight(600);
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.initModality(Modality.APPLICATION_MODAL);
    }

    /**
     * 打开对话框。
     *
     * @param owner      父窗口
     * @param files      待处理的图片
     * @param onFinished 全部处理完后回调成功的输出文件列表
     */
    public static void show(Window owner, List<Path> files, Consumer<List<Path>> onFinished) {
        if (files == null || files.isEmpty()) {
            return;
        }
        new StripMetaDialog(owner, files, onFinished).stage.show();
    }

    // ---------------------------------------------------------------- 布局

    private Node buildHeader() {
        Label title = new Label("去除图片元信息");
        title.getStyleClass().add("dialog-title");
        TextFlow hint = wrapHint("照片里的 EXIF / GPS / 软件名会泄露拍摄设备、时间和位置。"
                + "这里直接从文件结构里剪掉这些内容，像素保持不变，原图不会被修改。");
        return column(4, title, hint);
    }

    private Node buildFileCard() {
        VBox card = new VBox(6);
        card.getStyleClass().add("card");
        Label title = new Label(files.size() == 1 ? "待处理文件" : "待处理文件（" + files.size() + " 个）");
        title.getStyleClass().add("card-title");
        card.getChildren().add(title);

        int shown = Math.min(4, files.size());
        for (int index = 0; index < shown; index++) {
            card.getChildren().add(fileRow(files.get(index)));
        }
        if (files.size() > shown) {
            card.getChildren().add(UiKit.hint("… 另有 " + (files.size() - shown) + " 个文件"));
        }
        return card;
    }

    private Node fileRow(Path file) {
        Label name = new Label(file.getFileName().toString());
        name.getStyleClass().add("meta-value");
        name.setMinWidth(0);
        name.setTextOverrun(javafx.scene.control.OverrunStyle.CENTER_ELLIPSIS);

        Label meta = new Label(formatSize(file));
        meta.getStyleClass().add("hint");

        Region space = new Region();
        HBox.setHgrow(space, Priority.ALWAYS);

        HBox row = new HBox(10, name, space, meta);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Node buildOptionCard() {
        VBox card = new VBox(8);
        card.getStyleClass().add("card");

        Label title = new Label("处理选项");
        title.getStyleClass().add("card-title");

        keepIcc.setSelected(true);
        TextFlow iccHint = wrapHint("ICC 只描述颜色怎么解释，不含身份信息；去掉会让广色域照片（Adobe RGB / P3）偏色");
        VBox iccBox = column(1, keepIcc, indented(iccHint));

        Label orientationTitle = new Label("EXIF 方向标记");
        orientationTitle.getStyleClass().add("field-label");
        RadioButton keepOrientation = radio("保留方向（推荐）", ImageMetaStripper.OrientationMode.KEEP);
        RadioButton dropOrientation = radio("直接丢弃", ImageMetaStripper.OrientationMode.DROP);
        RadioButton bakeOrientation = radio("摆正像素", ImageMetaStripper.OrientationMode.BAKE);
        keepOrientation.setSelected(true);
        HBox radios = new HBox(16, keepOrientation, dropOrientation, bakeOrientation);
        radios.setAlignment(Pos.CENTER_LEFT);

        TextFlow orientationHint = wrapHint("手机照片的像素是横着存的，靠这个标记转正。"
                + "「保留方向」只留下 26 字节的方向标记；「摆正像素」会把旋转写进像素，但需要重新压缩。");

        card.getChildren().addAll(title, iccBox,
                column(6, orientationTitle, radios, orientationHint));
        return card;
    }

    private RadioButton radio(String text, ImageMetaStripper.OrientationMode mode) {
        RadioButton button = new RadioButton(text);
        button.setToggleGroup(orientationGroup);
        button.setUserData(mode);
        return button;
    }

    private Node buildOutputCard() {
        VBox card = new VBox(8);
        card.getStyleClass().add("card");

        Label title = new Label("保存位置");
        title.getStyleClass().add("card-title");

        ToggleGroup group = new ToggleGroup();
        sameFolder.setToggleGroup(group);
        customFolder.setToggleGroup(group);
        sameFolder.setSelected(true);

        folderField.setPromptText("选择输出文件夹");
        folderField.setDisable(true);
        HBox.setHgrow(folderField, Priority.ALWAYS);
        browseButton.getStyleClass().add("ghost-button");
        browseButton.setDisable(true);
        browseButton.setOnAction(event -> chooseFolder());
        customFolder.selectedProperty().addListener((observable, old, selected) -> {
            folderField.setDisable(!selected);
            browseButton.setDisable(!selected);
        });

        HBox customRow = new HBox(8, folderField, browseButton);
        customRow.setAlignment(Pos.CENTER_LEFT);
        customRow.setPadding(new Insets(0, 0, 0, 22));

        card.getChildren().addAll(title, sameFolder, customFolder, customRow);
        return card;
    }

    private Node buildFooter() {
        summary.getStyleClass().add("status");
        summary.setText("原始文件不会被修改");

        progress.setPrefWidth(120);
        progress.setVisible(false);
        progress.setManaged(false);

        openFolderButton.getStyleClass().add("ghost-button");
        openFolderButton.setVisible(false);
        openFolderButton.setManaged(false);
        openFolderButton.setOnAction(event -> openFirstOutputFolder());

        Button closeButton = new Button("关闭");
        closeButton.getStyleClass().add("ghost-button");
        closeButton.setOnAction(event -> stage.close());

        startButton.getStyleClass().add("primary-button");
        startButton.setDefaultButton(true);
        startButton.setOnAction(event -> start());

        Region space = new Region();
        HBox.setHgrow(space, Priority.ALWAYS);

        HBox bar = new HBox(10, summary, space, progress, openFolderButton, closeButton, startButton);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private Node indented(Node node) {
        if (node instanceof Region region) {
            region.setPadding(new Insets(0, 0, 0, 24));
        }
        return node;
    }

    /**
     * 会换行的说明文字。
     *
     * <p>用 {@link javafx.scene.text.TextFlow} 而不是带 {@code wrapText} 的 {@link Label}：
     * Label 的高度要在父容器分配宽度之后才能算准，首次布局时 VBox 拿到的是「单行高度」，
     * 于是长说明会被压成一行再裁掉；TextFlow 的首选高度本身就是按宽度算出来的，不会踩这个坑。
     */
    private static TextFlow wrapHint(String text) {
        Text content = new Text(text);
        content.getStyleClass().add("hint-text");
        TextFlow flow = new TextFlow(content);
        flow.setMinWidth(0);
        flow.setMaxWidth(Double.MAX_VALUE);
        return flow;
    }

    /**
     * 纵向容器。
     *
     * <p>{@code Region} 的最小宽度默认等于「内容首选宽度」，嵌套在 VBox 里时会被长说明文字
     * 撑开、顶出父容器——表现就是说明文字只显示一行然后被裁掉。这里统一把最小宽度归零，
     * 让父容器能把它压回可用宽度。
     */
    private static VBox column(double spacing, Node... children) {
        VBox box = new VBox(spacing, children);
        box.setMinWidth(0);
        return box;
    }

    // ---------------------------------------------------------------- 动作

    private void chooseFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择输出文件夹");
        Path current = currentOutputFolder();
        if (current != null && Files.isDirectory(current)) {
            chooser.setInitialDirectory(current.toFile());
        }
        java.io.File selected = chooser.showDialog(stage);
        if (selected != null) {
            folderField.setText(selected.getAbsolutePath());
        }
    }

    private void start() {
        Path custom = null;
        if (customFolder.isSelected()) {
            String text = folderField.getText() == null ? "" : folderField.getText().trim();
            if (text.isEmpty()) {
                UiKit.setStatus(summary, "请先选择输出文件夹", true);
                return;
            }
            custom = Path.of(text);
            if (!Files.isDirectory(custom)) {
                UiKit.setStatus(summary, "输出文件夹不存在：" + custom, true);
                return;
            }
        }

        ImageMetaStripper.Options options = new ImageMetaStripper.Options(
                keepIcc.isSelected(), selectedOrientation());
        Path outputFolder = custom;

        startButton.setDisable(true);
        browseButton.setDisable(true);
        progress.setVisible(true);
        progress.setManaged(true);
        progress.setProgress(0);
        resultBox.getChildren().clear();
        outputs.clear();
        failed = 0;
        UiKit.setStatus(summary, "正在处理 " + files.size() + " 个文件…", false);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                for (int index = 0; index < files.size(); index++) {
                    Path file = files.get(index);
                    try {
                        Path target = targetFor(file, outputFolder);
                        ImageMetaStripper.Report report =
                                ImageMetaStripper.strip(file, target, options);
                        outputs.add(report.target());
                        Platform.runLater(() -> appendSuccess(report));
                    } catch (Exception error) {
                        failed++;
                        String message = error.getMessage() == null
                                ? error.getClass().getSimpleName() : error.getMessage();
                        Platform.runLater(() -> appendFailure(file, message));
                    }
                    updateProgress(index + 1, files.size());
                }
                return null;
            }
        };
        progress.progressProperty().bind(task.progressProperty());

        task.setOnSucceeded(event -> {
            progress.progressProperty().unbind();
            progress.setVisible(false);
            progress.setManaged(false);
            startButton.setText("重新处理");
            startButton.setDisable(false);
            browseButton.setDisable(!customFolder.isSelected());
            if (!outputs.isEmpty()) {
                openFolderButton.setVisible(true);
                openFolderButton.setManaged(true);
            }
            if (failed == 0) {
                UiKit.setStatus(summary, "完成：" + outputs.size() + " 个文件已去除元信息", false);
            } else {
                UiKit.setStatus(summary, "完成：" + outputs.size() + " 个成功，" + failed + " 个失败", true);
            }
            if (onFinished != null && !outputs.isEmpty()) {
                onFinished.accept(List.copyOf(outputs));
            }
        });
        task.setOnFailed(event -> {
            progress.progressProperty().unbind();
            progress.setVisible(false);
            progress.setManaged(false);
            startButton.setDisable(false);
            UiKit.setStatus(summary, "处理中断："
                    + (task.getException() == null ? "未知错误" : task.getException().getMessage()), true);
        });

        UiKit.runInBackground(task, "img-meta-strip");
    }

    private ImageMetaStripper.OrientationMode selectedOrientation() {
        Object mode = orientationGroup.getSelectedToggle() == null
                ? null : orientationGroup.getSelectedToggle().getUserData();
        return mode instanceof ImageMetaStripper.OrientationMode value
                ? value : ImageMetaStripper.OrientationMode.KEEP;
    }

    /**
     * 计算输出路径。
     *
     * <p>批量处理时多个源文件同名、又都输出到同一个目录很常见（比如几个相册目录里都有
     * {@code IMG_0001.jpg}），统一交给 {@link FileNames#uniqueTarget} 追加序号，
     * 否则后一个会静默覆盖前一个。
     */
    private Path targetFor(Path file, Path folder) throws IOException {
        Path directory = folder == null ? file.toAbsolutePath().getParent() : folder;
        if (directory == null) {
            throw new IOException("无法确定输出目录：" + file);
        }
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        return FileNames.uniqueTarget(directory, base + "_clean" + extension);
    }

    private void appendSuccess(ImageMetaStripper.Report report) {
        String detail = report.removed().isEmpty()
                ? report.headline()
                : String.join("；", report.removed());
        String size = ImageMetaService.formatSize(report.originalBytes())
                + " → " + ImageMetaService.formatSize(report.targetBytes());
        Label label = new Label("✓ " + report.source().getFileName() + " · " + size
                + "\n    " + detail
                + "\n    → " + report.target());
        label.setWrapText(true);
        label.setMinWidth(0);
        label.setMaxWidth(Double.MAX_VALUE);
        label.getStyleClass().addAll("strip-result", "strip-ok");
        if (!report.warnings().isEmpty()) {
            Tooltip.install(label, new Tooltip(String.join("\n", report.warnings())));
        }
        resultBox.getChildren().add(label);
    }

    private void appendFailure(Path file, String message) {
        Label label = new Label("✕ " + file.getFileName() + " · " + message);
        label.setWrapText(true);
        label.setMinWidth(0);
        label.setMaxWidth(Double.MAX_VALUE);
        label.getStyleClass().addAll("strip-result", "strip-fail");
        resultBox.getChildren().add(label);
    }

    private void openFirstOutputFolder() {
        try {
            Path folder = outputs.isEmpty() ? null : outputs.get(0).getParent();
            if (folder != null) {
                Desktop.getDesktop().open(folder.toFile());
            }
        } catch (IOException | UnsupportedOperationException ignored) {
            // 没有图形文件管理器时静默失败，路径已经显示在结果里
        }
    }

    private Path currentOutputFolder() {
        if (customFolder.isSelected()) {
            String text = folderField.getText();
            return text == null || text.isBlank() ? null : Path.of(text.trim());
        }
        return files.isEmpty() ? null : files.get(0).toAbsolutePath().getParent();
    }

    private static String formatSize(Path file) {
        try {
            return ImageMetaService.formatSize(Files.size(file));
        } catch (IOException e) {
            return "";
        }
    }
}
