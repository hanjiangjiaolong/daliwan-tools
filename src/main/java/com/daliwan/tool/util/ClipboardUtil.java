package com.daliwan.tool.util;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

/** 系统剪贴板操作封装。 */
public final class ClipboardUtil {

    private ClipboardUtil() {
    }

    /**
     * 把文本写入系统剪贴板。
     *
     * @param text 待复制文本；为 null 或空串时忽略
     * @return 是否写入成功
     */
    public static boolean copy(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        return Clipboard.getSystemClipboard().setContent(content);
    }
}
