package com.daliwan.tool.core.win;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Windows 更新开关的测试。
 *
 * <p>刻意只测「纯字符串」那一半：脚本怎么生成、输出怎么解析、状态怎么判定。
 * 真正会改系统的 {@code apply / relaunchElevated / state} 一个都不调——
 * 测试跑在 Windows 上时它们会真的去改服务和注册表，那不是单元测试该干的事。
 */
class WindowsUpdateGuardTest {

    /** 一段「全部阻止成功」的查询输出。 */
    private static final String BLOCKED_OUTPUT = String.join("\n",
            "SERVICE|wuauserv|Disabled",
            "SERVICE|UsoSvc|Disabled",
            "SERVICE|DoSvc|Manual",
            "POLICY|NoAutoUpdate|1",
            "TASK|Schedule Scan|Disabled",
            "TASK|PerformRemediation|Disabled",
            "END|query");

    private static final String ALLOWED_OUTPUT = String.join("\n",
            "SERVICE|wuauserv|Manual",
            "SERVICE|UsoSvc|Manual",
            "SERVICE|DoSvc|Automatic",
            "POLICY|NoAutoUpdate|absent",
            "TASK|Schedule Scan|Ready",
            "END|query");

    @Test
    @DisplayName("阻止脚本禁用更新服务、写策略、停用计划任务")
    void blockScriptDisablesEverything() {
        String script = WindowsUpdateGuard.script(WindowsUpdateGuard.Action.BLOCK);

        assertTrue(script.contains("sc.exe config wuauserv start= disabled"), "主服务必须禁用");
        assertTrue(script.contains("sc.exe config UsoSvc start= disabled"), "更新编排服务必须禁用");
        assertTrue(script.contains("NoAutoUpdate' -Value 1"), "策略置 1");
        assertTrue(script.contains("Disable-ScheduledTask"), "停用计划任务");
        assertTrue(script.contains("WaaSMedic"), "微软的自我修复任务必须一起停，否则过几天会被改回去");
        assertTrue(script.contains("DONE|block"));
    }

    @Test
    @DisplayName("传递优化与 BITS 只降级为手动，不能直接禁")
    void blockScriptOnlyDegradesSharedServices() {
        String script = WindowsUpdateGuard.script(WindowsUpdateGuard.Action.BLOCK);

        assertTrue(script.contains("sc.exe config DoSvc start= demand"));
        assertTrue(script.contains("sc.exe config BITS start= demand"));
        assertFalse(script.contains("sc.exe config BITS start= disabled"),
                "BITS 还承担应用商店等正常下载，禁掉会连累别的功能");
    }

    @Test
    @DisplayName("恢复脚本把服务还原、清策略、启用任务，且与阻止脚本互斥")
    void allowScriptRestores() {
        String script = WindowsUpdateGuard.script(WindowsUpdateGuard.Action.ALLOW);

        assertTrue(script.contains("sc.exe config wuauserv start= demand"));
        assertTrue(script.contains("sc.exe config BITS start= delayed-auto"), "BITS 默认是延迟启动的自动");
        assertTrue(script.contains("Remove-ItemProperty"), "清掉策略值，而不是留个 0");
        assertTrue(script.contains("Enable-ScheduledTask"));
        assertFalse(script.contains("Disable-ScheduledTask"));
        assertFalse(script.contains("NoAutoUpdate' -Value 1"));
        assertTrue(script.contains("DONE|allow"));
    }

    @Test
    @DisplayName("查询脚本只读：既不改服务也不改任务")
    void queryScriptIsReadOnly() {
        String script = WindowsUpdateGuard.queryScript();

        assertTrue(script.contains("Get-Service"));
        assertFalse(script.contains("sc.exe config"), "查询绝不能改启动类型");
        assertFalse(script.contains("Disable-ScheduledTask"));
        assertFalse(script.contains("Enable-ScheduledTask"));
        assertFalse(script.contains("Remove-ItemProperty"));
    }

    @Test
    @DisplayName("脚本只输出 ASCII 标记行，解析不受系统语言影响")
    void scriptOutputIsLanguageNeutral() {
        for (WindowsUpdateGuard.Action action : WindowsUpdateGuard.Action.values()) {
            String script = WindowsUpdateGuard.script(action);
            assertTrue(script.contains("Write-Output \"SERVICE|"), "结果以标记行回传");
            assertTrue(script.contains("[Console]::OutputEncoding"));
        }
    }

    @Test
    @DisplayName("全阻止的输出解析为 BLOCKED")
    void parseBlocked() {
        WindowsUpdateGuard.State state = WindowsUpdateGuard.parseState(BLOCKED_OUTPUT);

        assertEquals("Disabled", state.services().get("wuauserv"));
        assertTrue(state.noAutoUpdate());
        assertEquals(WindowsUpdateGuard.Mode.BLOCKED, state.mode());
        assertTrue(state.summary().contains("已阻止"));
    }

    @Test
    @DisplayName("默认状态的输出解析为 ALLOWED")
    void parseAllowed() {
        WindowsUpdateGuard.State state = WindowsUpdateGuard.parseState(ALLOWED_OUTPUT);

        assertEquals("Manual", state.services().get("wuauserv"));
        assertNull(state.noAutoUpdate(), "策略键不存在时应为 null，而不是 false");
        assertEquals(WindowsUpdateGuard.Mode.ALLOWED, state.mode());
    }

    @Test
    @DisplayName("只关了一半算 PARTIAL，提示用户再点一次")
    void parsePartial() {
        WindowsUpdateGuard.State state = WindowsUpdateGuard.parseState(String.join("\n",
                "SERVICE|wuauserv|Disabled",
                "POLICY|NoAutoUpdate|absent",
                "TASK|Schedule Scan|Ready"));

        assertEquals(WindowsUpdateGuard.Mode.PARTIAL, state.mode());
        assertTrue(state.summary().contains("再点一次"));
    }

    @Test
    @DisplayName("解析不出来的输出是 UNKNOWN，不谎报状态")
    void parseUnknown() {
        assertEquals(WindowsUpdateGuard.Mode.UNKNOWN, WindowsUpdateGuard.parseState("").mode());
        assertEquals(WindowsUpdateGuard.Mode.UNKNOWN, WindowsUpdateGuard.parseState(null).mode());
        assertEquals(WindowsUpdateGuard.Mode.UNKNOWN,
                WindowsUpdateGuard.parseState("一堆中文报错\n完全不是标记行").mode());
    }

    @Test
    @DisplayName("解析时跳过无关行，误报的本地化错误不会污染状态")
    void parseSkipsNoise() {
        WindowsUpdateGuard.State state = WindowsUpdateGuard.parseState(String.join("\n",
                "Get-Service : 无法找到服务",
                "",
                "SERVICE|wuauserv|Manual",
                "无关|只有两段",
                "POLICY|NoAutoUpdate|0"));

        assertEquals(1, state.services().size());
        assertEquals(WindowsUpdateGuard.Mode.ALLOWED, state.mode());
    }

    @Test
    @DisplayName("空状态对象的明细不会抛异常")
    void emptyStateIsSafe() {
        WindowsUpdateGuard.State state = new WindowsUpdateGuard.State(Map.of(), null, Map.of());
        assertEquals(WindowsUpdateGuard.Mode.UNKNOWN, state.mode());
        assertTrue(state.summary().contains("没查到"));
    }

    @Test
    @DisplayName("命令行取值与动作的互相转换")
    void argumentMapping() {
        assertEquals("on", WindowsUpdateGuard.argumentOf(WindowsUpdateGuard.Action.BLOCK));
        assertEquals("off", WindowsUpdateGuard.argumentOf(WindowsUpdateGuard.Action.ALLOW));
        assertEquals(WindowsUpdateGuard.Action.BLOCK, WindowsUpdateGuard.actionOf("on"));
        assertEquals(WindowsUpdateGuard.Action.ALLOW, WindowsUpdateGuard.actionOf("OFF"));
        assertEquals(WindowsUpdateGuard.Action.BLOCK, WindowsUpdateGuard.actionOf("disable"));
        assertNull(WindowsUpdateGuard.actionOf("maybe"));
        assertNull(WindowsUpdateGuard.actionOf(null));
    }
}
