package com.mpzlog.gui;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.util.List;

public final class ClipboardUtil {

    private ClipboardUtil() {
    }

    public static void copyText(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    public static void copyLines(List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(lines.get(i));
        }
        copyText(sb.toString());
    }

}
