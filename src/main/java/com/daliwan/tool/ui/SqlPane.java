package com.daliwan.tool.ui;

import com.daliwan.tool.core.sql.SqlAssembler;
import com.daliwan.tool.core.sql.SqlDialect;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * MyBatis SQL 页：把 DEBUG 日志里的 Preparing / Parameters 两行还原成能直接执行的 SQL。
 *
 * <p>上面粘日志，下面出结果；参数按日志里的类型还原（{@code 01(String)} 会写成
 * {@code '01'}），最后按方言格式化。输入即自动处理，Ctrl+Enter 立即执行。
 */
public class SqlPane extends VBox {

    /** 示例用的日志，格式与真实项目里 Logback 打出来的完全一致。 */
    private static final String SAMPLE = """
            [2020-01-10 15:00:00] [DEBUG] getUserList.debug(159) - ==> Preparing: \
            select user_id, user_name from t_user where delete_flag = ? and status = ? \
            order by created_date desc
            [2020-01-10 15:00:00] [DEBUG] getUserList.debug(159) - ==> Parameters: N(String), 01(String)""";

    private final TextArea input = UiKit.monoArea("把 MyBatis 日志整段粘进来，例如：\n"
            + "==>  Preparing: select * from t_user where id = ?\n"
            + "==> Parameters: 1(Long)", false);
    private final SqlCodeArea output = new SqlCodeArea("拼接后的 SQL 会出现在这里");
    private final ComboBox<String> dialectBox = new ComboBox<>();
    private final CheckBox uppercase = new CheckBox("关键字大写");
    private final CheckBox pretty = new CheckBox("格式化");
    private final Label statusLabel = new Label();
    private final PauseTransition debounce = new PauseTransition(Duration.millis(350));

    public SqlPane() {
        setSpacing(12);
        setPadding(new Insets(18));

        // 日志一行动辄几百字符，横向滚动看不全，这里允许折行
        input.setWrapText(true);
        input.setMinHeight(92);
        output.setMinHeight(92);

        // 日志一行动辄几百字符，上下排布才能让日志和结果都拿到整行宽度
        SplitPane split = new SplitPane(
                UiKit.section("MyBatis 日志", input),
                UiKit.section("拼接结果", output));
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.46);
        split.getStyleClass().addAll("json-split", "split-vertical");
        VBox.setVgrow(split, Priority.ALWAYS);

        getChildren().addAll(buildToolbar(), split, buildFooter());

        debounce.setOnFinished(e -> assemble());
        input.textProperty().addListener((obs, old, value) -> debounce.playFromStart());
        dialectBox.valueProperty().addListener((obs, old, value) -> assemble());
        uppercase.selectedProperty().addListener((obs, old, value) -> assemble());
        pretty.selectedProperty().addListener((obs, old, value) -> assemble());

        addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.ENTER) {
                debounce.stop();
                assemble();
                event.consume();
            }
        });

        // 直接把手上的 .log 文件拖进来也行
        UiKit.installFileDrop(this, this::loadFiles, null);
    }

    // ---------------------------------------------------------------- 布局

    private HBox buildToolbar() {
        dialectBox.getItems().setAll(SqlDialect.labels());
        dialectBox.setValue(SqlDialect.MYSQL.label());
        dialectBox.setPrefWidth(140);
        dialectBox.setTooltip(new Tooltip("不同数据库的格式化规则略有差异，只影响排版"));

        uppercase.setSelected(true);
        uppercase.setTooltip(new Tooltip("SELECT / FROM / WHERE 等关键字转成大写"));
        pretty.setSelected(true);
        pretty.setTooltip(new Tooltip("按方言重新排版；关掉则只替换参数、保留原样"));

        Button sampleButton = new Button("示例");
        sampleButton.getStyleClass().add("ghost-button");
        sampleButton.setOnAction(e -> {
            input.setText(SAMPLE);
            input.positionCaret(0);
        });

        Button clearButton = new Button("清空");
        clearButton.getStyleClass().add("ghost-button");
        clearButton.setOnAction(e -> {
            input.clear();
            output.clear();
            UiKit.setStatus(statusLabel, "", false);
            statusLabel.setTooltip(null);
            input.requestFocus();
        });

        Button runButton = new Button("拼接");
        runButton.getStyleClass().add("primary-button");
        runButton.setDefaultButton(false);
        runButton.setOnAction(e -> {
            debounce.stop();
            assemble();
        });

        HBox bar = new HBox(10, UiKit.hint("粘贴日志即可自动拼接，Ctrl+Enter 立即执行"),
                UiKit.spacer(), dialectBox, uppercase, pretty, sampleButton, clearButton, runButton);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private HBox buildFooter() {
        statusLabel.getStyleClass().add("status");

        HBox bar = new HBox(8, statusLabel, UiKit.spacer(),
                UiKit.copyButton("复制 SQL", output::getText));
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    // ---------------------------------------------------------------- 动作

    /** 填入日志并立即处理；界面自检工具与外部调用使用。 */
    public void setLog(String log) {
        input.setText(log == null ? "" : log);
        debounce.stop();
        assemble();
    }

    private void assemble() {
        String log = input.getText();
        if (log == null || log.isBlank()) {
            output.clear();
            UiKit.setStatus(statusLabel, "", false);
            statusLabel.setTooltip(null);
            return;
        }

        SqlDialect dialect = SqlDialect.ofLabel(dialectBox.getValue());
        long startedAt = System.nanoTime();
        SqlAssembler.Result result = SqlAssembler.assemble(
                log, dialect, uppercase.isSelected(), pretty.isSelected());
        long cost = (System.nanoTime() - startedAt) / 1_000_000;

        output.setText(result.text());
        if (result.text().isEmpty()) {
            UiKit.setStatus(statusLabel, String.join("；", result.warnings()), true);
            return;
        }

        StringBuilder summary = new StringBuilder();
        summary.append("已拼接 ").append(result.statements()).append(" 条语句");
        summary.append(" · 填入 ").append(result.parameters()).append(" 个参数");
        summary.append(" · ").append(cost).append(" ms");
        if (!result.sources().isEmpty()) {
            String source = result.sources().get(0);
            summary.append(" · 来源 ").append(result.sources().size() > 1
                    ? source + " 等 " + result.sources().size() + " 处"
                    : source);
        }
        boolean hasWarning = !result.warnings().isEmpty();
        if (hasWarning) {
            summary.append(" · ⚠ ").append(result.warnings().get(0));
        }
        UiKit.setStatus(statusLabel, summary.toString(), hasWarning);
        if (hasWarning) {
            Tooltip tip = new Tooltip(String.join("\n", result.warnings()));
            tip.setWrapText(true);
            statusLabel.setTooltip(tip);
        } else {
            statusLabel.setTooltip(null);
        }
    }

    /** 拖入 .log / .txt 时读第一份内容；日志动辄几 MB，用后台线程读。 */
    private void loadFiles(List<Path> files) {
        if (files == null || files.isEmpty()) {
            return;
        }
        Path file = files.get(0);
        Thread worker = new Thread(() -> {
            try {
                String text = readLog(file);
                javafx.application.Platform.runLater(() -> {
                    input.setText(text);
                    input.positionCaret(0);
                });
            } catch (IOException e) {
                // 读文件在后台线程，改界面必须回到 FX 线程
                javafx.application.Platform.runLater(() ->
                        UiKit.setStatus(statusLabel, "读取失败：" + e.getMessage(), true));
            }
        }, "sql-log-reader");
        worker.setDaemon(true);
        worker.start();
    }

    /** 日志文件编码不确定：先按 UTF-8 严格解，失败再退回 GBK。 */
    private static String readLog(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (java.nio.charset.CharacterCodingException e) {
            return new String(bytes, Charset.forName("GBK"));
        }
    }
}
