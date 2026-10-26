package com.daliwan.tool.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 行号文本的补齐规则。 */
class LineNumbersTest {

    @Test
    void padsToWidthOfTotalLines() {
        assertEquals("1", LineNumbers.label(1, 1));
        assertEquals(" 1", LineNumbers.label(1, 10), "到两位数时个位数要补一个空格，右对齐才齐");
        assertEquals("10", LineNumbers.label(10, 10));
        assertEquals("  7", LineNumbers.label(7, 100));
        assertEquals("100", LineNumbers.label(100, 100));
    }

    @Test
    void neverShrinksLongerNumbers() {
        assertEquals("1000", LineNumbers.label(1000, 10), "行数变少不该把已有的数字截掉");
    }

    @Test
    void survivesOddTotals() {
        assertEquals("1", LineNumbers.label(1, 0));
        assertEquals("1", LineNumbers.label(1, -5));
    }
}
