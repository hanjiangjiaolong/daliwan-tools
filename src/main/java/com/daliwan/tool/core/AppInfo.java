package com.daliwan.tool.core;

import java.util.List;

/**
 * 应用自身信息：版本号、运行环境、第三方组件清单。
 *
 * <p>「关于」弹窗的全部数据来源。刻意放在 {@code core} 而不写在界面层：
 * 这些都是纯字符串拼装，不需要 JavaFX，也就可以直接单测；
 * 界面层 {@code ui.AboutDialog} 只负责取值和排版。
 *
 * <p>版本号从 jar 清单读取而不是硬编码，升版本时不用再改一处常量。
 */
public final class AppInfo {

    /** 应用名。 */
    public static final String APP_NAME = "大力丸";

    /** 一句话定位，用于弹窗副标题。 */
    public static final String TAGLINE = "本地运行 · 不联网 · 不上传任何数据";

    /** 源码仓库地址。 */
    public static final String REPO_URL = "https://gitee.com/hanjiangjiaolong/daliwan-tools";

    /** 拿不到 jar 清单时（开发期直接跑 class 目录）退回的版本号。 */
    private static final String FALLBACK_VERSION = "1.0.0";

    /**
     * 第三方组件条目。
     *
     * @param name    组件名
     * @param purpose 在本程序里干什么——用户关心的是「为什么这里有个东西」
     * @param license 许可证；拿不到明确声明的写「宽松开源许可」，不猜
     */
    public record Credit(String name, String purpose, String license) {
    }

    private static final List<Credit> CREDITS = List.of(
            new Credit("Stirling Office Convert / PDFBox / POI", "Word、Excel、PPT 与 PDF 互转", "MIT / Apache-2.0"),
            new Credit("Apache Fesod / POI", "表格导出 xlsx", "Apache-2.0"),
            new Credit("Apache PDFBox", "PDF 合并、拆分、提取", "Apache-2.0"),
            new Credit("Jackson", "JSON 解析、格式化与校验", "Apache-2.0"),
            new Credit("RichTextFX", "JSON / SQL 语法着色与行号", "BSD-2-Clause"),
            new Credit("ZXing", "二维码生成与识别", "Apache-2.0"),
            new Credit("RapidOCR", "图片与截图 OCR 识别", "宽松开源许可"),
            new Credit("JavaFX / OpenJDK 运行时", "界面与运行环境",
                    "GPL-2.0 with Classpath Exception"));

    private AppInfo() {
    }

    /** 版本号；取不到 jar 清单时退回 {@code 1.0.0}。 */
    public static String version() {
        Package self = AppInfo.class.getPackage();
        String version = self == null ? null : self.getImplementationVersion();
        return version == null || version.isBlank() ? FALLBACK_VERSION : version;
    }

    /** Java 运行时描述，例如 {@code 21.0.10 · Eclipse Adoptium}。 */
    public static String javaRuntime() {
        String version = System.getProperty("java.version", "?");
        String vendor = System.getProperty("java.vm.vendor", System.getProperty("java.vendor", ""));
        return vendor.isBlank() ? version : version + " · " + vendor;
    }

    /** 操作系统描述，例如 {@code Windows 11 amd64}。 */
    public static String osDescription() {
        return System.getProperty("os.name", "?") + " " + System.getProperty("os.arch", "?");
    }

    /** 配置文件路径。 */
    public static String configPath() {
        return ToolSettings.DEFAULT_FILE.toString();
    }

    /**
     * 开源协议；与仓库根目录的 {@code LICENSE} / {@code NOTICE} 保持一致。
     *
     * <p>改协议时要三处一起改：LICENSE 文件、NOTICE 归属、pom 的 {@code <licenses>}。
     */
    public static String license() {
        return "Apache-2.0";
    }

    /** 源码仓库地址；界面上做成可点击的链接。 */
    public static String repository() {
        return REPO_URL;
    }

    /** 第三方组件清单（不可变）。 */
    public static List<Credit> credits() {
        return CREDITS;
    }

    /**
     * 一段可直接贴给别人的排障信息。
     *
     * <p>用户反馈问题时最常被追问的就是这四行，能一键复制省掉来回问。
     */
    public static String summary() {
        StringBuilder text = new StringBuilder();
        text.append(APP_NAME).append(' ').append(version()).append('\n');
        text.append(TAGLINE).append('\n');
        text.append("Java ").append(javaRuntime()).append('\n');
        text.append("系统 ").append(osDescription()).append('\n');
        text.append("配置文件 ").append(configPath()).append('\n');
        text.append("仓库 ").append(REPO_URL);
        return text.toString();
    }
}
