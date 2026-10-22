package com.daliwan.tool.core.doc;

import java.nio.file.Path;

/**
 * 把 Word 文档转换为 PDF 的引擎。
 *
 * <p>PDF 不是简单改扩展名就能得到的格式，必须由排版引擎按页面重新组织内容。
 * Word 里的字体、分页、表格、页眉页脚都要经过布局计算，因此本工具不自己实现转换，
 * 而是对接成熟的引擎：
 * <ul>
 *   <li>{@link LibreOfficeEngine}：开源免费，转换结果与 LibreOffice 打开时一致</li>
 *   <li>{@link AsposeEngine}：商业库，保真度最高，需授权</li>
 * </ul>
 */
public interface ConversionEngine {

    /** 引擎名称，用于日志与提示。 */
    String name();

    /**
     * 执行转换。
     *
     * @param word 源 Word 文档
     * @param pdf  目标 PDF 路径，父目录须已存在
     * @throws ConversionException 引擎不可用、源文件损坏或写出失败
     */
    void convert(Path word, Path pdf) throws ConversionException;
}
