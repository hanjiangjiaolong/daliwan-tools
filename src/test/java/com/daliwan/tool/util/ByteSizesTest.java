package com.daliwan.tool.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ByteSizesTest {

    @Test
    @DisplayName("不足 1 KB 直接按字节显示")
    void bytes() {
        assertEquals("0 B", ByteSizes.format(0));
        assertEquals("812 B", ByteSizes.format(812));
        assertEquals("1023 B", ByteSizes.format(1023));
    }

    @Test
    @DisplayName("KB 一位小数，MB / GB 两位")
    void units() {
        assertEquals("1.0 KB", ByteSizes.format(1024));
        assertEquals("15.4 KB", ByteSizes.format(15_800));
        assertEquals("1.00 MB", ByteSizes.format(1024 * 1024));
        assertEquals("2.31 MB", ByteSizes.format(2_425_000));
        assertEquals("1.05 GB", ByteSizes.format(1_128_000_000L));
    }

    @Test
    @DisplayName("边界值不会落到下一档")
    void boundaries() {
        assertEquals("1024.00 MB", ByteSizes.format(1024L * 1024 * 1024 - 1));
    }
}
