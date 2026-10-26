package com.daliwan.tool.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「关于」弹窗的数据来源。
 *
 * <p>这里不测界面，只保证弹窗拿到的是完整、可用的信息：
 * 版本号不能是空串、复制出去的排障信息必须包含让人能定位问题的几行。
 */
class AppInfoTest {

    @Test
    void versionIsNeverBlank() {
        String version = AppInfo.version();
        assertNotNull(version);
        assertFalse(version.isBlank(), "版本号不能为空，拿不到清单时应退回 1.0.0");
    }

    @Test
    void runtimeMatchesSystemProperties() {
        assertTrue(AppInfo.javaRuntime().contains(System.getProperty("java.version")),
                "Java 版本应来自运行时属性");
        assertTrue(AppInfo.osDescription().startsWith(System.getProperty("os.name")),
                "系统描述应以 os.name 开头");
    }

    @Test
    void configPathPointsAtSettingsFile() {
        assertTrue(AppInfo.configPath().endsWith("settings.properties"));
        assertTrue(AppInfo.configPath().contains(".daliwan"));
    }

    @Test
    void creditsAreComplete() {
        assertFalse(AppInfo.credits().isEmpty());
        for (AppInfo.Credit credit : AppInfo.credits()) {
            assertFalse(credit.name().isBlank());
            assertFalse(credit.purpose().isBlank(), credit.name() + " 缺用途说明");
            assertFalse(credit.license().isBlank(), credit.name() + " 缺许可证说明");
        }
    }

    @Test
    void repositoryIsPublished() {
        assertTrue(AppInfo.repository().startsWith("https://"), "仓库地址应是可直接点开的 https 链接");
        assertFalse(AppInfo.repository().isBlank());
    }

    @Test
    void summaryCarriesEverythingNeededForSupport() {
        String summary = AppInfo.summary();
        assertTrue(summary.contains(AppInfo.APP_NAME));
        assertTrue(summary.contains(AppInfo.version()));
        assertTrue(summary.contains("Java "), "应带 Java 运行时");
        assertTrue(summary.contains(AppInfo.configPath()), "应带配置文件路径，便于让用户直接打开看");
        assertTrue(summary.contains(AppInfo.repository()), "应带仓库地址，方便别人直接去看源码");
        assertTrue(summary.lines().count() >= 5);
    }
}
