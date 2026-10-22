package com.daliwan.tool.core;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.EncodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;

/**
 * 二维码生成与识别，基于 ZXing。
 *
 * <p>本类只处理位图数据，不引用 JavaFX，界面层负责把 {@link BitMatrix} 画成图片。
 */
public final class QrCodeService {

    /** 容错级别：级别越高可恢复的破损面积越大，但相同内容需要更高的版本（更密的点阵）。 */
    public enum Level {
        LOW("L · 约 7%", ErrorCorrectionLevel.L),
        MEDIUM("M · 约 15%", ErrorCorrectionLevel.M),
        QUARTILE("Q · 约 25%", ErrorCorrectionLevel.Q),
        HIGH("H · 约 30%", ErrorCorrectionLevel.H);

        private final String label;
        private final ErrorCorrectionLevel zxingLevel;

        Level(String label, ErrorCorrectionLevel zxingLevel) {
            this.label = label;
            this.zxingLevel = zxingLevel;
        }

        public ErrorCorrectionLevel zxingLevel() {
            return zxingLevel;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private QrCodeService() {
    }

    /**
     * 生成二维码点阵。
     *
     * @param text   内容，按 UTF-8 编码
     * @param size   期望边长（像素）；实际边长可能略小，以返回矩阵为准
     * @param level  容错级别
     * @param margin 四周留白（模块数），0–4
     * @throws QrCodeException 内容为空或超出二维码容量
     */
    public static BitMatrix encode(String text, int size, Level level, int margin) throws QrCodeException {
        if (text == null || text.isEmpty()) {
            throw new QrCodeException("内容为空，无法生成二维码");
        }
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.ERROR_CORRECTION, level.zxingLevel());
        hints.put(EncodeHintType.MARGIN, Math.max(0, Math.min(4, margin)));
        try {
            return new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints);
        } catch (WriterException e) {
            throw new QrCodeException("内容过长，超出二维码容量（约 2900 字节），请缩短后重试", e);
        }
    }

    /**
     * 识别图片中的二维码，支持常见位图格式与带透明通道的 PNG。
     *
     * @param image 源图
     * @return 二维码内容
     * @throws QrCodeException 未识别到二维码
     */
    public static String decode(BufferedImage image) throws QrCodeException {
        if (image == null) {
            throw new QrCodeException("图片为空");
        }
        // 透明背景会干扰二值化，先铺一层白底
        BufferedImage flattened = flattenOnWhite(image);
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        try {
            LuminanceSource source = new BufferedImageLuminanceSource(flattened);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            Result result = new MultiFormatReader().decode(bitmap, hints);
            return result.getText();
        } catch (NotFoundException e) {
            throw new QrCodeException("未在图片中识别到二维码，请确认图片清晰、未被裁切或遮挡", e);
        }
    }

    private static BufferedImage flattenOnWhite(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        BufferedImage out = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, source.getWidth(), source.getHeight());
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return out;
    }
}
