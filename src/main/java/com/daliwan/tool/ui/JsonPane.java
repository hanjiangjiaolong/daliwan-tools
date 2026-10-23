package com.daliwan.tool.ui;

import com.daliwan.tool.core.IndentStyle;
import com.daliwan.tool.core.JsonDocument;
import com.daliwan.tool.core.JsonFormatException;
import com.daliwan.tool.core.JsonFormatter;
import com.daliwan.tool.core.JsonTable;
import com.daliwan.tool.core.JsonTreeBuilder;
import com.daliwan.tool.core.JsonTreeNode;
import com.daliwan.tool.core.XlsxExporter;
import com.daliwan.tool.util.ClipboardUtil;
import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON 工具页：格式化 / 压缩、树视图、数组转表格合并为一页，同一块区域在三种形态间切换。
 *
 * <p>文本是唯一数据源：文本态直接格式化 / 压缩（原地改写，可 Ctrl+Z 撤销），
 * 切到树视图即按当前文本解析，不存在两处内容不一致，也不需要单独的「生成树」按钮；
 * 树视图删节点同样改文本再重建，表格视图则从文本里自动找出最像表格的那段数组。
 */
public class JsonPane extends VBox {

    /** 超过该长度则不做实时校验，避免大文本反复解析卡顿。 */
    private static final int REALTIME_LIMIT = 512 * 1024;

    /** 超过此节点数时，展开全部只展开三层，避免界面卡死。 */
    private static final int LARGE_TREE_THRESHOLD = 3000;

    private static final long DROP_FILE_LIMIT = 8L * 1024 * 1024;

    /** 单元格文本多长才给悬停提示——短文本一眼看得全，弹提示反而是打扰。 */
    private static final int TOOLTIP_FROM = 24;

    /** 悬停提示里最多预览多少字符，完整内容在双击弹出的窗口里。 */
    private static final int TOOLTIP_LIMIT = 300;

    private static final String SAMPLE = """
            {"code":0,"message":"ok","data":{"total":2,"page":1,"list":[\
            {"id":1001,"name":"张三","tags":["admin","dev"],"active":true,"score":98.5,"remark":null},\
            {"id":1002,"name":"李四","tags":[],"active":false,"score":76,"remark":"试用期"}]}}""";

    /** 表格视图用的示例：接口常见的包裹结构，数组藏在 data.list 里，正好演示自动定位。 */
    private static final String SAMPLE_ROWS = """
            {"code":0,"message":"ok","data":{"total":3,"page":1,"list":[\
            {"id":1001,"name":"张三","tags":["admin","dev"],"active":true,"score":98.5},\
            {"id":1002,"name":"李四","tags":[],"active":false,"score":76,"remark":"试用期"},\
            {"id":1003,"name":"王五","active":true,"owner":{"team":"平台组"}}]}}""";

    private final JsonCodeArea editor = new JsonCodeArea(true);
    private final TreeView<JsonTreeNode> treeView = new TreeView<>();
    private final StackPane treeHost = new StackPane();
    private final Label treePlaceholder = new Label();
    private final TableView<ObservableList<String>> tableView = new TableView<>();
    private final Label tablePlaceholder = new Label();
    private final Label statusLabel = new Label();

    /** 工具栏左侧的一句话提示，随视图切换（表格视图里提示怎么看长单元格）。 */
    private final Label toolbarHint = new Label("粘贴或拖入 JSON");

    /** 着色被停掉时的提示——一整行的超大 JSON 上色会把界面卡死，得说明为什么没颜色。 */
    private final Label highlightNote = new Label();

    private final ToggleGroup viewGroup = new ToggleGroup();
    private final ToggleButton textToggle = new ToggleButton("文本");
    private final ToggleButton treeToggle = new ToggleButton("树视图");
    private final ToggleButton tableToggle = new ToggleButton("表格");

    private final Label indentLabel = new Label("缩进");
    private final ComboBox<IndentStyle> indentBox = new ComboBox<>();
    private final Button formatButton = new Button("格式化");
    private final Button minifyButton = new Button("压缩");
    private final Button expandButton = new Button("展开全部");
    private final Button collapseButton = new Button("折叠全部");
    private final Button exportButton = new Button("导出 ▾");
    private final ContextMenu exportMenu = new ContextMenu();
    private final Button copyButton = UiKit.copyButton("复制 JSON", () -> editor.getText());

    /** 表格视图的数据源切换：一份 JSON 里常常有好几处数组。 */
    private final Label sourceLabel = new Label("数据源");
    private final ComboBox<JsonTable.Candidate> sourceBox = new ComboBox<>();
    private HBox sourceBar;

    private final PauseTransition debounce = new PauseTransition(Duration.millis(250));

    /**
     * 程序内部改写文本后记录的新内容。
     *
     * <p>格式化会触发文本变更事件，若不加区分，250ms 后的实时校验会把
     * 「✓ 已格式化」的提示覆盖成「✓ 语法合法」，因此用内容比对跳过这一次。
     */
    private String programmaticText;

    /** 当前是否为树视图形态。 */
    private boolean treeMode;

    /** 当前视图：文本 / 树 / 表格。 */
    private View view = View.TEXT;

    /** 表格视图的三种形态。 */
    private enum View {
        TEXT, TREE, TABLE
    }

    /** 最近一次转换出的表格数据，导出时用（界面里额外插了一列行号）。 */
    private JsonTable.Table currentTable;

    /** 表格数据来自哪个字段，空串表示用的就是顶层。 */
    private String currentTableSource = "";

    /** 全文里可选的数据源，以及它们所属的文档——切换候选时不必重新解析。 */
    private List<JsonTable.Candidate> tableCandidates = List.of();
    private JsonDocument tableDocument;

    /** 程序内同步下拉选中项时置位，避免 listener 把切换再跑一遍。 */
    private boolean syncingSource;

    private int nodeCount;
    private int maxDepth;
    /** 删除节点后要重新展开到的位置，避免重建树时把用户钻进去的层级收起来。 */
    private List<Integer> pendingExpand;
    /** 树构建请求的序号，用于丢弃过期结果（用户连续操作时会连续发起解析）。 */
    private int treeToken;
    /** 表格转换请求的序号，作用同上。 */
    private int tableToken;

    public JsonPane() {
        getStyleClass().add("json-pane");
        setSpacing(12);
        setPadding(new Insets(18));

        configureTree();
        configureTable();
        configureEditor();
        configureViewSwitch();
        configureDrop();
        configureShortcut();

        HBox toolbar = buildToolbar();
        VBox.setVgrow(treeHost, Priority.ALWAYS);
        getChildren().addAll(toolbar, treeHost, buildStatusBar());

        debounce.setOnFinished(e -> validateLive());
        textToggle.setSelected(true);
    }

    // ---------------------------------------------------------------- 布局

    private HBox buildToolbar() {
        toolbarHint.getStyleClass().add("hint");

        Region spacer = UiKit.spacer();

        indentBox.getItems().addAll(IndentStyle.values());
        indentBox.setValue(IndentStyle.TWO_SPACES);
        indentBox.getStyleClass().add("indent-box");

        formatButton.getStyleClass().add("primary-button");
        formatButton.setOnAction(e -> doFormat());

        minifyButton.getStyleClass().add("ghost-button");
        minifyButton.setOnAction(e -> doMinify());

        expandButton.getStyleClass().add("ghost-button");
        expandButton.setDisable(true);
        expandButton.setOnAction(e -> setExpandedAll(true));

        collapseButton.getStyleClass().add("ghost-button");
        collapseButton.setDisable(true);
        collapseButton.setOnAction(e -> setExpandedAll(false));

        Button sampleButton = new Button("示例");
        sampleButton.getStyleClass().add("ghost-button");
        sampleButton.setOnAction(e -> {
            if (view == View.TABLE) {
                // 表格视图给一份对象数组，否则顶层对象只有一行，看不出效果
                replaceContent(SAMPLE_ROWS);
                prepareTableAsync();
                return;
            }
            replaceContent(SAMPLE);
            showTree();
        });

        Button clearButton = new Button("清空");
        clearButton.getStyleClass().add("ghost-button");
        clearButton.setOnAction(e -> clearAll());

        HBox bar = new HBox(8, toolbarHint, spacer, textToggle, treeToggle, tableToggle,
                indentLabel, indentBox, formatButton, minifyButton,
                expandButton, collapseButton, sampleButton, clearButton);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private HBox buildStatusBar() {
        statusLabel.getStyleClass().add("status");

        // 状态文字可被压缩：数据源下拉与导出按钮必须优先显示出来，
        // 否则一段长状态会把它们挤出屏幕（HBox 会先满足 Label 的宽度）。
        statusLabel.setMinWidth(0);
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);

        // 着色暂停提示：只在文本视图里出现，且不能把状态文字挤没
        highlightNote.getStyleClass().addAll("status", "status-warn");
        highlightNote.setMinWidth(0);
        highlightNote.setVisible(false);
        highlightNote.setManaged(false);

        copyButton.setDisable(true);

        // 数据源切换：一份 JSON 里常有多个数组，自动挑的只是「最像表格」，得让用户能换
        sourceLabel.getStyleClass().add("field-label");
        sourceBox.getStyleClass().add("indent-box");
        sourceBox.setPrefWidth(210);
        sourceBox.setMaxWidth(320);
        sourceBox.setPrefHeight(26);
        sourceBox.setVisibleRowCount(12);
        sourceBox.valueProperty().addListener((observable, old, next) -> {
            if (!syncingSource && next != null) {
                switchTableSource(next);
            }
        });
        sourceBar = new HBox(6, sourceLabel, sourceBox);
        sourceBar.setAlignment(Pos.CENTER_LEFT);
        sourceBar.setVisible(false);
        sourceBar.setManaged(false);

        MenuItem xlsx = new MenuItem("Excel 工作簿（.xlsx）");
        xlsx.setOnAction(e -> exportXlsx());
        MenuItem csv = new MenuItem("CSV 文件（.csv）");
        csv.setOnAction(e -> exportCsv());
        exportMenu.getItems().setAll(xlsx, csv);
        // 用普通按钮 + 弹出菜单而不是 MenuButton：后者内置的箭头格子会把自己撑高，
        // 为了和旁边的「复制」等高又得压缩内部空间，字就会被挤扁
        // 菜单朝上弹：按钮在状态栏最底部，往下弹会伸到窗口外面去。
        // 纵向偏移要减掉按钮自身高度——Side.TOP 的定位基准是按钮底边，
        // 不减的话菜单会盖在按钮上（实测底边与按钮底边齐平）。
        exportButton.setOnAction(e -> exportMenu.show(exportButton, Side.TOP,
                0, -(exportButton.getHeight() + 6)));
        exportButton.getStyleClass().add("export-button");
        exportButton.setMinHeight(25);
        exportButton.setVisible(false);
        exportButton.setManaged(false);

        HBox bar = new HBox(10, statusLabel, highlightNote, sourceBar, exportButton, copyButton);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    // ---------------------------------------------------------------- 控件配置

    private void configureEditor() {
        Label placeholder = new Label("在此粘贴 JSON 文本，或把 .json 文件拖入窗口…");
        placeholder.getStyleClass().addAll("hint", "code-placeholder");
        editor.codeArea().setPlaceholder(placeholder);
        editor.setOnTextChanged(() -> {
            String text = editor.getText();
            copyButton.setDisable(text.isEmpty());
            if (programmaticText != null && programmaticText.equals(text)) {
                programmaticText = null;
                debounce.stop();
                return;
            }
            programmaticText = null;
            debounce.playFromStart();
        });

        treeHost.getChildren().add(editor);
    }

    private void configureTree() {
        treePlaceholder.setText("切到「树视图」后，节点会显示在这里");
        treePlaceholder.getStyleClass().add("hint");
        treePlaceholder.setMouseTransparent(true);

        treeView.setShowRoot(true);
        treeView.getStyleClass().add("json-tree");
        treeView.setCellFactory(tree -> new JsonTreeCell());
        ScrollBars.installMinThumb(treeView);

        treeHost.getChildren().addAll(treeView, treePlaceholder);
    }

    /**
     * 表格视图。
     *
     * <p>列是动态的（对象数组里所有键名的并集），因此每次转换都重建列，
     * 再往里灌一组「行号 + 单元格」的字符串列表——行号也当数据放，排序后才不会串行。
     */
    private void configureTable() {
        tablePlaceholder.setText("切到「表格」后，JSON 数组会显示成表格");
        tablePlaceholder.getStyleClass().add("hint");
        tablePlaceholder.setMouseTransparent(true);

        tableView.getStyleClass().add("json-table");
        // 表格动辄几千行，滑块同样会被压没
        ScrollBars.installMinThumb(tableView);
        tableView.setPlaceholder(placeholderOf("没有数据"));
        tableView.setVisible(false);
        tableView.setManaged(false);
        tablePlaceholder.setVisible(false);
        tablePlaceholder.setManaged(false);

        treeHost.getChildren().addAll(tableView, tablePlaceholder);
    }

    /**
     * 空表格 / 空列表的占位文字。
     *
     * <p>顺手带 hint 样式：裸 Label 只有 modena 的 {@code *.label} 规则给它上色，
     * 而 JavaFX 25 那条规则 lookup 的变量我们的主题里没有，解析失败会退回默认黑字，
     * 深色主题下就是一片看不见。
     */
    private static Label placeholderOf(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("hint");
        return label;
    }

    /** 树构建的结果：根节点与统计信息，全部在后台线程算好再交给界面。 */
    private record TreeData(JsonTreeNode root, int nodeCount, int maxDepth) {
    }

    /**
     * 懒加载子节点。
     *
     * <p>展开时才生成下一层，界面只承担「当前看得见的那部分」，
     * 避免一次性包出几万个 TreeItem 把界面线程卡住。
     */
    private static final class LazyTreeItem extends TreeItem<JsonTreeNode> {

        private final JsonTreeNode node;
        private boolean childrenBuilt;

        LazyTreeItem(JsonTreeNode node) {
            super(node);
            this.node = node;
            expandedProperty().addListener((observable, wasExpanded, expanded) -> {
                if (expanded) {
                    buildChildren();
                }
            });
        }

        @Override
        public boolean isLeaf() {
            return !node.hasChildren();
        }

        @Override
        public javafx.collections.ObservableList<TreeItem<JsonTreeNode>> getChildren() {
            buildChildren();
            return super.getChildren();
        }

        private void buildChildren() {
            if (childrenBuilt || !node.hasChildren()) {
                return;
            }
            childrenBuilt = true;
            List<TreeItem<JsonTreeNode>> items = new ArrayList<>(node.childCount());
            for (JsonTreeNode child : node.children()) {
                items.add(new LazyTreeItem(child));
            }
            super.getChildren().setAll(items);
        }
    }

    /**
     * 强制切到树视图并重新解析。
     *
     * <p>即使用户已经在树视图（例如刚点了「示例」），也要重建，
     * 否则 ToggleGroup 选中态没变化、树还停在旧内容上。
     */
    private void showTree() {
        if (viewGroup.getSelectedToggle() == treeToggle) {
            prepareTreeAsync();
            return;
        }
        viewGroup.selectToggle(treeToggle);
    }

    /** 三个按钮共用一个 ToggleGroup，切换时决定哪块内容可见、哪组按钮可用。 */
    private void configureViewSwitch() {
        for (ToggleButton button : new ToggleButton[]{textToggle, treeToggle, tableToggle}) {
            button.setToggleGroup(viewGroup);
            button.getStyleClass().add("mode-toggle");
            button.setFocusTraversable(false);
        }

        viewGroup.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
            if (newToggle == null) {
                // 再次点击已选中的按钮会取消选择，这里恢复回去
                if (oldToggle != null) {
                    oldToggle.setSelected(true);
                }
                return;
            }
            View next = newToggle == treeToggle ? View.TREE
                    : newToggle == tableToggle ? View.TABLE : View.TEXT;
            // 先切视图再解析：解析在后台跑，期间区域显示「正在解析 / 转换…」
            applyView(next);
            if (next == View.TREE) {
                prepareTreeAsync();
            } else if (next == View.TABLE) {
                prepareTableAsync();
            }
        });
    }

    private void configureDrop() {
        UiKit.installFileDrop(this, files -> loadFile(files.get(0)), null);
    }

    private void configureShortcut() {
        addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.ENTER) {
                // 文本态快捷格式化；树 / 表格态等同于按当前内容重建
                switch (view) {
                    case TREE -> showTree();
                    case TABLE -> prepareTableAsync();
                    default -> doFormat();
                }
                event.consume();
            }
        });
    }

    /**
     * 读取拖入的文件。
     *
     * <p>读盘在后台线程做：8MB 的文本在界面线程上读取 + 建树会让窗口假死数秒，
     * 表现就是「拖完文件界面卡住」。这里先给出提示，读完再交给树视图解析。
     */
    private void loadFile(Path file) {
        setStatus("正在读取 " + file.getFileName() + " …", false);
        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                long size = Files.size(file);
                if (size > DROP_FILE_LIMIT) {
                    throw new IOException("文件过大（" + size + " 字节），请改用较小的文件");
                }
                return Files.readString(file, StandardCharsets.UTF_8);
            }
        };
        task.setOnSucceeded(event -> {
            replaceContent(task.getValue());
            setStatus("已载入 " + file.getFileName(), false);
            showTree();
        });
        task.setOnFailed(event -> setStatus("读取文件失败：" + messageOf(task.getException()), true));
        UiKit.runInBackground(task, "json-read-file");
    }

    private static String messageOf(Throwable error) {
        if (error == null) {
            return "未知错误";
        }
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.toString() : message;
    }


    // ---------------------------------------------------------------- 对外接口

    /** 载入一段 JSON 到编辑框（供外部调用或自动化测试使用）。 */
    public void setContent(String json) {
        replaceContent(json == null ? "" : json);
    }

    /** 立即执行格式化，效果等同于点击「格式化」按钮。 */
    public void formatNow() {
        doFormat();
    }

    /** 立即切到树视图并重建，效果等同于点击「树视图」。 */
    public void buildNow() {
        showTree();
    }

    /** 立即切到表格视图并转换，效果等同于点击「表格」。 */
    public void tableNow() {
        if (viewGroup.getSelectedToggle() != tableToggle) {
            viewGroup.selectToggle(tableToggle);
            return;
        }
        prepareTableAsync();
    }

    /** 当前表格的数据行数（不含表头），供自动化自检断言。 */
    public int tableRowCount() {
        return tableView.getItems().size();
    }

    /** 当前表格的列名（不含行号列）。 */
    public List<String> tableColumns() {
        return currentTable == null ? List.of() : currentTable.columns();
    }

    /** 当前表格的数据来源路径（如 {@code data.list}），空串表示用的就是顶层。 */
    public String tableSource() {
        return currentTableSource;
    }

    /** 供自动化自检：按位置路径删除节点，等价于右键「删除此节点」。 */
    public boolean deletePathNow(List<Integer> segments) {
        return deleteNode(segments, "节点 " + segments);
    }

    /** 全文可切换的数据源数量（表格视图）。 */
    public int candidateCount() {
        return tableCandidates.size();
    }

    /** 供自动化自检：切换到第 {@code index} 个数据源。 */
    public void selectCandidateNow(int index) {
        if (index < 0 || index >= tableCandidates.size()) {
            return;
        }
        sourceBox.getSelectionModel().select(index);
    }

    /** 供自动化自检：同步导出 xlsx 到指定路径。 */
    public void exportXlsxNow(Path target) throws IOException {
        if (currentTable == null || currentTable.columns().isEmpty()) {
            throw new IOException("没有可导出的表格");
        }
        XlsxExporter.write(target, sheetName(), currentTable.columns(), currentTable.rows());
    }

    /** 当前编辑框内的文本，便于自动化测试断言。 */
    public String content() {
        return editor.getText();
    }

    // ---------------------------------------------------------------- 动作

    private void doFormat() {
        debounce.stop();
        String source = editor.getText();
        if (source.isBlank()) {
            setStatus("请先输入 JSON 内容", true);
            return;
        }
        try {
            String formatted = JsonFormatter.format(source, indentBox.getValue());
            replaceContent(formatted);
            setStatus("✓ 格式化完成，" + formatted.length() + " 字符", false);
            refreshActiveView();
        } catch (JsonFormatException e) {
            setStatus("✗ " + e.getFriendlyMessage(), true);
        }
    }

    private void doMinify() {
        debounce.stop();
        String source = editor.getText();
        if (source.isBlank()) {
            setStatus("请先输入 JSON 内容", true);
            return;
        }
        try {
            String compact = JsonFormatter.minify(source);
            replaceContent(compact);
            setStatus("✓ 已压缩，" + compact.length() + " 字符", false);
            refreshActiveView();
        } catch (JsonFormatException e) {
            setStatus("✗ " + e.getFriendlyMessage(), true);
        }
    }

    /** 程序内改写文本：记下内容以便跳过一次实时校验回调。 */
    private void replaceContent(String text) {
        programmaticText = text;
        editor.setText(text);
        copyButton.setDisable(text.isEmpty());
        // 编辑器是同步着色的，这里能立刻知道着色有没有被停掉
        updateHighlightNote();
    }

    /**
     * 更新「着色已暂停」提示。
     *
     * <p>一整行的超大 JSON 上色会让界面卡死，{@link JsonCodeArea} 会主动跳过它；
     * 界面必须说明为什么没有颜色，否则看起来像着色坏了。
     */
    private void updateHighlightNote() {
        boolean paused = view == View.TEXT && editor.isHighlightPaused();
        highlightNote.setVisible(paused);
        highlightNote.setManaged(paused);
        if (paused) {
            highlightNote.setText("⚠ 有单行长 " + editor.longestParagraphLength()
                    + " 字符，已暂停着色；点「格式化」展开后恢复");
        }
    }

    private void validateLive() {
        if (view != View.TEXT) {
            return;
        }
        try {
            String text = editor.getText();
            if (text.isBlank()) {
                setStatus("", false);
                return;
            }
            if (text.length() > REALTIME_LIMIT) {
                setStatus("内容较长，已暂停实时校验，点击「格式化」时校验", false);
                return;
            }
            try {
                JsonFormatter.validate(text);
                setStatus("✓ 语法合法，" + text.length() + " 字符", false);
            } catch (JsonFormatException e) {
                setStatus("✗ " + e.getFriendlyMessage(), true);
            }
        } finally {
            updateHighlightNote();
        }
    }

    // ---------------------------------------------------------------- 逻辑

    /** 切换视图时的可见性与按钮状态。 */
    private void applyView(View mode) {
        view = mode;
        treeMode = mode == View.TREE;
        debounce.stop();

        boolean text = mode == View.TEXT;
        boolean tree = mode == View.TREE;
        boolean table = mode == View.TABLE;

        editor.setVisible(text);
        editor.setManaged(text);

        treeView.setVisible(tree);
        treeView.setManaged(tree);
        treePlaceholder.setVisible(tree && treeView.getRoot() == null);
        treePlaceholder.setManaged(tree && treeView.getRoot() == null);

        tableView.setVisible(table);
        tableView.setManaged(table);
        boolean tableEmpty = tableView.getItems().isEmpty();
        tablePlaceholder.setVisible(table && tableEmpty);
        tablePlaceholder.setManaged(table && tableEmpty);

        // 格式化 / 压缩始终可用（作用于文本并同步刷新视图），其余按钮只在对应视图有意义
        indentLabel.setVisible(text);
        indentLabel.setManaged(text);
        indentBox.setVisible(text);
        indentBox.setManaged(text);
        expandButton.setVisible(tree);
        expandButton.setManaged(tree);
        collapseButton.setVisible(tree);
        collapseButton.setManaged(tree);
        expandButton.setDisable(!tree);
        collapseButton.setDisable(!tree);
        exportButton.setVisible(table);
        exportButton.setManaged(table);
        exportButton.setDisable(table && currentTable == null);

        // 表格里的单元格是被列宽截断的，得告诉用户还有完整内容可看
        toolbarHint.setText(table ? "双击单元格查看完整内容" : "粘贴或拖入 JSON");

        syncSourceBar();
        updateHighlightNote();

        if (text) {
            editor.requestFocusOnArea();
            validateLive();
        }
    }

    /**
     * 按当前状态决定「数据源」下拉是否出现。
     *
     * <p>必须在表格数据落地后也调一次：切到表格视图时解析还在后台跑，
     * 那一刻候选还是空的；若只在切换视图时判断，下拉就永远不会出现，
     * 用户只能看到状态栏写着「可在右侧切换」却找不到可切换的地方。
     */
    private void syncSourceBar() {
        boolean show = view == View.TABLE && tableCandidates.size() > 1;
        sourceBar.setVisible(show);
        sourceBar.setManaged(show);
    }

    /** 文本被改写后，按当前视图刷新对应的解析结果。 */
    private void refreshActiveView() {
        if (view == View.TREE) {
            prepareTreeAsync();
        } else if (view == View.TABLE) {
            prepareTableAsync();
        }
    }

    /**
     * 解析当前文本构建树。
     *
     * <p>解析与统计都放在后台线程，界面只负责把结果挂到 TreeView 上；
     * 期间显示「正在解析…」，失败则自动退回文本视图方便就地修改。
     */
    private void prepareTreeAsync() {
        String text = editor.getText();
        if (text.isBlank()) {
            setStatus("请先输入 JSON 内容", true);
            revertToText();
            return;
        }
        int token = ++treeToken;
        treeView.setRoot(null);
        expandButton.setDisable(true);
        collapseButton.setDisable(true);
        treePlaceholder.setText("正在解析…");
        treePlaceholder.setVisible(true);
        treePlaceholder.setManaged(true);
        setStatus("正在解析…", false);

        Task<TreeData> task = new Task<>() {
            @Override
            protected TreeData call() throws JsonFormatException {
                JsonTreeNode root = JsonTreeBuilder.build(text);
                return new TreeData(root, countNodes(root), depthOf(root, 1));
            }
        };
        task.setOnSucceeded(event -> {
            if (token != treeToken) {
                return;
            }
            installTree(task.getValue());
        });
        task.setOnFailed(event -> {
            if (token != treeToken) {
                return;
            }
            Throwable error = task.getException();
            treeView.setRoot(null);
            treePlaceholder.setText("解析失败，切回「文本」修改后重试");
            nodeCount = 0;
            maxDepth = 0;
            String message = error instanceof JsonFormatException jsonError
                    ? jsonError.getFriendlyMessage()
                    : messageOf(error);
            setStatus("✗ " + message, true);
            revertToText();
        });
        UiKit.runInBackground(task, "json-tree-build");
    }

    /** 解析成功后把根节点挂上，并展开前两层。 */
    private void installTree(TreeData data) {
        nodeCount = data.nodeCount();
        maxDepth = data.maxDepth();

        TreeItem<JsonTreeNode> rootItem = new LazyTreeItem(data.root());
        treeView.setRoot(rootItem);
        rootItem.setExpanded(true);
        if (nodeCount <= 300) {
            for (TreeItem<JsonTreeNode> child : rootItem.getChildren()) {
                child.setExpanded(true);
            }
        }
        if (pendingExpand != null) {
            expandTo(rootItem, pendingExpand);
            pendingExpand = null;
        }
        treePlaceholder.setText("切到「树视图」后，节点会显示在这里");
        treePlaceholder.setVisible(false);
        treePlaceholder.setManaged(false);
        expandButton.setDisable(false);
        collapseButton.setDisable(false);
        setStatus("✓ 共 " + nodeCount + " 个节点，最大深度 " + maxDepth
                + "，右键节点可复制或删除", false);
    }

    /**
     * 按位置路径逐层展开。
     *
     * <p>删除一个深层节点后树会整体重建，若不做这件事，用户刚钻进去的层级会全部收起，
     * 看起来像「删完之后跑到别的地方去了」。
     */
    private void expandTo(TreeItem<JsonTreeNode> root, List<Integer> path) {
        TreeItem<JsonTreeNode> current = root;
        current.setExpanded(true);
        for (int index : path) {
            if (index < 0 || index >= current.getChildren().size()) {
                return;
            }
            current = current.getChildren().get(index);
            current.setExpanded(true);
        }
    }

    // ---------------------------------------------------------------- 删除节点

    /**
     * 删除树上的一个节点（连同其子树），并把结果写回文本。
     *
     * <p>文本始终是唯一数据源：解析成可变的 {@link JsonDocument}、按位置删掉，
     * 再按原排版写回去——原文是单行就保持单行，多行就沿用它的缩进——最后重建树。
     * 若只改树模型不改文本，两个视图就会各说各话。
     *
     * <p>不做二次确认：删节点是高频操作，每次弹框比删错更烦；文本改写走的是
     * CodeArea 的编辑历史，删错了 {@code Ctrl+Z} 就能退回来。
     *
     * @param segments 节点在文档里的位置（{@link JsonTreeNode#segments()}）
     * @param label    状态栏里的名字
     */
    private boolean deleteNode(List<Integer> segments, String label) {
        if (segments == null || segments.isEmpty()) {
            setStatus("根节点不能删除，可用「清空」清掉整份内容", true);
            return false;
        }
        String source = editor.getText();
        try {
            JsonDocument document = JsonDocument.parse(source);
            if (!document.remove(segments)) {
                setStatus("这个节点已经不存在了，请重新解析后再试", true);
                return false;
            }
            boolean singleLine = source.indexOf('\n') < 0;
            String updated = singleLine
                    ? document.toCompactJson()
                    : document.toJson(detectIndent(source));
            // 记住父级位置，重建树后把这一支重新展开
            pendingExpand = List.copyOf(segments.subList(0, segments.size() - 1));
            replaceContent(updated);
            prepareTreeAsync();
            setStatus("✓ 已删除 " + label + "，可按 Ctrl+Z 撤销", false);
            return true;
        } catch (JsonFormatException e) {
            setStatus("✗ " + e.getFriendlyMessage(), true);
            return false;
        }
    }

    /** 从已有文本里猜缩进单位，避免删一个节点就把 4 空格的文件重排成 2 空格。 */
    private IndentStyle detectIndent(String text) {
        for (int i = text.indexOf('\n'); i >= 0 && i + 1 < text.length(); i = text.indexOf('\n', i + 1)) {
            int j = i + 1;
            while (j < text.length() && (text.charAt(j) == ' ' || text.charAt(j) == '\t')) {
                j++;
            }
            if (j >= text.length() || text.charAt(j) == '\n' || text.charAt(j) == '\r') {
                continue;
            }
            String lead = text.substring(i + 1, j);
            if (lead.startsWith("\t")) {
                return IndentStyle.TAB;
            }
            return lead.length() >= 4 ? IndentStyle.FOUR_SPACES : IndentStyle.TWO_SPACES;
        }
        return indentBox.getValue() == null ? IndentStyle.TWO_SPACES : indentBox.getValue();
    }

    /** 解析失败或内容为空时退回文本视图（树、表格都适用）。 */
    private void revertToText() {
        if (view == View.TEXT) {
            return;
        }
        textToggle.setSelected(true);
    }

    private void clearAll() {
        treeToken++;
        tableToken++;
        programmaticText = null;
        editor.clear();
        treeView.setRoot(null);
        treePlaceholder.setText("切到「树视图」后，节点会显示在这里");
        tableView.getItems().clear();
        tableView.getColumns().clear();
        currentTable = null;
        currentTableSource = "";
        tableCandidates = List.of();
        tableDocument = null;
        syncingSource = true;
        sourceBox.getItems().clear();
        syncingSource = false;
        sourceBar.setVisible(false);
        sourceBar.setManaged(false);
        tablePlaceholder.setText("切到「表格」后，JSON 数组会显示成表格");
        nodeCount = 0;
        maxDepth = 0;
        copyButton.setDisable(true);
        setStatus("", false);
        viewGroup.selectToggle(textToggle);
    }

    // ---------------------------------------------------------------- 表格

    /**
     * 解析当前文本并转成表格。
     *
     * <p>与建树一样放在后台线程：几万行的数组在界面线程上转完再灌进 TableView，
     * 会有一段明显空白。序号 token 用来丢弃过期结果。
     */
    private void prepareTableAsync() {
        String text = editor.getText();
        if (text.isBlank()) {
            setStatus("请先输入 JSON 内容", true);
            revertToText();
            return;
        }
        int token = ++tableToken;
        tableView.getItems().clear();
        tableView.getColumns().clear();
        currentTable = null;
        currentTableSource = "";
        tableCandidates = List.of();
        tableDocument = null;
        syncingSource = true;
        sourceBox.getItems().clear();
        syncingSource = false;
        sourceBar.setVisible(false);
        sourceBar.setManaged(false);
        exportButton.setDisable(true);
        tablePlaceholder.setText("正在转换成表格…");
        tablePlaceholder.setVisible(true);
        tablePlaceholder.setManaged(true);
        setStatus("正在转换…", false);

        Task<JsonTable.AutoResult> task = new Task<>() {
            @Override
            protected JsonTable.AutoResult call() throws JsonFormatException {
                // 自动找最像表格的数组：接口返回多半把真正的数据包在 data.list 这类字段里
                return JsonTable.buildAuto(text);
            }
        };
        task.setOnSucceeded(event -> {
            if (token != tableToken) {
                return;
            }
            installTable(task.getValue());
        });
        task.setOnFailed(event -> {
            if (token != tableToken) {
                return;
            }
            Throwable error = task.getException();
            String message = error instanceof JsonFormatException jsonError
                    ? jsonError.getFriendlyMessage()
                    : messageOf(error);
            tablePlaceholder.setText("转换失败，切回「文本」修改后重试");
            setStatus("✗ " + message, true);
            revertToText();
        });
        UiKit.runInBackground(task, "json-table-build");
    }

    /** 把自动定位的结果装进界面：先铺数据源下拉，再填表格。 */
    private void installTable(JsonTable.AutoResult result) {
        tableCandidates = result.candidates();
        tableDocument = result.document();

        syncingSource = true;
        sourceBox.getItems().setAll(result.candidates());
        sourceBox.getSelectionModel().select(result.chosen());
        syncingSource = false;

        currentTableSource = result.sourcePath();
        fillTable(result.table(), result.candidates().size());
    }

    /**
     * 把候选数据源换成另一处数组。
     *
     * <p>文档在 {@link JsonTable.AutoResult} 里带着，这里不用重新解析全文；
     * 转换仍在后台跑，切换失败就把下拉选回原来那项。
     */
    private void switchTableSource(JsonTable.Candidate candidate) {
        JsonDocument document = tableDocument;
        if (document == null || candidate.path().equals(currentTableSource)) {
            return;
        }
        int token = ++tableToken;
        setStatus("正在读取 " + candidate.label() + " …", false);
        Task<JsonTable.Table> task = new Task<>() {
            @Override
            protected JsonTable.Table call() throws JsonFormatException {
                return JsonTable.buildAt(document, candidate);
            }
        };
        task.setOnSucceeded(event -> {
            if (token != tableToken) {
                return;
            }
            currentTableSource = candidate.path();
            fillTable(task.getValue(), tableCandidates.size());
        });
        task.setOnFailed(event -> {
            if (token != tableToken) {
                return;
            }
            Throwable error = task.getException();
            String message = error instanceof JsonFormatException jsonError
                    ? jsonError.getFriendlyMessage()
                    : messageOf(error);
            setStatus("✗ " + message, true);
            syncingSource = true;
            sourceBox.getSelectionModel().select(tableCandidates.indexOf(
                    tableCandidates.stream()
                            .filter(item -> item.path().equals(currentTableSource))
                            .findFirst().orElse(null)));
            syncingSource = false;
        });
        UiKit.runInBackground(task, "json-table-switch");
    }

    /**
     * 把表格数据装进 TableView：第一列是行号，其余列按数据动态生成。
     *
     * @param candidateCount 全文可切的数据源数量，用于提示「还能换」
     */
    private void fillTable(JsonTable.Table data, int candidateCount) {
        currentTable = data;
        tableView.getColumns().clear();
        tableView.getItems().clear();
        // 候选是后台解析完才到的，下拉要在这里补开（切换视图那时它还是空的）
        syncSourceBar();

        if (data.columns().isEmpty()) {
            tablePlaceholder.setText(currentTableSource.isEmpty()
                    ? "这是空数组，没有可显示的行"
                    : currentTableSource + " 是空数组，没有可显示的行");
            tablePlaceholder.setVisible(true);
            tablePlaceholder.setManaged(true);
            exportButton.setDisable(true);
            setStatus("✓ 空数组，0 行" + sourceSuffix(candidateCount), false);
            return;
        }

        TableColumn<ObservableList<String>, String> indexColumn = new TableColumn<>("#");
        indexColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().get(0)));
        indexColumn.setPrefWidth(52);
        indexColumn.setSortable(false);
        tableView.getColumns().add(indexColumn);

        for (int i = 0; i < data.columns().size(); i++) {
            int cellIndex = i + 1;
            TableColumn<ObservableList<String>, String> column =
                    new TableColumn<>(data.columns().get(i));
            column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                    cellIndex < cell.getValue().size() ? cell.getValue().get(cellIndex) : ""));
            column.setPrefWidth(140);
            column.setCellFactory(col -> new JsonTableCell());
            tableView.getColumns().add(column);
        }

        ObservableList<ObservableList<String>> rows = FXCollections.observableArrayList();
        for (int i = 0; i < data.rows().size(); i++) {
            ObservableList<String> cells = FXCollections.observableArrayList();
            cells.add(String.valueOf(i + 1));
            cells.addAll(data.rows().get(i));
            rows.add(cells);
        }
        tableView.setItems(rows);
        tablePlaceholder.setVisible(false);
        tablePlaceholder.setManaged(false);
        exportButton.setDisable(false);

        StringBuilder message = new StringBuilder("✓ ")
                .append(data.rows().size()).append(" 行 × ")
                .append(data.columns().size()).append(" 列");
        if (data.truncatedRows()) {
            message.append("；共 ").append(data.totalRows()).append(" 行，只显示前 ")
                    .append(JsonTable.DEFAULT_MAX_ROWS).append(" 行");
        }
        if (data.truncatedColumns()) {
            message.append("；字段过多，只显示前 ").append(JsonTable.DEFAULT_MAX_COLUMNS).append(" 列");
        }
        message.append(sourceSuffix(candidateCount));
        setStatus(message.toString(), false);
    }

    /**
     * 表格单元格：内容超长时截断成省略号，双击 / 右键看完整内容。
     *
     * <p>嵌套的对象与数组是还原成紧凑 JSON 放进一格里的（见 {@link JsonTable}），
     * 几百个字符在 140px 的列宽里连零头都显示不出来，只能靠弹窗补上。
     * 悬停先给一段预览提示，双击才开窗——单击仍然是选中行，不至于看谁都弹一下。
     *
     * <p>行号从行数据的第 0 格取（{@link #fillTable} 放进去的），
     * 不用 {@code getIndex()}：排序后下标指的是排序后的视图位置，不是原来那一行。
     */
    private final class JsonTableCell extends TableCell<ObservableList<String>, String> {

        private JsonTableCell() {
            setTextOverrun(OverrunStyle.ELLIPSIS);
            setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !isEmpty()) {
                    openDetail();
                }
            });
            setOnKeyPressed(event -> {
                if (!isEmpty() && (event.getCode() == KeyCode.ENTER
                        || event.getCode() == KeyCode.SPACE)) {
                    openDetail();
                }
            });
            setContextMenu(buildCellMenu());
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setTooltip(null);
                return;
            }
            setText(item);
            setTooltip(item.length() >= TOOLTIP_FROM ? new Tooltip(previewOf(item)) : null);
        }

        /** 右键菜单：看完整内容与复制，不必为了复制先进弹窗。 */
        private ContextMenu buildCellMenu() {
            MenuItem view = new MenuItem("查看完整内容");
            view.setOnAction(event -> openDetail());
            MenuItem copy = new MenuItem("复制此单元格");
            copy.setOnAction(event -> {
                String value = getItem();
                if (value != null && !value.isEmpty()) {
                    ClipboardUtil.copy(value);
                }
            });
            return new ContextMenu(view, copy);
        }

        private void openDetail() {
            String value = getItem();
            if (value == null || value.isEmpty()) {
                return;
            }
            ObservableList<String> row = getTableRow() == null ? null : getTableRow().getItem();
            String rowNumber = row == null || row.isEmpty() ? "-" : row.get(0);
            TableColumn<ObservableList<String>, String> column = getTableColumn();
            String columnName = column == null ? "" : column.getText();
            Scene scene = getTableView() == null ? null : getTableView().getScene();
            Window owner = scene == null ? null : scene.getWindow();
            CellDetailDialog.show(owner, columnName, rowNumber, value);
        }
    }

    /** 悬停提示的预览文本：完整内容交给弹窗，这里只给个能认出来的开头。 */
    private static String previewOf(String text) {
        return text.length() > TOOLTIP_LIMIT ? text.substring(0, TOOLTIP_LIMIT) + "…" : text;
    }

    /** 状态栏用的来源说明：表格取的是哪一段数组，以及还能换几处。 */
    private String sourceSuffix(int candidateCount) {
        if (currentTableSource.isEmpty()) {
            return "";
        }
        String suffix = " · 数据来自 " + currentTableSource;
        if (candidateCount > 1) {
            suffix += "（全文 " + candidateCount + " 处数组，可在右侧切换）";
        }
        return suffix;
    }

    /**
     * 导出为 xlsx。
     *
     * <p>默认导出格式：CSV 在中文 Excel 里会踩编码、逗号、长数字变科学计数法这些坑，
     * 而 xlsx 是二进制格式，数字与文本各归各位。
     */
    private void exportXlsx() {
        if (currentTable == null || currentTable.columns().isEmpty()) {
            setStatus("没有可导出的表格", true);
            return;
        }
        File target = chooseExportTarget("导出 Excel", exportBaseName() + ".xlsx",
                new FileChooser.ExtensionFilter("Excel 工作簿", "*.xlsx"));
        if (target == null) {
            return;
        }
        JsonTable.Table data = currentTable;
        String sheet = sheetName();
        setStatus("正在写出 " + target.getName() + " …", false);
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws IOException {
                XlsxExporter.write(target.toPath(), sheet, data.columns(), data.rows());
                return null;
            }
        };
        task.setOnSucceeded(event -> setStatus("✓ 已导出 " + target.getName() + "（"
                + data.rows().size() + " 行 × " + data.columns().size() + " 列）", false));
        task.setOnFailed(event -> setStatus("✗ 导出失败：" + messageOf(task.getException()), true));
        UiKit.runInBackground(task, "json-xlsx-export");
    }

    /** 导出为 CSV（UTF-8 带 BOM，Excel 打开不乱码）。 */
    private void exportCsv() {
        if (currentTable == null || currentTable.columns().isEmpty()) {
            setStatus("没有可导出的表格", true);
            return;
        }
        File target = chooseExportTarget("导出 CSV", exportBaseName() + ".csv",
                new FileChooser.ExtensionFilter("CSV 文件", "*.csv"));
        if (target == null) {
            return;
        }
        String csv = "\uFEFF" + JsonTable.toCsv(currentTable);
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws IOException {
                Files.writeString(target.toPath(), csv, StandardCharsets.UTF_8);
                return null;
            }
        };
        task.setOnSucceeded(event -> setStatus("✓ 已导出 " + target.getName(), false));
        task.setOnFailed(event -> setStatus("✗ 导出失败：" + messageOf(task.getException()), true));
        UiKit.runInBackground(task, "json-csv-export");
    }

    /** 选保存位置；用户取消时返回 {@code null}。 */
    private File chooseExportTarget(String title, String fileName,
                                    FileChooser.ExtensionFilter filter) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.setInitialFileName(fileName);
        chooser.getExtensionFilters().add(filter);
        return chooser.showSaveDialog(UiKit.ownerWindow(this));
    }

    /** 导出文件名的默认值：取数据源路径的最后一段，{@code data.list} → {@code list}。 */
    private String exportBaseName() {
        if (currentTableSource.isEmpty()) {
            return "table";
        }
        String path = currentTableSource;
        int dot = path.lastIndexOf('.');
        String last = dot >= 0 && dot + 1 < path.length() ? path.substring(dot + 1) : path;
        String cleaned = last.replaceAll("[\\\\/:*?\"<>|\\[\\]]", "_").trim();
        return cleaned.isEmpty() ? "table" : cleaned;
    }

    /** 工作表名，同样取数据源路径；非法字符由 {@link XlsxExporter} 兜底。 */
    private String sheetName() {
        return currentTableSource.isEmpty() ? "数据" : currentTableSource;
    }

    private int countNodes(JsonTreeNode node) {
        int total = 1;
        for (JsonTreeNode child : node.children()) {
            total += countNodes(child);
        }
        return total;
    }

    private int depthOf(JsonTreeNode node, int current) {
        int deepest = current;
        for (JsonTreeNode child : node.children()) {
            deepest = Math.max(deepest, depthOf(child, current + 1));
        }
        return deepest;
    }

    private void setExpandedAll(boolean expanded) {
        TreeItem<JsonTreeNode> root = treeView.getRoot();
        if (root == null) {
            return;
        }
        int limit = nodeCount > LARGE_TREE_THRESHOLD && expanded ? 3 : Integer.MAX_VALUE;
        expandRecursive(root, expanded, 0, limit);
        if (nodeCount > LARGE_TREE_THRESHOLD && expanded) {
            setStatus("节点较多，仅展开前三层（共 " + nodeCount + " 个节点）", false);
        }
    }

    private void expandRecursive(TreeItem<JsonTreeNode> item, boolean expanded, int level, int limit) {
        if (level >= limit) {
            return;
        }
        item.setExpanded(expanded);
        for (TreeItem<JsonTreeNode> child : item.getChildren()) {
            expandRecursive(child, expanded, level + 1, limit);
        }
    }

    private void setStatus(String message, boolean error) {
        UiKit.setStatus(statusLabel, message, error);
    }

    /** 自定义单元格：键名 + 类型徽章 + 值预览；右键复制或删除。 */
    private final class JsonTreeCell extends TreeCell<JsonTreeNode> {

        @Override
        protected void updateItem(JsonTreeNode item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setContextMenu(null);
                return;
            }
            setText(null);
            setGraphic(render(item));
            setContextMenu(buildNodeMenu(item));
        }

        private HBox render(JsonTreeNode item) {
            Label key = new Label(item.key());
            key.getStyleClass().add("tree-key");

            Label badge = new Label(item.type().label());
            badge.getStyleClass().addAll("type-badge", "type-" + item.type().name().toLowerCase());

            Label preview = new Label(item.preview());
            preview.getStyleClass().add("tree-preview");

            HBox row = new HBox(8, key, badge, preview);
            row.setAlignment(Pos.CENTER_LEFT);
            return row;
        }
    }

    /**
     * 节点右键菜单。
     *
     * <p>抽成 {@code JsonPane} 的方法而不是写在单元格里，只是为了让自检能直接
     * 断言菜单项；单元格每次刷新都会重建菜单，闭包里捕获的必须是当次的节点。
     */
    private ContextMenu buildNodeMenu(JsonTreeNode item) {
        MenuItem copyPath = new MenuItem("复制路径");
        copyPath.setOnAction(e -> ClipboardUtil.copy(item.path()));

        MenuItem copyKey = new MenuItem("复制键名");
        copyKey.setOnAction(e -> ClipboardUtil.copy(item.key()));
        // 根节点占位名与数组下标不算键名
        copyKey.setDisable(!item.hasName());

        MenuItem copyValue = new MenuItem("复制值");
        copyValue.setOnAction(e -> ClipboardUtil.copy(item.value()));
        copyValue.setDisable(item.type().isContainer());

        // 根节点没有父容器，删不掉
        MenuItem delete = new MenuItem("删除此节点");
        delete.setDisable(item.isRoot());
        delete.setOnAction(e -> deleteNode(item.segments(), item.path()));

        return new ContextMenu(copyPath, copyKey, copyValue,
                new SeparatorMenuItem(), delete);
    }

    /** 供自动化自检：列出某个节点的右键菜单项（分隔线不计）。 */
    public List<String> menuLabels(JsonTreeNode node) {
        List<String> labels = new ArrayList<>();
        for (MenuItem item : buildNodeMenu(node).getItems()) {
            if (item instanceof SeparatorMenuItem) {
                continue;
            }
            labels.add(item.getText() + (item.isDisable() ? "（禁用）" : ""));
        }
        return labels;
    }
}
