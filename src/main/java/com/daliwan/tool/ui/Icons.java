package com.daliwan.tool.ui;

import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

import java.util.Map;

/**
 * 线性图标集合。
 *
 * <p>全部为 16×16 网格上的描边路径，不引入图标字体或图片资源；
 * 颜色由样式表通过 {@code -fx-stroke} 统一控制，因此图标能自动跟随主题与选中态。
 */
public final class Icons {

    private static final double SIZE = 16;

    private static final Map<String, String> PATHS = Map.ofEntries(
            // 摘要：# 号
            Map.entry("hash", "M6 2 L4.7 14 M11.3 2 L10 14 M2.7 6 H13.3 M2.7 10 H13.3"),
            // 对称加密：挂锁
            Map.entry("cipher", "M3.3 7.3 H12.7 V14 H3.3 Z M5.3 7.3 V4.7 A2.7 2.7 0 0 1 10.7 4.7 V7.3"),
            // RSA：钥匙
            Map.entry("rsa", "M10.2 6.3 A2.7 2.7 0 1 1 4.8 6.3 A2.7 2.7 0 1 1 10.2 6.3"
                    + " M9.4 8.3 L14 12.9 M12 11.2 L13.4 9.8"),
            // Base64：编码块
            Map.entry("base64", "M2.7 2.7 H13.3 V13.3 H2.7 Z M5.3 6.3 H10.7 M5.3 9.3 H8.7"),
            // URL：链环
            Map.entry("url", "M6.7 9.3 A2.7 2.7 0 0 0 10.5 9.3 L12.3 7.5 A2.7 2.7 0 0 0 8.5 3.7 L7.6 4.6"
                    + " M9.3 6.7 A2.7 2.7 0 0 0 5.5 6.7 L3.7 8.5 A2.7 2.7 0 0 0 7.5 12.3 L8.4 11.4"),
            // Unicode：字形
            Map.entry("unicode", "M4.3 3 V8 A3.7 3.7 0 0 0 11.7 8 V3"),
            // 时间戳：时钟
            Map.entry("timestamp", "M13.3 8 A5.3 5.3 0 1 1 2.7 8 A5.3 5.3 0 1 1 13.3 8 M8 4.7 V8 L10.4 9.4"),
            // 二维码：码点
            Map.entry("qrcode", "M2.7 2.7 H6 V6 H2.7 Z M10 2.7 H13.3 V6 H10 Z M2.7 10 H6 V13.3 H2.7 Z"
                    + " M8.7 8.7 H10.7 V10.7 H8.7 Z M12 10.7 H13.3 V13.3 H12 Z M9.3 12.7 H10.7"),
            // OCR：扫描框
            Map.entry("ocr", "M2.7 5.3 V3.3 H5.3 M10.7 3.3 H13.3 V5.3 M13.3 10.7 V12.7 H10.7"
                    + " M5.3 12.7 H2.7 V10.7 M5.3 8 H10.7"),
            // 文本比对：双向箭头
            Map.entry("diff", "M5.3 4.7 H12 M10 2.7 L12 4.7 L10 6.7 M10.7 11.3 H4 M6 9.3 L4 11.3 L6 13.3"),
            // 图片信息：图片加信息横线
            Map.entry("img-meta", "M2.7 3.3 H13.3 V12.7 H2.7 Z M2.7 10.4 L6 7 L8.6 9.6 L10.7 7.6 L13.3 10.2"
                    + " M5.3 5.2 H7"),
            // JSON 工具：花括号
            Map.entry("json", "M6 2.7 C4 2.7 4.7 6.7 3.3 8 C4.7 9.3 4 13.3 6 13.3"
                    + " M10 2.7 C12 2.7 11.3 6.7 12.7 8 C11.3 9.3 12 13.3 10 13.3"),
            // MyBatis SQL：数据库柱体
            Map.entry("sql", "M3.1 4.7 A4.9 2.2 0 1 1 12.9 4.7 A4.9 2.2 0 1 1 3.1 4.7"
                    + " M3.1 4.7 V11.1 A4.9 2.2 0 0 0 12.9 11.1 V4.7"
                    + " M3.1 8 A4.9 2.2 0 0 0 12.9 8"),
            // UUID / 随机串：骰子
            Map.entry("uuid", "M2.7 2.7 H13.3 V13.3 H2.7 Z"
                    + " M5.7 5.7 H6.1 M9.9 5.7 H10.3 M5.7 8 H6.1 M7.8 8 H8.2 M9.9 8 H10.3"
                    + " M5.7 10.3 H6.1 M7.8 10.3 H8.2 M9.9 10.3 H10.3"),

            // Office 转 PDF：文档加箭头
            Map.entry("office2pdf", "M4 2 H9.3 L12 4.7 V14 H4 Z M9.3 2 V4.7 H12"
                    + " M8 7.3 V11.3 M6 9.3 L8 11.3 L10 9.3"),
            // PDF 拆分合并：文档堆叠
            Map.entry("pdf-tools", "M4.7 2 H10.7 V11.3 H4.7 Z M3.3 4.7 V14 H9.3"),
            // 图片转 PDF：图片
            Map.entry("img2pdf", "M2.7 3.3 H13.3 V12.7 H2.7 Z M2.7 10 L6 6.7 L8.7 9.3 L10.7 7.3 L13.3 10"
                    + " M10.4 5.2 A0.9 0.9 0 1 1 10.4 5.21"),
            // 界面设置：滑杆
            Map.entry("settings", "M2.7 5.3 H13.3 M2.7 10.7 H13.3"
                    + " M6.9 5.3 A1.6 1.6 0 1 1 3.7 5.3 A1.6 1.6 0 1 1 6.9 5.3"
                    + " M12.3 10.7 A1.6 1.6 0 1 1 9.1 10.7 A1.6 1.6 0 1 1 12.3 10.7"),
            // 深色主题：月亮
            Map.entry("moon", "M13.1 10 A5.8 5.8 0 0 1 5.9 2.8 A5.5 5.5 0 1 0 13.1 10 Z"),
            // 跟随系统：显示器
            Map.entry("auto", "M2.4 3.2 H13.6 V10.8 H2.4 Z M5.6 13.4 H10.4 M8 10.8 V13.4"),
            // 浅色主题：太阳
            Map.entry("sun", "M11.2 8 A3.2 3.2 0 1 1 4.8 8 A3.2 3.2 0 1 1 11.2 8"
                    + " M8 0.9 V2.6 M8 13.4 V15.1 M0.9 8 H2.6 M13.4 8 H15.1"
                    + " M3 3 L4.2 4.2 M11.8 11.8 L13 13 M13 3 L11.8 4.2 M4.2 11.8 L3 13"),
            // 返回
            Map.entry("back", "M9.6 3.4 L5 8 L9.6 12.6 M5 8 H13.3"),
            // 更多：向下的折角
            Map.entry("more", "M4.6 6.6 L8 10 L11.4 6.6"),
            // 关于：圆圈里一个 i（竖线 + 点，圆头笔画天然成圆点）
            Map.entry("info", "M8 14.5 A6.5 6.5 0 1 1 8 1.5 A6.5 6.5 0 1 1 8 14.5"
                    + " M8 7.4 V11.4 M8 4.6 L8 5.2")
    );

    private Icons() {
    }

    /** 按标识创建图标；未知标识返回空路径，不会抛异常。 */
    public static SVGPath of(String id) {
        SVGPath path = new SVGPath();
        path.setContent(PATHS.getOrDefault(id, ""));
        path.getStyleClass().add("icon");
        path.setStrokeLineCap(StrokeLineCap.ROUND);
        path.setStrokeLineJoin(StrokeLineJoin.ROUND);
        return path;
    }

    /** 创建指定大小的图标容器（用于侧栏等固定尺寸位置）。 */
    public static javafx.scene.layout.StackPane boxed(String id, double size) {
        javafx.scene.layout.StackPane box = new javafx.scene.layout.StackPane(of(id));
        box.setPrefSize(size, size);
        box.setMinSize(size, size);
        box.setMaxSize(size, size);
        return box;
    }
}
