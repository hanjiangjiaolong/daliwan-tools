package com.daliwan.tool.core;

import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QrCodeServiceTest {

    private String roundTrip(String text, QrCodeService.Level level) throws Exception {
        BitMatrix matrix = QrCodeService.encode(text, 300, level, 2);
        BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);
        return QrCodeService.decode(image);
    }

    @Test
    @DisplayName("生成的点阵是正方形且边长接近请求值")
    void encodesSquareMatrix() throws Exception {
        BitMatrix matrix = QrCodeService.encode("https://example.com", 400, QrCodeService.Level.MEDIUM, 2);
        assertEquals(matrix.getWidth(), matrix.getHeight());
        assertTrue(matrix.getWidth() >= 200, "实际边长：" + matrix.getWidth());
        assertTrue(matrix.getWidth() <= 400);
    }

    @Test
    @DisplayName("英文内容往返一致")
    void roundTripsAscii() throws Exception {
        String text = "https://example.com/path?query=1&lang=zh";
        assertEquals(text, roundTrip(text, QrCodeService.Level.MEDIUM));
    }

    @Test
    @DisplayName("中文内容按 UTF-8 往返一致")
    void roundTripsChinese() throws Exception {
        String text = "开发者工具箱：二维码生成与识别";
        assertEquals(text, roundTrip(text, QrCodeService.Level.MEDIUM));
    }

    @Test
    @DisplayName("高容错级别同样可还原")
    void roundTripsWithHighCorrection() throws Exception {
        String text = "容错级别 H 测试";
        assertEquals(text, roundTrip(text, QrCodeService.Level.HIGH));
    }

    @Test
    @DisplayName("多行文本往返一致")
    void roundTripsMultiline() throws Exception {
        String text = "第一行\n第二行\n第三行";
        assertEquals(text, roundTrip(text, QrCodeService.Level.QUARTILE));
    }

    @Test
    @DisplayName("空内容被拒绝")
    void rejectsEmptyText() {
        assertThrows(QrCodeException.class,
                () -> QrCodeService.encode("", 300, QrCodeService.Level.MEDIUM, 2));
        assertThrows(QrCodeException.class,
                () -> QrCodeService.encode(null, 300, QrCodeService.Level.MEDIUM, 2));
    }

    @Test
    @DisplayName("超出容量的内容给出提示")
    void rejectsOversizedText() {
        String text = "x".repeat(5000);
        QrCodeException error = assertThrows(QrCodeException.class,
                () -> QrCodeService.encode(text, 300, QrCodeService.Level.HIGH, 2));
        assertTrue(error.getMessage().contains("容量"));
    }

    @Test
    @DisplayName("空白图片识别失败并给出可读提示")
    void decodeFailsOnBlankImage() {
        BufferedImage blank = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        QrCodeException error = assertThrows(QrCodeException.class, () -> QrCodeService.decode(blank));
        assertTrue(error.getMessage().contains("未在图片中识别到二维码"));
    }

    @Test
    @DisplayName("带透明通道的 PNG 也能识别")
    void decodesImageWithAlpha() throws Exception {
        BitMatrix matrix = QrCodeService.encode("透明背景测试", 300, QrCodeService.Level.MEDIUM, 2);
        BufferedImage source = MatrixToImageWriter.toBufferedImage(matrix);
        BufferedImage withAlpha = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        withAlpha.getGraphics().drawImage(source, 0, 0, null);

        assertEquals("透明背景测试", QrCodeService.decode(withAlpha));
    }
}
