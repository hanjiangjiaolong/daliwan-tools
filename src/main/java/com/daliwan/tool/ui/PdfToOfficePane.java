package com.daliwan.tool.ui;

import com.daliwan.tool.core.doc.PdfToOfficeConverter;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * PDF 转 Office 页：把 PDF 反解成 Word / Excel / PPT 或纯文本。
 *
 * <p>和「Office 转 PDF」是同一个开源库的两个方向（Stirling Office Convert，MIT），
 * 同样不需要装 Office。区别在于这一页的产出是「可编辑」而不是「可打印」：
 * 段落、标题、列表、表格会被还原成真的 Office 元素，但复杂排版只能近似，
 * 界面上先把这句话说在前面，免得用户拿到的 Word 和想象的不一样。
 *
 * <p>列表与批量转换的骨架在 {@link BatchConvertPane}，这里多一个目标格式下拉，
 * 并按选中的格式决定产出扩展名。
 */
public class PdfToOfficePane extends BatchConvertPane {

    private final ComboBox<PdfToOfficeConverter.Target> targetBox =
            new ComboBox<>(FXCollections.observableArrayList(PdfToOfficeConverter.Target.values()));

    public PdfToOfficePane() {
        super("把 PDF 拖进来，或点击右上角「添加文件」");

        targetBox.getSelectionModel().select(PdfToOfficeConverter.Target.WORD);
        targetBox.setCellFactory(view -> targetCell());
        targetBox.setButtonCell(targetCell());

        Label tip = new Label("输出到源文件所在目录，同名文件不覆盖；"
                + "PDF 转的是可编辑内容，版式只能近似还原");
        tip.getStyleClass().add("footer");
        tip.setWrapText(true);
        HBox.setHgrow(tip, Priority.ALWAYS);

        getChildren().addAll(buildToolbar(), listView(), buildFooter(tip));
        configureDragAndDrop();

        if (!PdfToOfficeConverter.isAvailable()) {
            setStatus("PDF 转 Office 组件不可用（需要 Java 25 及以上运行环境）", true);
        }
    }

    /** 目标格式下拉的单元格：显示「Word（.docx）」这类可读文本。 */
    private ListCell<PdfToOfficeConverter.Target> targetCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(PdfToOfficeConverter.Target target, boolean empty) {
                super.updateItem(target, empty);
                setText(empty || target == null ? null : target.label() + "（." + target.extension() + "）");
            }
        };
    }

    private HBox buildToolbar() {
        Button addButton = new Button("添加文件");
        addButton.getStyleClass().add("ghost-button");
        addButton.setOnAction(e -> chooseFiles());

        Button clearButton = new Button("清空列表");
        clearButton.getStyleClass().add("ghost-button");
        clearButton.setOnAction(e -> clearItems());

        Region spacer = UiKit.spacer();

        HBox bar = new HBox(8, UiKit.hint("转换为"), targetBox,
                spacer, addButton, clearButton);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private void configureDragAndDrop() {
        UiKit.installFileDrop(this, files -> files.forEach(this::addFile), null);
    }

    private void chooseFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择要转换的 PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF 文件", "*.pdf"));
        List<File> files = chooser.showOpenMultipleDialog(
                UiKit.ownerWindow(this));
        if (files != null) {
            files.forEach(file -> addFile(file.toPath()));
        }
    }

    @Override
    protected String rejection(Path file) {
        return PdfToOfficeConverter.isPdf(file) ? null : "只支持 PDF 文件：" + file.getFileName();
    }

    @Override
    protected Path convert(Path file) throws Exception {
        PdfToOfficeConverter.Target target = targetBox.getValue();
        Path output = PdfToOfficeConverter.targetOf(file, target);
        PdfToOfficeConverter.convert(file, output, target);
        return output;
    }

    @Override
    protected boolean readyToConvert() {
        return targetBox.getValue() != null;
    }

    @Override
    protected String workerName() {
        return "pdf-to-office-worker";
    }
}
