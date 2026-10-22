package com.daliwan.tool.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class UnicodeCodecTest {

    @Test
    @DisplayName("中文转义为小写形式")
    void escapesChineseInLowercase() {
        assertEquals("\\u4e2d\\u6587",
                UnicodeCodec.escape("中文", UnicodeCodec.Scope.NON_ASCII, UnicodeCodec.LetterCase.LOWER));
    }

    @Test
    @DisplayName("大写形式")
    void escapesInUppercase() {
        assertEquals("\\u4E2D",
                UnicodeCodec.escape("中", UnicodeCodec.Scope.NON_ASCII, UnicodeCodec.LetterCase.UPPER));
    }

    @Test
    @DisplayName("仅非 ASCII 时保留英文与数字")
    void keepsAsciiWhenScopeIsNonAscii() {
        assertEquals("A1\\u4e2d",
                UnicodeCodec.escape("A1中", UnicodeCodec.Scope.NON_ASCII, UnicodeCodec.LetterCase.LOWER));
    }

    @Test
    @DisplayName("全部转义时 ASCII 也会被编码")
    void escapesEverythingInAllScope() {
        assertEquals("\\u0041\\u0031",
                UnicodeCodec.escape("A1", UnicodeCodec.Scope.ALL, UnicodeCodec.LetterCase.LOWER));
    }

    @Test
    @DisplayName("转义后可原样还原")
    void roundTrips() {
        String original = "中文 A1 & 符号";
        String escaped = UnicodeCodec.escape(original, UnicodeCodec.Scope.ALL, UnicodeCodec.LetterCase.LOWER);
        assertEquals(original, UnicodeCodec.unescape(escaped));
    }

    @Test
    @DisplayName("emoji 以代理对形式转义并正确还原")
    void roundTripsEmoji() {
        String emoji = "\uD83D\uDE00";
        String escaped = UnicodeCodec.escape(emoji, UnicodeCodec.Scope.ALL, UnicodeCodec.LetterCase.UPPER);
        assertEquals("\\uD83D\\uDE00", escaped);
        assertEquals(emoji, UnicodeCodec.unescape(escaped));
    }

    @Test
    @DisplayName("非法或残缺序列原样保留")
    void keepsInvalidSequences() {
        assertEquals("\\uZZZZ", UnicodeCodec.unescape("\\uZZZZ"));
        assertEquals("\\u12", UnicodeCodec.unescape("\\u12"));
        assertEquals("no escape", UnicodeCodec.unescape("no escape"));
    }

    @Test
    @DisplayName("空输入返回空串")
    void handlesEmptyInput() {
        assertEquals("", UnicodeCodec.escape("", UnicodeCodec.Scope.ALL, UnicodeCodec.LetterCase.LOWER));
        assertEquals("", UnicodeCodec.unescape(""));
    }

    @Test
    @DisplayName("转义结果与原文不同")
    void escapeActuallyChangesText() {
        assertNotEquals("中文", UnicodeCodec.escape("中文", UnicodeCodec.Scope.NON_ASCII,
                UnicodeCodec.LetterCase.LOWER));
    }
}
