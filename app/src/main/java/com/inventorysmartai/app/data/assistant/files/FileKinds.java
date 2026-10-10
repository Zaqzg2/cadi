package com.inventorysmartai.app.data.assistant.files;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * What an attached file really is, decided from its BYTES first (a file named .jpg that is really a PDF is a PDF) and
 * only then from its name. Unsupported files are refused with an Arabic message that says what to do instead.
 */
public final class FileKinds {

    public enum Kind { SPREADSHEET, TEXT_DOCUMENT, IMAGE, PDF }

    public static final class Detected {
        public final Kind kind;
        public final String mimeType;
        public final String extension;

        Detected(Kind kind, String mimeType, String extension) {
            this.kind = kind;
            this.mimeType = mimeType;
            this.extension = extension;
        }
    }

    public static final String XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private static final int MAX_ZIP_ENTRIES_SCANNED = 500;

    private FileKinds() {
    }

    /** @throws IllegalArgumentException with an Arabic explanation when the file cannot be used */
    public static Detected detect(String name, String declaredMime, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("الملف فارغ");
        }
        if (starts(bytes, 0x25, 0x50, 0x44, 0x46)) {
            return new Detected(Kind.PDF, "application/pdf", "pdf");
        }
        if (starts(bytes, 0xFF, 0xD8, 0xFF)) {
            return new Detected(Kind.IMAGE, "image/jpeg", "jpg");
        }
        if (starts(bytes, 0x89, 0x50, 0x4E, 0x47)) {
            return new Detected(Kind.IMAGE, "image/png", "png");
        }
        if (bytes.length >= 12 && starts(bytes, 0x52, 0x49, 0x46, 0x46) && ascii(bytes, 8, 4).equals("WEBP")) {
            return new Detected(Kind.IMAGE, "image/webp", "webp");
        }
        if (bytes.length >= 12 && ascii(bytes, 4, 4).equals("ftyp")) {
            String brand = ascii(bytes, 8, 4);
            if (brand.startsWith("hei") || brand.startsWith("hev") || brand.equals("mif1") || brand.equals("msf1")) {
                return new Detected(Kind.IMAGE, "image/heic", "heic");
            }
        }
        if (starts(bytes, 0x47, 0x49, 0x46, 0x38) || starts(bytes, 0x42, 0x4D)) {
            throw new IllegalArgumentException("صيغة هذه الصورة غير مدعومة. استخدم صورة JPEG أو PNG");
        }
        if (starts(bytes, 0x50, 0x4B, 0x03, 0x04)) {
            String found = zipKind(bytes);
            if (found.equals("xlsx")) {
                return new Detected(Kind.SPREADSHEET, XLSX_MIME, "xlsx");
            }
            if (found.equals("docx")) {
                return new Detected(Kind.TEXT_DOCUMENT, DOCX_MIME, "docx");
            }
            throw new IllegalArgumentException("هذا النوع من الملفات المضغوطة غير مدعوم. المدعوم: Excel (.xlsx) وWord (.docx)");
        }
        if (starts(bytes, 0xD0, 0xCF, 0x11, 0xE0)) {
            throw new IllegalArgumentException("صيغة Office القديمة (.xls / .doc) غير مدعومة. احفظ الملف بصيغة .xlsx أو .docx ثم أرفقه");
        }

        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        String ext = lower.contains(".") ? lower.substring(lower.lastIndexOf('.') + 1) : "";
        String mime = declaredMime == null ? "" : declaredMime.toLowerCase(Locale.ROOT);
        if (ext.equals("csv") || ext.equals("tsv") || mime.contains("csv")) {
            return new Detected(Kind.SPREADSHEET, "text/csv", "csv");
        }
        if (ext.equals("txt") || ext.equals("md") || ext.equals("text") || ext.equals("log") || mime.startsWith("text/")) {
            return new Detected(Kind.TEXT_DOCUMENT, "text/plain", "txt");
        }
        throw new IllegalArgumentException("نوع الملف غير مدعوم. المدعوم: Excel (.xlsx) وCSV وWord (.docx) وPDF والصور (JPEG/PNG) والنصوص (.txt)");
    }

    private static String zipKind(byte[] bytes) {
        ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes));
        try {
            ZipEntry entry;
            int scanned = 0;
            while ((entry = zip.getNextEntry()) != null && scanned++ < MAX_ZIP_ENTRIES_SCANNED) {
                String n = entry.getName();
                if (n.equals("xl/workbook.xml")) {
                    return "xlsx";
                }
                if (n.equals("word/document.xml")) {
                    return "docx";
                }
            }
        } catch (IOException e) {
            return "";
        } finally {
            try {
                zip.close();
            } catch (IOException ignored) {
                // nothing to do
            }
        }
        return "";
    }

    private static boolean starts(byte[] bytes, int... signature) {
        if (bytes.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((bytes[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private static String ascii(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, StandardCharsets.US_ASCII);
    }
}
