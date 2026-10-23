package com.daliwan.tool.ui;

import com.daliwan.tool.core.ImageMetaService;
import com.daliwan.tool.core.ImageMetaStripper;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * 图片信息页：拖入或选择图片，展示概要与 EXIF / IPTC / XMP / GPS 等元数据。
 */
public class ImgMetaPane extends VBox {

    private static final double PREVIEW_WIDTH = 300;

    private final ImageView preview = new ImageView();
    private final Label placeholder = new Label("拖入或选择图片\n支持 JPEG / PNG / GIF / WebP / BMP / TIFF / HEIC …");
    private final VBox metaBox = new VBox(2);
    private final ScrollPane metaScroll = new ScrollPane(metaBox);
    private final Label statusLabel = new Label("尚未选择图片");
    private final Button copyButton = UiKit.copyButton("复制全部", this::currentText);
    private final Button stripButton = new Button("去除元信息…");

    private ImageMetaService.Info current;
    /** 当前展示的文件；{@link ImageMetaService.Info} 里只有字符串路径，批量处理时需要真的 {@link Path}。 */
    private Path currentFile;
    /** 一次拖入／选中的全部图片，用于批量去除元信息。 */
    private List<Path> batch = List.of();

    public ImgMetaPane() {
        setSpacing(12);
        setPadding(new Insets(18));

        placeholder.getStyleClass().add("img-preview-placeholder");
        placeholder.setTextAlignment(TextAlignment.CENTER);
        placeholder.setWrapText(true);
        preview.setPreserveRatio(true);
        preview.setFitWidth(PREVIEW_WIDTH - 24);
        preview.setFitHeight(260);

        StackPane previewBox = new StackPane(preview, placeholder);
        previewBox.getStyleClass().add("img-preview-box");
        previewBox.setPrefWidth(PREVIEW_WIDTH);
        previewBox.setMinWidth(PREVIEW_WIDTH);
        previewBox.setAlignment(Pos.CENTER);

        metaBox.setPadding(new Insets(4, 12, 12, 4));
        metaScroll.setFitToWidth(true);
        metaScroll.getStyleClass().add("plain-scroll");

        SplitPane body = new SplitPane(previewBox, metaScroll);
        body.setDividerPositions(0.26);
        body.getStyleClass().add("json-split");
        VBox.setVgrow(body, Priority.ALWAYS);

        getChildren().addAll(buildToolbar(), body, buildFooter());
        configureDrop();
    }

    // ---------------------------------------------------------------- 布局

    private HBox buildToolbar() {
        Button chooseButton = new Button("选择图片…");
        chooseButton.getStyleClass().add("primary-button");
        chooseButton.setOnAction(e -> chooseFile());

        copyButton.setDisable(true);

        stripButton.getStyleClass().add("ghost-button");
        stripButton.setDisable(true);
        stripButton.setOnAction(e -> openStripDialog());

        Button clearButton = new Button("清空");
        clearButton.getStyleClass().add("ghost-button");
        clearButton.setOnAction(e -> clearAll());

        Region spacer = UiKit.spacer();

        HBox bar = new HBox(8, UiKit.hint("拖入图片即可读取元数据，多张可一次处理"), spacer,
                chooseButton, copyButton, stripButton, clearButton);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private HBox buildFooter() {
        statusLabel.getStyleClass().add("status");
        HBox bar = new HBox(8, statusLabel);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    // ---------------------------------------------------------------- 动作

    private void configureDrop() {
        UiKit.installFileDrop(this, this::applyBatch, null);
    }

    /**
     * 接收一批文件：第一张负责展示，全部作为「去除元信息」的批量队列。
     *
     * <p>没有可处理后缀的文件（如 HEIC）不直接丢弃，仍然展示其元数据，
     * 只是「去除元信息」按钮会置灰——能看不能改，比什么都不显示更好理解。
     */
    private void applyBatch(List<Path> files) {
        List<Path> images = files.stream().filter(ImageMetaStripper::isSupported).toList();
        batch = images.isEmpty() ? List.copyOf(files) : images;
        String note = images.isEmpty() || images.size() == files.size()
                ? null
                : "已跳过 " + (files.size() - images.size()) + " 个不支持去除的格式";
        load(batch.get(0), note);
    }

    private void chooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择图片");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "图片文件",
                "*.jpg", "*.jpeg", "*.png", "*.gif", "*.bmp", "*.webp",
                "*.tif", "*.tiff", "*.heic", "*.heif", "*.ico", "*.psd"));
        List<File> chosen = chooser.showOpenMultipleDialog(
                UiKit.ownerWindow(this));
        if (chosen != null && !chosen.isEmpty()) {
            applyBatch(chosen.stream().map(File::toPath).toList());
        }
    }

    /**
     * 打开去除元信息对话框。
     *
     * <p>处理完把输出文件重新读进来：元数据面板几乎清空，正好用来确认效果。
     */
    private void openStripDialog() {
        List<Path> targets = batch.isEmpty()
                ? (currentFile == null ? List.of() : List.of(currentFile)) : batch;
        if (targets.isEmpty()) {
            return;
        }
        StripMetaDialog.show(UiKit.ownerWindow(this), targets, outputs -> {
            if (outputs.isEmpty()) {
                return;
            }
            batch = List.copyOf(outputs);
            load(outputs.get(0), "已去除 " + outputs.size() + " 张图片的元数据");
        });
    }

    /** 复制按钮取值的来源；放在方法里引用字段，避免字段初始化顺序上的前向引用。 */
    private String currentText() {
        return current == null ? null : ImageMetaService.toText(current);
    }

    /**
     * 读取并展示；失败时在状态栏提示，不清空已有内容。
     *
     * <p>元数据解析放到后台线程：大图的 EXIF / XMP 段可能好几兆，
     * 同步解析会让拖入后的界面卡住一下。
     *
     * @param note 成功时拼在状态文字前面的补充说明，如「已去除 3 张图片的元数据」
     */
    private boolean load(Path file, String note) {
        currentFile = file;
        showPreview(file);
        metaBox.getChildren().clear();
        copyButton.setDisable(true);
        stripButton.setDisable(true);
        statusLabel.getStyleClass().setAll("status");
        statusLabel.setText("正在读取 " + file.getFileName() + " …");

        Task<ImageMetaService.Info> task = new Task<>() {
            @Override
            protected ImageMetaService.Info call() throws Exception {
                return ImageMetaService.read(file);
            }
        };
        task.setOnSucceeded(event -> {
            ImageMetaService.Info info = task.getValue();
            current = info;
            renderSections(info);
            copyButton.setDisable(false);
            stripButton.setDisable(!ImageMetaStripper.isSupported(file));
            statusLabel.getStyleClass().setAll("status");
            int tagCount = info.sections().stream()
                    .mapToInt(s -> s.entries().size()).sum();
            String prefix = note == null ? "" : note + " · ";
            statusLabel.setText(prefix + "已读取 " + info.fileName()
                    + " · " + (info.width() > 0 ? info.width() + " × " + info.height() : "尺寸未知")
                    + " · " + tagCount + " 条元数据");
        });
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            statusLabel.getStyleClass().setAll("status", "status-error");
            statusLabel.setText("读取失败："
                    + (error == null ? "未知错误" : String.valueOf(error.getMessage())));
        });

        UiKit.runInBackground(task, "img-meta-read");
        return true;
    }

    private void showPreview(Path file) {
        placeholder.setVisible(true);
        preview.setImage(null);
        try {
            Image image = new Image(file.toUri().toString(),
                    PREVIEW_WIDTH - 24, 260, true, true, true);
            if (!image.isError()) {
                preview.setImage(image);
                placeholder.setVisible(false);
            }
        } catch (RuntimeException ignored) {
            // 格式不支持预览时只展示元数据
        }
    }

    private void renderSections(ImageMetaService.Info info) {
        metaBox.getChildren().clear();
        for (ImageMetaService.Section section : info.sections()) {
            metaBox.getChildren().add(renderSection(section));
        }
    }

    private VBox renderSection(ImageMetaService.Section section) {
        Label title = new Label(section.name());
        title.getStyleClass().add("section-label");

        VBox rows = new VBox();
        for (ImageMetaService.Entry entry : section.entries()) {
            Label tag = new Label(entry.tag());
            tag.getStyleClass().add("meta-tag");

            Label value = new Label(entry.value());
            value.getStyleClass().add("meta-value");
            value.setWrapText(true);
            HBox.setHgrow(value, Priority.ALWAYS);

            HBox row = new HBox(10, tag, value);
            row.setAlignment(Pos.TOP_LEFT);
            row.getStyleClass().add("meta-row");
            rows.getChildren().add(row);
        }
        VBox box = new VBox(6, title, rows);
        box.getStyleClass().add("meta-section");
        return box;
    }

    private void clearAll() {
        current = null;
        currentFile = null;
        batch = List.of();
        preview.setImage(null);
        placeholder.setVisible(true);
        metaBox.getChildren().clear();
        copyButton.setDisable(true);
        stripButton.setDisable(true);
        statusLabel.getStyleClass().setAll("status");
        statusLabel.setText("尚未选择图片");
    }
}
