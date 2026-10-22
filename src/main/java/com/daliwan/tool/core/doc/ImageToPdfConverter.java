package com.daliwan.tool.core.doc;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 图片转 PDF：每张图片一页，页面尺寸与图片像素一致。
 *
 * <p>只支持位图（PNG / JPEG / BMP / GIF / TIFF）。GIF 动图取第一帧，
 * 这是 PDF 的固有约束 —— 一个页面只能承载静态图像。
 */
public final class ImageToPdfConverter {

    /** 可作为输入的图片格式。 */
    private static final Set<String> SUPPORTED = Set.of(
            ".png", ".jpg", ".jpeg", ".bmp", ".gif", ".tif", ".tiff");

    /** 判断扩展名是否受支持。 */
    public static boolean isSupported(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot > 0 && SUPPORTED.contains(name.substring(dot));
    }

    /** 支持的格式列表，用于提示文案。 */
    public static String supportedExtensions() {
        return String.join(" / ", SUPPORTED.stream().sorted().toList());
    }

    /**
     * 把多张图片合成一份 PDF。
     *
     * @param images 图片路径，按列表顺序成页
     * @param output 目标 PDF
     * @return 目标路径
     * @throws ConversionException 图片格式不支持或读取失败
     */
    public Path convert(List<Path> images, Path output) throws ConversionException {
        if (images == null || images.isEmpty()) {
            throw new ConversionException("没有待转换的图片");
        }
        try (PDDocument document = new PDDocument()) {
            for (Path image : images) {
                if (!isSupported(image)) {
                    throw new ConversionException("不支持的图片格式：" + image.getFileName()
                            + "\n仅支持 " + supportedExtensions());
                }
                PDImageXObject xObject = PDImageXObject.createFromFile(image.toString(), document);
                float width = xObject.getWidth();
                float height = xObject.getHeight();
                if (width <= 0 || height <= 0) {
                    throw new ConversionException("图片尺寸异常：" + image.getFileName());
                }
                PDPage page = new PDPage(new PDRectangle(width, height));
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.drawImage(xObject, 0, 0, width, height);
                }
            }
            document.save(output.toFile());
            return output;
        } catch (IOException e) {
            throw new ConversionException("图片转 PDF 失败：" + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new ConversionException("图片无法解码，可能已损坏：" + e.getMessage(), e);
        }
    }

    /** 读取图片像素尺寸，用于界面预览与校验。 */
    public static int[] sizeOf(Path image) throws ConversionException {
        try {
            BufferedImage buffered = ImageIO.read(image.toFile());
            if (buffered == null) {
                throw new ConversionException("无法识别该图片：" + image.getFileName());
            }
            return new int[]{buffered.getWidth(), buffered.getHeight()};
        } catch (IOException e) {
            throw new ConversionException("读取图片失败：" + e.getMessage(), e);
        }
    }
}
