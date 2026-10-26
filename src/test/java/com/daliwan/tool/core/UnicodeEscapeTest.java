package com.daliwan.tool.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 二次转义的还原规则。
 *
 * <p>文本里写的是两个反斜杠加 u（{@code \\u4f70} 这种），
 * 解析器只会解掉一层，剩下的要靠这里。
 * 下面用 {@link #BS}、{@link #Q} 两个常量拼输入，
 * 免得源码里满屏反斜杠看不清到底测的是什么。
 */
class UnicodeEscapeTest {

    /** 文本里的两个反斜杠（源码里每个反斜杠要写两遍）。 */
    private static final String BS = "\\\\";

    /** JSON 文本里的转义引号 {@code \"}。 */
    private static final String Q = "\\\"";

    @Test
    void decodesDoubleEscapedText() {
        assertEquals("佰赢控股",
                UnicodeEscape.decode(BS + "u4f70" + BS + "u8d62" + BS + "u63a7" + BS + "u80a1"));
        assertEquals(Q + "name" + Q + ":" + Q + "佰赢控股" + Q,
                UnicodeEscape.decode(Q + "name" + Q + ":" + Q
                        + BS + "u4f70" + BS + "u8d62" + BS + "u63a7" + BS + "u80a1" + Q));
    }

    @Test
    void joinsSurrogatePairs() {
        assertEquals("笑 \uD83D\uDE00", UnicodeEscape.decode("笑 " + BS + "uD83D" + BS + "uDE00"));
    }

    @Test
    void leavesPlainTextAlone() {
        String plain = Q + "path" + Q + ":" + Q + "C:" + BS + "users" + BS + "temp" + Q;
        assertEquals(plain, UnicodeEscape.decode(plain), "u 后面不是十六进制的不动，避免误伤路径");
    }

    @Test
    void leavesSingleEscapesForParser() {
        // 单层的 \u4f70 是标准 JSON 转义，交给解析器，这里不动它
        String single = Q + "name" + Q + ":" + Q + "\\u4f70" + Q;
        assertEquals(single, UnicodeEscape.decode(single));
    }

    @Test
    void handlesTextWithoutBackslash() {
        String text = "没有反斜杠的普通文本";
        assertSame(text, UnicodeEscape.decode(text), "没有可还原内容时应返回原对象");
        assertEquals("", UnicodeEscape.decode(""));
        assertNull(UnicodeEscape.decode(null));
    }

    @Test
    void decodesRealPayload() {
        String text = Q + "store_name" + Q + ":" + Q
                + BS + "u4f70" + BS + "u8d62" + BS + "u63a7" + BS + "u80a1"
                + BS + "u96c6" + BS + "u56e2" + Q;
        assertEquals(Q + "store_name" + Q + ":" + Q + "佰赢控股集团" + Q,
                UnicodeEscape.decode(text));
    }
}
