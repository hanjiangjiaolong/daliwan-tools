package com.daliwan.tool.core.doc;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stirling 引擎的纯逻辑测试：扩展名清单与可用性判定。
 *
 * <p>可用性与运行时 JDK 版本强相关——上游按 Java 25 编译，低版本 JDK 上
 * 类加载必然失败。测试据此断言两者一致，避免「引擎明明不可用却报可用」。
 */
class StirlingEngineTest {

    @Test
    void extensionsCoverMainOfficeFormats() {
        List<String> extensions = StirlingEngine.extensions();
        for (String expected : new String[]{".doc", ".docx", ".rtf", ".odt",
                ".xls", ".xlsx", ".xlsm", ".xlsb", ".csv", ".ods",
                ".ppt", ".pptx", ".pptm", ".pps", ".ppsx", ".odp"}) {
            assertTrue(extensions.contains(expected), "缺少 " + expected);
        }
    }

    @Test
    void wpsIsNotRegistered() {
        // 上游不支持 WPS 私有格式，注册了也转不出来
        assertFalse(StirlingEngine.extensions().contains(".wps"));
    }

    @Test
    void supportsMatchesExtensionList() {
        assertTrue(StirlingEngine.supports(java.nio.file.Path.of("a.DOCX")));
        assertTrue(StirlingEngine.supports(java.nio.file.Path.of("b.xlsx")));
        assertFalse(StirlingEngine.supports(java.nio.file.Path.of("c.wps")));
        assertFalse(StirlingEngine.supports(java.nio.file.Path.of("d.txt")));
        assertFalse(StirlingEngine.supports(java.nio.file.Path.of("noext")));
    }

    @Test
    void availabilityMatchesRuntimeJdk() {
        boolean expected = Runtime.version().feature() >= 25;
        assertEquals(expected, StirlingEngine.isAvailable(),
                "JDK " + Runtime.version().feature() + " 下可用性应为 " + expected);
    }
}
