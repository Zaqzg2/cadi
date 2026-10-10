package com.inventorysmartai.app.data.assistant.files;

import java.util.Locale;

/** File names that are safe to write to disk and to hand to other apps, Arabic letters kept. */
public final class FileNames {

    private static final int MAX_BASE_CHARS = 60;

    private FileNames() {
    }

    /**
     * "تقرير: المبيعات/1" + ".xlsx" -> "تقرير- المبيعات-1.xlsx". Path separators and characters that file systems or
     * share targets reject become "-", control characters vanish, the length is capped, and the extension is added once.
     * {@code extension} may be empty (keep the name as it came) and is given without the dot.
     */
    public static String safe(String title, String fallback, String extension) {
        String base = title == null ? "" : title;
        String ext = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        if (!ext.isEmpty() && base.toLowerCase(Locale.ROOT).endsWith("." + ext)) {
            base = base.substring(0, base.length() - ext.length() - 1);
        }
        StringBuilder sb = new StringBuilder(base.length());
        for (int i = 0; i < base.length(); i++) {
            char c = base.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                continue;
            }
            if ("\\/:*?\"<>|".indexOf(c) >= 0) {
                sb.append('-');
            } else {
                sb.append(c);
            }
        }
        String cleaned = CellValues.clean(sb.toString());
        while (cleaned.startsWith(".")) {
            cleaned = cleaned.substring(1);
        }
        while (cleaned.endsWith(".") || cleaned.endsWith(" ")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        if (cleaned.length() > MAX_BASE_CHARS) {
            cleaned = cleaned.substring(0, MAX_BASE_CHARS).trim();
        }
        if (cleaned.isEmpty()) {
            cleaned = fallback == null || fallback.isEmpty() ? "ملف" : fallback;
        }
        return ext.isEmpty() ? cleaned : cleaned + "." + ext;
    }
}
