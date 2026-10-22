package com.daliwan.tool.ui;

import javafx.animation.TranslateTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * 开关控件。
 *
 * <p>JavaFX 没有内置 Switch，用 CheckBox 改样式很难做圆角滑块动画，
 * 这里用一个胶囊轨道加一枚圆形滑块自绘，切换时有短促的位移动画。
 */
public class ToggleSwitch extends StackPane {

    private static final double TRACK_WIDTH = 38;
    private static final double TRACK_HEIGHT = 22;
    private static final double THUMB_SIZE = 16;
    private static final double TRAVEL = 8;

    private final BooleanProperty selected = new SimpleBooleanProperty(false);
    private final Region track = new Region();
    private final Region thumb = new Region();
    private final TranslateTransition slide = new TranslateTransition(Duration.millis(140), thumb);

    public ToggleSwitch(boolean initial) {
        setPrefSize(TRACK_WIDTH, TRACK_HEIGHT);
        setMinSize(TRACK_WIDTH, TRACK_HEIGHT);
        setMaxSize(TRACK_WIDTH, TRACK_HEIGHT);
        setCursor(Cursor.HAND);

        track.getStyleClass().add("switch-track");
        track.setPrefSize(TRACK_WIDTH, TRACK_HEIGHT);
        // Region 放进 StackPane 会被拉伸铺满，必须锁死尺寸才能保持胶囊与圆点
        track.setMinSize(TRACK_WIDTH, TRACK_HEIGHT);
        track.setMaxSize(TRACK_WIDTH, TRACK_HEIGHT);

        thumb.getStyleClass().add("switch-thumb");
        thumb.setPrefSize(THUMB_SIZE, THUMB_SIZE);
        thumb.setMinSize(THUMB_SIZE, THUMB_SIZE);
        thumb.setMaxSize(THUMB_SIZE, THUMB_SIZE);
        thumb.setTranslateX(initial ? TRAVEL : -TRAVEL);

        getChildren().addAll(track, thumb);
        StackPane.setAlignment(thumb, Pos.CENTER);

        selected.set(initial);
        paintTrack();

        setOnMouseClicked(event -> selected.set(!selected.get()));
        selected.addListener((obs, oldValue, newValue) -> {
            paintTrack();
            slide.stop();
            slide.setToX(newValue ? TRAVEL : -TRAVEL);
            slide.playFromStart();
        });
    }

    public BooleanProperty selectedProperty() {
        return selected;
    }

    public boolean isSelected() {
        return selected.get();
    }

    public void setSelected(boolean value) {
        selected.set(value);
    }

    private void paintTrack() {
        track.getStyleClass().setAll("switch-track",
                selected.get() ? "switch-track-on" : "switch-track-off");
    }
}
