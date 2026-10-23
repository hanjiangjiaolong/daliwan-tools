package com.daliwan.tool.ui;

import com.daliwan.tool.util.ClipboardUtil;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * 时间戳工具：当前时间实时显示、时间戳转日期、日期转时间戳。
 */
public class TimestampPane extends VBox {

    /** 时区选项。 */
    private record ZoneOption(String label, ZoneId zone) {
        @Override
        public String toString() {
            return label;
        }
    }

    private static final List<ZoneOption> ZONES = List.of(
            new ZoneOption("系统默认（" + ZoneId.systemDefault().getId() + "）", ZoneId.systemDefault()),
            new ZoneOption("UTC", ZoneOffset.UTC),
            new ZoneOption("中国标准时间（UTC+8）", ZoneId.of("Asia/Shanghai")),
            new ZoneOption("东京（UTC+9）", ZoneId.of("Asia/Tokyo")),
            new ZoneOption("纽约", ZoneId.of("America/New_York")),
            new ZoneOption("伦敦", ZoneId.of("Europe/London"))
    );

    /**
     * 展示用的可读格式，也是 {@link #DATE_FORMATS} 里最优先尝试的那一种。
     *
     * <p>提为常量而不是每次现造：当前时间的那行每秒刷新一次，
     * {@code ofPattern} 每次都要重新解析一遍模板。
     */
    private static final DateTimeFormatter READABLE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            READABLE,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
    );

    private final Label currentSeconds = new Label("—");
    private final Label currentMillis = new Label("—");
    private final Label currentReadable = new Label("");

    private final TextField timestampInput = new TextField();
    private final ComboBox<ZoneOption> zoneBox = new ComboBox<>();
    private final VBox timestampResult = new VBox(6);
    private final Label timestampStatus = new Label();

    private final TextField dateInput = new TextField();
    private final VBox dateResult = new VBox(6);
    private final Label dateStatus = new Label();

    public TimestampPane() {
        setSpacing(12);
        setPadding(new Insets(18));

        zoneBox.getItems().addAll(ZONES);
        zoneBox.setValue(ZONES.get(0));
        zoneBox.getStyleClass().add("indent-box");

        VBox body = new VBox(12,
                buildCurrentCard(),
                buildTimestampCard(),
                buildDateCard());

        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("plain-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        getChildren().add(scroll);
        startTicker();
    }

    // ---------------------------------------------------------------- 卡片

    private VBox buildCurrentCard() {
        currentSeconds.getStyleClass().add("metric-value");
        currentMillis.getStyleClass().add("metric-value");
        currentReadable.getStyleClass().add("status");

        Label secondsLabel = new Label("秒");
        secondsLabel.getStyleClass().add("metric-label");
        Label millisLabel = new Label("毫秒");
        millisLabel.getStyleClass().add("metric-label");

        Button copySeconds = UiKit.copyButton("复制", currentSeconds::getText);
        Button copyMillis = UiKit.copyButton("复制", currentMillis::getText);

        HBox secondsValue = new HBox(10, currentSeconds, copySeconds);
        secondsValue.setAlignment(Pos.BOTTOM_LEFT);
        HBox millisValue = new HBox(10, currentMillis, copyMillis);
        millisValue.setAlignment(Pos.BOTTOM_LEFT);

        VBox secondsBox = new VBox(4, secondsLabel, secondsValue);
        VBox millisBox = new VBox(4, millisLabel, millisValue);

        HBox values = new HBox(28, secondsBox, millisBox);
        values.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(8, UiKit.cardTitle("当前时间戳"), values, currentReadable);
        card.getStyleClass().add("card");
        return card;
    }

    private VBox buildTimestampCard() {
        timestampInput.setPromptText("10 位秒 / 13 位毫秒");
        timestampInput.getStyleClass().add("json-input-field");
        timestampInput.setPrefWidth(260);
        timestampInput.textProperty().addListener((obs, oldText, newText) -> convertTimestamp());

        Button nowButton = new Button("填入当前");
        nowButton.getStyleClass().add("ghost-button");
        nowButton.setOnAction(e -> timestampInput.setText(String.valueOf(Instant.now().getEpochSecond())));

        zoneBox.valueProperty().addListener((obs, oldValue, newValue) -> convertTimestamp());

        HBox inputRow = new HBox(8, timestampInput, nowButton, zoneBox);
        inputRow.setAlignment(Pos.CENTER_LEFT);

        timestampStatus.getStyleClass().add("status");

        VBox card = new VBox(10, UiKit.cardTitle("时间戳 → 日期时间"), inputRow, timestampStatus, timestampResult);
        card.getStyleClass().add("card");
        return card;
    }

    private VBox buildDateCard() {
        dateInput.setPromptText("例如 2026-10-20 23:05:12，也支持 ISO 格式");
        dateInput.getStyleClass().add("json-input-field");
        dateInput.setPrefWidth(320);
        dateInput.setOnAction(e -> convertDate());

        Button convertButton = new Button("转换");
        convertButton.getStyleClass().add("primary-button");
        convertButton.setOnAction(e -> convertDate());

        HBox inputRow = new HBox(8, dateInput, convertButton);
        inputRow.setAlignment(Pos.CENTER_LEFT);

        dateStatus.getStyleClass().add("status");

        VBox card = new VBox(10, UiKit.cardTitle("日期时间 → 时间戳"), inputRow, dateStatus, dateResult);
        card.getStyleClass().add("card");
        return card;
    }

    // ---------------------------------------------------------------- 逻辑

    private void startTicker() {
        tick();
        Timeline ticker = new Timeline(new KeyFrame(Duration.seconds(1), e -> tick()));
        ticker.setCycleCount(Animation.INDEFINITE);
        ticker.play();
    }

    private void tick() {
        Instant now = Instant.now();
        currentSeconds.setText(String.valueOf(now.getEpochSecond()));
        currentMillis.setText(String.valueOf(now.toEpochMilli()));
        currentReadable.setText(format(now, ZoneId.systemDefault()).replace("\n", " · "));
    }

    private void convertTimestamp() {
        timestampResult.getChildren().clear();
        String raw = timestampInput.getText().trim();
        if (raw.isEmpty()) {
            UiKit.setStatus(timestampStatus, "", false);
            return;
        }
        long value;
        boolean millis;
        try {
            value = Long.parseLong(raw);
            millis = raw.replace("-", "").length() >= 13;
        } catch (NumberFormatException e) {
            UiKit.setStatus(timestampStatus, "✗ 请输入纯数字时间戳", true);
            return;
        }

        Instant instant = millis ? Instant.ofEpochMilli(value) : Instant.ofEpochSecond(value);
        ZoneId zone = zoneBox.getValue().zone();

        addRow(timestampResult, "日期时间", format(instant, zone));
        addRow(timestampResult, "ISO 8601", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(instant.atZone(zone)));
        addRow(timestampResult, "RFC 1123", DateTimeFormatter.RFC_1123_DATE_TIME.format(instant.atZone(zone)));
        addRow(timestampResult, "UTC", format(instant, ZoneOffset.UTC));
        addRow(timestampResult, "识别为", millis ? "毫秒（13 位）" : "秒（10 位）");
        UiKit.setStatus(timestampStatus, "", false);
    }

    private void convertDate() {
        dateResult.getChildren().clear();
        String raw = dateInput.getText().trim();
        if (raw.isEmpty()) {
            UiKit.setStatus(dateStatus, "", false);
            return;
        }
        ZoneId zone = zoneBox.getValue().zone();
        LocalDateTime dateTime = parseDateTime(raw);
        if (dateTime == null) {
            UiKit.setStatus(dateStatus, "✗ 无法识别该日期格式，建议使用 yyyy-MM-dd HH:mm:ss", true);
            return;
        }
        ZonedDateTime zoned = dateTime.atZone(zone);
        addRow(dateResult, "秒", String.valueOf(zoned.toEpochSecond()));
        addRow(dateResult, "毫秒", String.valueOf(zoned.toInstant().toEpochMilli()));
        addRow(dateResult, "ISO 8601", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(zoned));
        UiKit.setStatus(dateStatus, "", false);
    }

    private LocalDateTime parseDateTime(String raw) {
        String normalized = raw.replace('T', ' ').trim();
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDateTime.parse(normalized, formatter);
            } catch (DateTimeParseException ignored) {
                // 尝试下一种格式
            }
            try {
                return LocalDate.parse(normalized, formatter).atStartOfDay();
            } catch (DateTimeParseException ignored) {
                // 尝试下一种格式
            }
        }
        try {
            return ZonedDateTime.parse(raw).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String format(Instant instant, ZoneId zone) {
        return READABLE.format(instant.atZone(zone));
    }

    private void addRow(VBox container, String name, String value) {
        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("field-label");
        nameLabel.setMinWidth(84);
        nameLabel.setPrefWidth(84);

        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("result-value");

        HBox row = new HBox(10, nameLabel, valueLabel, UiKit.spacer(),
                UiKit.copyButton("复制", () -> value));
        row.setAlignment(Pos.CENTER_LEFT);
        container.getChildren().add(row);
    }
}
