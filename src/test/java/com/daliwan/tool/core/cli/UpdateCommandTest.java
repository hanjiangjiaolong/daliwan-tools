package com.daliwan.tool.core.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 更新开关命令行的参数解析。
 *
 * <p>只测解析：{@code run} 会真的去改系统设置（没有管理员时还会弹 UAC），
 * 单元测试里不能碰。
 */
class UpdateCommandTest {

    @Test
    @DisplayName("认出 --update-block 及其取值")
    void readsValue() {
        String[] args = {"--update-block", "on"};
        assertTrue(UpdateCommand.matches(args));
        assertEquals("on", UpdateCommand.valueOf(args));
    }

    @Test
    @DisplayName("开关大小写不敏感")
    void flagIsCaseInsensitive() {
        String[] args = {"--UPDATE-BLOCK", "status"};
        assertTrue(UpdateCommand.matches(args));
        assertEquals("status", UpdateCommand.valueOf(args));
    }

    @Test
    @DisplayName("开关在末尾没有取值时不认")
    void flagWithoutValue() {
        assertFalse(UpdateCommand.matches(new String[]{"--update-block"}));
    }

    @Test
    @DisplayName("普通启动参数不误判")
    void unrelatedArguments() {
        assertFalse(UpdateCommand.matches(new String[]{}));
        assertFalse(UpdateCommand.matches((String[]) null));
        assertFalse(UpdateCommand.matches(new String[]{"--to-pdf", "a.docx"}));
    }

    @Test
    @DisplayName("取值取自开关后一个参数，不受其它参数干扰")
    void valueFollowsFlag() {
        String[] args = {"--to-pdf", "--update-block", "off", "x.pdf"};
        assertEquals("off", UpdateCommand.valueOf(args));
    }
}
