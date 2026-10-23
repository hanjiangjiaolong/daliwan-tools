package com.daliwan.tool.core.doc;

import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.multipdf.PDFMergerUtility;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * PDF 合并：把多份 PDF 按顺序拼成一份。
 *
 * <p>使用 PDFBox 自带的 {@link PDFMergerUtility}，它会把源文档的页面连同样式、
 * 书签与页面标签一并合并，不需要手工搬运资源。
 */
public final class PdfMerger {

    /**
     * 合并 PDF。
     *
     * @param inputs 源文件，按列表顺序拼接
     * @param output 目标文件，父目录须存在
     * @return 目标文件路径
     * @throws ConversionException 文件不足两个、加密或损坏
     */
    public Path merge(List<Path> inputs, Path output) throws ConversionException {
        if (inputs == null || inputs.isEmpty()) {
            throw new ConversionException("没有待合并的 PDF");
        }
        if (inputs.size() < 2) {
            throw new ConversionException("至少需要两个 PDF 才能合并");
        }
        try {
            PDFMergerUtility merger = new PDFMergerUtility();
            merger.setDestinationFileName(output.toString());
            for (Path input : inputs) {
                merger.addSource(input.toFile());
            }
            merger.mergeDocuments(IOUtils.createMemoryOnlyStreamCache());
            return output;
        } catch (IOException e) {
            throw new ConversionException("合并失败：" + e.getMessage(), e);
        }
    }

}
