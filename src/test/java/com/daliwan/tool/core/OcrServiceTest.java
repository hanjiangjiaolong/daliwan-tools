package com.daliwan.tool.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OcrServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void 图片不存在时直接报错() throws Exception {
        Path missing = tempDir.resolve("不存在.png");

        OcrException error = assertThrows(OcrException.class,
                () -> OcrService.recognize(missing, OcrService.Options.DEFAULT));
        assertTrue(error.getMessage().contains("图片不存在"), error.getMessage());
    }

    @Test
    void 图片为目录时也算不存在() {
        assertThrows(OcrException.class,
                () -> OcrService.recognize(tempDir, OcrService.Options.DEFAULT));
    }

    @Test
    void 默认选项不重复创建实例() {
        assertSame(OcrService.Options.DEFAULT, OcrService.Options.of(false));
        assertFalse(OcrService.Options.DEFAULT.detectAngle());
        assertTrue(OcrService.Options.of(true).detectAngle());
    }

    @Test
    void 引擎描述面向用户且不为空() {
        String text = OcrService.describeEngine();
        assertNotNull(text);
        assertFalse(text.isBlank());
    }

    @Test
    void 空参数直接报错() {
        assertThrows(OcrException.class, () -> OcrService.recognize(null, null));
        assertThrows(OcrException.class, () -> OcrService.recognizeBlocks(null, null));
        // options 为 null 时按「不纠正方向」处理，不因空指针中断
        assertThrows(OcrException.class,
                () -> OcrService.recognize(tempDir.resolve("nope.png"), null));
    }
}
