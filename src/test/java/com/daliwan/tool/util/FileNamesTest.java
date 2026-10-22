package com.daliwan.tool.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileNamesTest {

    @Test
    @DisplayName("去掉最后一个扩展名；没有点或点在首位时原样返回")
    void baseNameStripsExtension() {
        assertEquals("合同", FileNames.baseName("合同.pdf"));
        assertEquals("归档.tar", FileNames.baseName("归档.tar.gz"));
        assertEquals("README", FileNames.baseName("README"));
    }

    @Test
    @DisplayName("扩展名统一小写返回")
    void extensionIsLowerCased() {
        assertEquals(".pdf", FileNames.extension("合同.PDF"));
        assertEquals(".docx", FileNames.extension("报告.docx"));
        assertEquals("", FileNames.extension("无扩展名"));
    }

    @Test
    @DisplayName("目标不存在时直接用原名")
    void keepsNameWhenFree(@TempDir Path dir) {
        assertEquals(dir.resolve("结果.pdf"), FileNames.uniqueTarget(dir, "结果.pdf"));
    }

    @Test
    @DisplayName("重名时追加 (1)、(2)")
    void appendsIndexWhenTaken(@TempDir Path dir) throws IOException {
        Files.createFile(dir.resolve("结果.pdf"));
        assertEquals(dir.resolve("结果(1).pdf"), FileNames.uniqueTarget(dir, "结果.pdf"));

        Files.createFile(dir.resolve("结果(1).pdf"));
        assertEquals(dir.resolve("结果(2).pdf"), FileNames.uniqueTarget(dir, "结果.pdf"));
    }

    @Test
    @DisplayName("后缀大写时也要能找到空位（大小写不一致曾导致死循环）")
    void handlesUpperCaseExtension(@TempDir Path dir) throws IOException {
        Files.createFile(dir.resolve("报告.PDF"));
        Path target = FileNames.uniqueTarget(dir, "报告.PDF");
        assertEquals(dir.resolve("报告(1).PDF"), target);
    }
}
