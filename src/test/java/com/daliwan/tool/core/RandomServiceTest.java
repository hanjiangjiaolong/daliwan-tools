package com.daliwan.tool.core;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomServiceTest {

    @Test
    void uuidByDefaultHasHyphenAndLowerCase() {
        String value = RandomService.uuid(true, false);
        assertEquals(36, value.length());
        assertEquals(4, value.chars().filter(c -> c == '-').count());
        // 能解析回 UUID，说明格式正确
        UUID.fromString(value);
    }

    @Test
    void uuidWithoutHyphenIsUppercaseOnDemand() {
        String value = RandomService.uuid(false, true);
        assertEquals(32, value.length());
        assertFalse(value.contains("-"));
        assertEquals(value.toUpperCase(), value);
    }

    @Test
    void uuidBatchIsUnique() {
        List<String> values = RandomService.uuids(200, true, false);
        assertEquals(200, values.size());
        assertEquals(values.size(), new HashSet<>(values).size());
    }

    @Test
    void randomStringUsesOnlySelectedCharset() {
        RandomService.Options options =
                new RandomService.Options(64, true, true, false, false, false);
        String value = RandomService.randomString(options);
        assertEquals(64, value.length());
        assertTrue(value.matches("[0-9a-z]+"));
    }

    @Test
    void randomStringCanAvoidConfusingCharacters() {
        RandomService.Options options =
                new RandomService.Options(256, true, true, true, false, true);
        String value = RandomService.randomString(options);
        for (char confusing : "0Oo1lI|".toCharArray()) {
            assertFalse(value.indexOf(confusing) >= 0, "不应包含易混淆字符 " + confusing);
        }
    }

    @Test
    void randomBatchRespectsCountAndLength() {
        RandomService.Options options =
                new RandomService.Options(12, true, false, false, true, false);
        List<String> values = RandomService.randomStrings(10, options);
        assertEquals(10, values.size());
        Set<String> unique = new HashSet<>(values);
        // 长度 12、池子 10 位数字 + 22 个符号，重复概率极低
        assertEquals(10, unique.size());
        values.forEach(value -> assertEquals(12, value.length()));
    }

    @Test
    void emptyCharsetIsRejected() {
        RandomService.Options options =
                new RandomService.Options(8, false, false, false, false, false);
        assertThrows(IllegalArgumentException.class, () -> RandomService.randomString(options));
    }

    @Test
    void badLengthIsRejected() {
        RandomService.Options options =
                new RandomService.Options(0, true, false, false, false, false);
        assertThrows(IllegalArgumentException.class, () -> RandomService.randomString(options));
    }

    @Test
    void poolTextMatchesOptions() {
        RandomService.Options options =
                new RandomService.Options(8, true, false, true, false, false);
        String pool = RandomService.poolText(options);
        // 10 个数字 + 26 个大写字母
        assertEquals(36, pool.length());
        assertTrue(pool.contains("5"));
        assertTrue(pool.contains("A"));
        assertFalse(pool.contains("a"));
    }
}
