package com.daliwan.tool.ui;

import com.daliwan.tool.core.doc.ConversionEngine;
import com.daliwan.tool.core.doc.EngineResolver;
import com.daliwan.tool.core.doc.StirlingEngine;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Office 转 PDF 页：批量把 Word / Excel / PPT 文档转换为 PDF。
 *
 * <p>转换由内置 Stirling 开源引擎（MIT）完成，本页只负责收集文件、
 * 逐个调用并回显结果。右键菜单把文件递进来时也走同一条路径，
 * 这样用户能看到进度，而不是只有一个「已生成」的提示。
 *
 * <p>列表与批量转换的骨架在 {@link BatchConvertPane}，这里只回答两件事：
 * 哪些文件能进列表（与内置引擎的能力范围一致），以及单个文件怎么转。
 */
public class DocConvertPane extends BatchConvertPane {

    /** 可转换的扩展名，与内置引擎保持一致。 */
    private static final Set<String> SUPPORTED = Set.copyOf(StirlingEngine.extensions());

    public DocConvertPane() {
        super("把 Word / Excel / PPT 文件拖进来，或点击右上角「添加文件」");

        Label tip = new Label("PDF 输出到源文件所在目录，同名文件不覆盖；"
                + "设置里可以把「转 PDF」加到资源管理器右键菜单");
        tip.getStyleClass().add("footer");

        getChildren().addAll(buildToolbar(), listView(), tip, buildFooter());
        configureDragAndDrop();

        // 引擎能力说明（含兜底状态），转换前就让用户知道当前用的是什么引擎
        String capability = EngineResolver.describeCapability();
        if (capability != null) {
            setStatus(capability, false);
        }
    }

    private HBox buildToolbar() {
        Button addButton = new Button("添加文件");
        addButton.getStyleClass().add("ghost-button");
        addButton.setOnAction(e -> chooseFiles());

        Button clearButton = new Button("清空列表");
        clearButton.getStyleClass().add("ghost-button");
        clearButton.setOnAction(e -> clearItems());

        Region spacer = UiKit.spacer();

        String engine = EngineResolver.describeAvailable();
        Label engineLabel = new Label(engine == null ? "未检测到转换引擎" : "引擎：" + engine);
        engineLabel.getStyleClass().add(engine == null ? "status-error" : "status");

        HBox bar = new HBox(8, UiKit.hint("支持 Word / Excel / PPT / CSV，可直接把文件拖进列表"),
                spacer, addButton, clearButton, engineLabel);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private void configureDragAndDrop() {
        UiKit.installFileDrop(this, files -> files.forEach(this::addFile), null);
    }

    private void chooseFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择要转换的 Office 文档");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Office 文档",
                StirlingEngine.extensions().stream().map(extension -> "*" + extension).toArray(String[]::new)));
        List<File> files = chooser.showOpenMultipleDialog(
                UiKit.ownerWindow(this));
        if (files != null) {
            files.forEach(file -> addFile(file.toPath()));
        }
    }

    @Override
    protected String rejection(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        String extension = dot > 0 ? name.substring(dot) : "";
        return SUPPORTED.contains(extension) ? null : "跳过不支持的文件：" + file.getFileName();
    }

    @Override
    protected Path convert(Path file) throws Exception {
        // 每个文件单独挑引擎：Word / Excel / PPT 都走内置 Stirling
        ConversionEngine engine = EngineResolver.resolve(file);
        Path target = sibling(file, ".pdf");
        engine.convert(file, target);
        return target;
    }

    @Override
    protected String workerName() {
        return "office-convert-worker";
    }
}
