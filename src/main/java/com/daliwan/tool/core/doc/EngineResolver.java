package com.daliwan.tool.core.doc;

import java.nio.file.Path;

/**
 * 按优先级选择转换引擎。
 *
 * <p>Word / Excel / PPT 全部由内置 Aspose 处理，不需要用户额外安装任何东西；
 * LibreOffice 只作为兜底：万一某个 Aspose 组件没打进包，或者以后要支持
 * Aspose 覆盖不到的格式，装了 LibreOffice 仍能转。
 */
public final class EngineResolver {

    private EngineResolver() {
    }

    /** 不选文件时的默认引擎。 */
    public static ConversionEngine resolve() throws ConversionException {
        if (AsposeEngine.isAvailable()) {
            return new AsposeEngine();
        }
        Path soffice = LibreOfficeEngine.findExecutable();
        if (soffice != null) {
            return new LibreOfficeEngine(soffice);
        }
        throw unavailable(null);
    }

    /** 按具体文件挑引擎：Aspose 认得的走内置，其余交给 LibreOffice。 */
    public static ConversionEngine resolve(Path file) throws ConversionException {
        AsposeEngine.Family family = AsposeEngine.familyOf(file);
        if (family != null && AsposeEngine.isAvailable(family)) {
            return new AsposeEngine();
        }
        Path soffice = LibreOfficeEngine.findExecutable();
        if (soffice != null) {
            return new LibreOfficeEngine(soffice);
        }
        throw unavailable(file);
    }

    /** 当前可用引擎的名称，用于界面提示；不可用时返回 null。 */
    public static String describeAvailable() {
        if (AsposeEngine.isAvailable()) {
            return "Aspose（Word · Excel · PPT 内置）";
        }
        Path soffice = LibreOfficeEngine.findExecutable();
        return soffice == null ? null : "LibreOffice";
    }

    /**
     * 引擎能力的一句话说明，含授权状态提示。
     *
     * <p>Excel / PPT 用的是官方评估版构件，导出的 PDF 每页带评估水印，
     * 这件事必须让用户提前知道，否则转换完才发现会以为是程序坏了。
     * Word 用的是项目内置的无水印构件，不需要额外说明。
     */
    public static String describeCapability() {
        if (!AsposeEngine.isAvailable()) {
            Path soffice = LibreOfficeEngine.findExecutable();
            return soffice == null
                    ? "未找到可用的转换引擎"
                    : "LibreOffice（外部引擎）：安装位置 " + soffice;
        }
        boolean excelLicensed = AsposeEngine.isLicensed(AsposeEngine.Family.EXCEL);
        boolean slidesLicensed = AsposeEngine.isLicensed(AsposeEngine.Family.SLIDES);
        if (excelLicensed && slidesLicensed) {
            return "内置 Aspose：Word / Excel / PPT 全格式，无需安装 Office";
        }
        // 一行写完：状态条只留一行高度，长文案会被窗口底部裁掉
        return "内置 Aspose：Excel / PPT 为评估版，导出 PDF 每页带水印；授权文件放程序目录 lib 即可去除";
    }

    private static ConversionException unavailable(Path file) {
        String what = file == null ? "文档" : file.getFileName().toString();
        StringBuilder message = new StringBuilder("没有可用的转换引擎，无法转换 ").append(what).append("。");
        message.append(System.lineSeparator()).append(System.lineSeparator());
        if (!AsposeEngine.isAvailable()) {
            message.append("当前程序缺少内置 Aspose 组件，可能是不完整的构建产物；")
                    .append(System.lineSeparator())
                    .append("请使用完整版大力丸发行包，或安装 LibreOffice 作为兜底：")
                    .append(System.lineSeparator())
                    .append("  https://zh-cn.libreoffice.org/download/");
        } else {
            message.append("该格式不在内置引擎的能力范围内，安装 LibreOffice 后可扩展支持：")
                    .append(System.lineSeparator())
                    .append("  https://zh-cn.libreoffice.org/download/");
        }
        return new ConversionException(message.toString());
    }
}
