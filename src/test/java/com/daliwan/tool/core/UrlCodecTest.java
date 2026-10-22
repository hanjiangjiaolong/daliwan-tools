package com.daliwan.tool.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UrlCodecTest {

    private static final String TEXT = "中文 abc&x=1";

    @Test
    @DisplayName("RFC 3986 编码：空格转 %20，中文按 UTF-8 百分号编码")
    void encodesRfc3986() {
        assertEquals("%E4%B8%AD%E6%96%87%20abc%26x%3D1",
                UrlCodec.encodeRfc3986(TEXT, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("表单编码：空格转 +")
    void encodesForm() {
        String encoded = UrlCodec.encodeForm("a b", StandardCharsets.UTF_8);
        assertEquals("a+b", encoded);
    }

    @Test
    @DisplayName("两种规则均可还原原文")
    void roundTripsBothRules() {
        for (UrlCodec.Rule rule : UrlCodec.Rule.values()) {
            String encoded = UrlCodec.encode(TEXT, rule, StandardCharsets.UTF_8);
            assertEquals(TEXT, UrlCodec.decode(encoded, rule, StandardCharsets.UTF_8), rule.name());
        }
    }

    @Test
    @DisplayName("RFC 3986 解码不把 + 当作空格")
    void rfc3986KeepsPlus() {
        assertEquals("a+b", UrlCodec.decodeRfc3986("a+b", StandardCharsets.UTF_8));
        assertEquals("a+b", UrlCodec.decodeRfc3986("a%2Bb", StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("表单解码把 + 还原为空格")
    void formDecodeTurnsPlusIntoSpace() {
        assertEquals("a b", UrlCodec.decodeForm("a+b", StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("小写百分号转义也能解码")
    void decodesLowercaseEscapes() {
        assertEquals("中", UrlCodec.decodeRfc3986("%e4%b8%ad", StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("残缺的转义序列原样保留")
    void keepsIncompleteEscape() {
        assertEquals("%zz", UrlCodec.decodeRfc3986("%zz", StandardCharsets.UTF_8));
        assertEquals("%4", UrlCodec.decodeRfc3986("%4", StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("无保留字符原样输出")
    void keepsUnreservedCharacters() {
        String plain = "abc-_.~123";
        assertEquals(plain, UrlCodec.encodeRfc3986(plain, StandardCharsets.UTF_8));
    }
}
