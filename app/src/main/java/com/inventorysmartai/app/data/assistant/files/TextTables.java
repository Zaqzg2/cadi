package com.inventorysmartai.app.data.assistant.files;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * The text-shaped inputs: CSV files, Markdown tables (what OCR returns for a photographed table) and the text of a
 * Word document. Each is turned into the same {@link SheetData} / plain text the rest of the engine uses.
 */
public final class TextTables {

    public static final int MAX_CSV_ROWS = 50000;
    private static final int MAX_DOCX_PART_BYTES = 20 * 1024 * 1024;

    private static final Pattern MD_SEPARATOR = Pattern.compile("^\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?$");
    private static final Pattern BR = Pattern.compile("(?i)<br\\s*/?>");
    private static final Pattern DOCX_TOKEN = Pattern.compile(
            "<w:t(?:\\s[^>]*)?>([^<]*)</w:t>|<w:tab\\s*/>|<w:br(?:\\s[^>]*)?/>|<w:cr\\s*/>|</w:p>|</w:tc>|</w:tr>");

    private TextTables() {
    }

    // ---------------------------------------------------------------- CSV

    /** UTF-8 (with or without BOM), UTF-16 with BOM, else Windows-1256 (the Arabic code page old exports use). */
    public static String decode(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            try {
                return new String(bytes, Charset.forName("windows-1256"));
            } catch (RuntimeException unsupported) {
                return new String(bytes, StandardCharsets.ISO_8859_1);
            }
        }
    }

    /** Picks , ; tab or | by counting them (outside quotes) in the first non-empty line. */
    public static char detectDelimiter(String text) {
        char[] candidates = {',', ';', '\t', '|'};
        int[] counts = new int[candidates.length];
        boolean inQuotes = false;
        boolean seenContent = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                seenContent = true;
            } else if (!inQuotes && (c == '\n' || c == '\r')) {
                if (seenContent) {
                    break;
                }
            } else if (!inQuotes) {
                seenContent = true;
                for (int k = 0; k < candidates.length; k++) {
                    if (c == candidates[k]) {
                        counts[k]++;
                    }
                }
            }
        }
        int best = 0;
        for (int k = 1; k < candidates.length; k++) {
            if (counts[k] > counts[best]) {
                best = k;
            }
        }
        return counts[best] == 0 ? ',' : candidates[best];
    }

    /** RFC 4180 parsing: quoted fields, doubled quotes, line breaks inside quotes. */
    public static List<List<String>> parseCsv(String text, char delimiter) {
        List<List<String>> rows = new ArrayList<List<String>>();
        List<String> row = new ArrayList<String>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        int n = text.length();
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < n && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
                continue;
            }
            if (c == '"' && field.length() == 0) {
                inQuotes = true;
            } else if (c == delimiter) {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < n && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<String>();
                if (rows.size() >= MAX_CSV_ROWS) {
                    return rows;
                }
            } else {
                field.append(c);
            }
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    public static SheetData csvSheet(String name, byte[] bytes) {
        String text = decode(bytes);
        return SheetData.fromRows(name, parseCsv(text, detectDelimiter(text)));
    }

    // ---------------------------------------------------------------- Markdown tables

    /** Every Markdown table in the text, as sheets named "جدول 1", "جدول 2"... Tables with fewer than two rows are ignored. */
    public static List<SheetData> markdownTables(String text) {
        List<SheetData> out = new ArrayList<SheetData>();
        if (text == null) {
            return out;
        }
        List<List<String>> current = new ArrayList<List<String>>();
        String[] lines = text.split("\\r?\\n");
        for (int i = 0; i <= lines.length; i++) {
            String line = i < lines.length ? lines[i].trim() : null;
            boolean tableLine = line != null && line.startsWith("|") && line.indexOf('|', 1) > 0;
            if (tableLine) {
                if (!MD_SEPARATOR.matcher(line).matches()) {
                    current.add(splitMarkdownRow(line));
                }
            } else {
                if (current.size() >= 2) {
                    out.add(SheetData.fromRows("جدول " + (out.size() + 1), current));
                }
                current = new ArrayList<List<String>>();
            }
        }
        return out;
    }

    private static List<String> splitMarkdownRow(String line) {
        String body = line;
        if (body.startsWith("|")) {
            body = body.substring(1);
        }
        if (body.endsWith("|") && !body.endsWith("\\|")) {
            body = body.substring(0, body.length() - 1);
        }
        List<String> cells = new ArrayList<String>();
        StringBuilder cell = new StringBuilder();
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '\\' && i + 1 < body.length() && body.charAt(i + 1) == '|') {
                cell.append('|');
                i++;
            } else if (c == '|') {
                cells.add(cleanMarkdownCell(cell.toString()));
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cleanMarkdownCell(cell.toString()));
        return cells;
    }

    private static String cleanMarkdownCell(String raw) {
        return CellValues.clean(BR.matcher(raw).replaceAll(" "));
    }

    // ---------------------------------------------------------------- Word (.docx)

    /** Plain text of a Word document: paragraphs on their own lines, table cells separated by tabs. */
    public static String docxText(byte[] docx) throws IOException {
        String xml = null;
        ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx));
        try {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    xml = new String(readLimited(zip, MAX_DOCX_PART_BYTES), StandardCharsets.UTF_8);
                    break;
                }
            }
        } finally {
            zip.close();
        }
        if (xml == null) {
            throw new IOException("الملف ليس مستند Word (.docx) صالحًا");
        }
        StringBuilder sb = new StringBuilder();
        Matcher m = DOCX_TOKEN.matcher(xml);
        while (m.find()) {
            String token = m.group();
            if (m.group(1) != null) {
                sb.append(XmlText.unescape(m.group(1)));
            } else if (token.startsWith("<w:tab") || token.equals("</w:tc>")) {
                sb.append('\t');
            } else {
                sb.append('\n');
            }
        }
        // A paragraph break right before a cell separator is the end of that cell's paragraph, not a new line.
        return sb.toString().replace("\n\t", "\t").replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n{3,}", "\n\n").trim();
    }

    private static byte[] readLimited(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
        byte[] buffer = new byte[8192];
        int read;
        int total = 0;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > limit) {
                throw new IOException("الملف أكبر من أن يُقرأ");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
