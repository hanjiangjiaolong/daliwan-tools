package com.daliwan.tool.core.doc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 扩展名到 Aspose 产品的映射；不加载 Aspose 本体，纯字符串判断。 */
class AsposeEngineTest {

    @Test
    @DisplayName("Word 系归 Words")
    void mapsWordFamily() {
        assertEquals(AsposeEngine.Family.WORD, AsposeEngine.familyOf(Path.of("报告.docx")));
        assertEquals(AsposeEngine.Family.WORD, AsposeEngine.familyOf(Path.of("a.DOC")));
        assertEquals(AsposeEngine.Family.WORD, AsposeEngine.familyOf(Path.of("a.rtf")));
    }

    @Test
    @DisplayName("Excel 系归 Cells")
    void mapsExcelFamily() {
        assertEquals(AsposeEngine.Family.EXCEL, AsposeEngine.familyOf(Path.of("清单.xlsx")));
        assertEquals(AsposeEngine.Family.EXCEL, AsposeEngine.familyOf(Path.of("a.xls")));
        assertEquals(AsposeEngine.Family.EXCEL, AsposeEngine.familyOf(Path.of("a.xlsm")));
        assertEquals(AsposeEngine.Family.EXCEL, AsposeEngine.familyOf(Path.of("a.csv")));
    }

    @Test
    @DisplayName("PPT 系归 Slides")
    void mapsSlidesFamily() {
        assertEquals(AsposeEngine.Family.SLIDES, AsposeEngine.familyOf(Path.of("汇报.pptx")));
        assertEquals(AsposeEngine.Family.SLIDES, AsposeEngine.familyOf(Path.of("a.ppt")));
        assertEquals(AsposeEngine.Family.SLIDES, AsposeEngine.familyOf(Path.of("a.ppsx")));
    }

    @Test
    @DisplayName("不认识的扩展名返回 null，交给其它引擎或直接拒绝")
    void rejectsUnknown() {
        assertNull(AsposeEngine.familyOf(Path.of("a.txt")));
        assertNull(AsposeEngine.familyOf(Path.of("a.pdf")));
        assertNull(AsposeEngine.familyOf(Path.of("noextension")));
        assertFalse(AsposeEngine.supports(Path.of("a.zip")));
    }

    @Test
    @DisplayName("对外公布的扩展名与三族合并结果一致")
    void exposesAllExtensions() {
        assertTrue(AsposeEngine.extensions().containsAll(
                java.util.List.of(".doc", ".docx", ".xls", ".xlsx", ".xlsm", ".ppt", ".pptx")));
    }
}
