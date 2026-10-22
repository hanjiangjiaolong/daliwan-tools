package com.daliwan.tool.core.doc;

import java.nio.file.Path;

/**
 * 按优先级选择转换引擎。
 *
 * <p>Word / Excel / PPT 全部由内置 Stirling 开源引擎（MIT）处理，不需要用户
 * 额外安装任何东西；LibreOffice 只作为兜底：万一内置组件没打进包
 * （例如在不完整的构建产物上运行），装了 LibreOffice 仍能转。
 */
public final class EngineResolver {

    private EngineResolver() {
    }

    /** 不选文件时的默认引擎。 */
    public static ConversionEngine resolve() throws ConversionException {
        if (StirlingEngine.isAvailable()) {
            return new StirlingEngine();
        }
        Path soffice = LibreOfficeEngine.findExecutable();
        if (soffice != null) {
            return new LibreOfficeEngine(soffice);
        }
        throw unavailable(null);
    }

    /** 按具体文件挑引擎：引擎认得的走内置，其余交给 LibreOffice。 */
    public static ConversionEngine resolve(Path file) throws ConversionException {
        if (StirlingEngine.supports(file) && StirlingEngine.isAvailable()) {
            return new StirlingEngine();
        }
        Path soffice = LibreOfficeEngine.findExecutable();
        if (soffice != null) {
            return new LibreOfficeEngine(soffice);
        }
        throw unavailable(file);
    }

    /** 当前可用引擎的名称，用于界面提示；不可用时返回 null。 */
    public static String describeAvailable() {
        if (StirlingEngine.isAvailable()) {
            return "Stirling（Word · Excel · PPT 内置）";
        }
        Path soffice = LibreOfficeEngine.findExecutable();
        return soffice == null ? null : "LibreOffice";
    }

    /**
     * 引擎能力的一句话说明，用于状态条；只留一行高度，长文案会被窗口底部裁掉。
     */
    public static String describeCapability() {
        if (!StirlingEngine.isAvailable()) {
            Path soffice = LibreOfficeEngine.findExecutable();
            return soffice == null
                    ? "未找到可用的转换引擎"
                    : "LibreOffice（外部引擎）：安装位置 " + soffice;
        }
        return "内置 Stirling 开源引擎（MIT）：Word / Excel / PPT 转 PDF，无需安装 Office";
    }

    private static ConversionException unavailable(Path file) {
        String what = file == null ? "文档" : file.getFileName().toString();
        StringBuilder message = new StringBuilder("没有可用的转换引擎，无法转换 ").append(what).append("。");
        message.append(System.lineSeparator()).append(System.lineSeparator());
        if (!StirlingEngine.isAvailable()) {
            message.append("当前程序缺少内置转换组件，可能是不完整的构建产物；")
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
