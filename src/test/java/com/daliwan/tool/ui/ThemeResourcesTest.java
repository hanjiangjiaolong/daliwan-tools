package com.daliwan.tool.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 样式表与图标是运行时按 classpath 路径取的，取不到不会抛异常，只会退化成无样式界面。
 * 改包名、挪资源目录时最容易在这里漏改，用测试兜住。
 */
class ThemeResourcesTest {

    @Test
    void stylesheetsAreOnClasspath() {
        for (String path : ThemeManager.resourcePaths()) {
            assertNotNull(ThemeManager.class.getResource(path),
                    "样式表缺失（界面会变成无样式状态）：" + path);
        }
    }

    @Test
    void iconsAreOnClasspath() {
        for (int size : new int[]{16, 24, 32, 48, 64, 128, 256}) {
            String path = "/icons/icon-" + size + ".png";
            assertNotNull(ThemeManager.class.getResource(path), "图标缺失：" + path);
        }
    }
}
