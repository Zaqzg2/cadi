package com.inventorysmartai.app.data.assistant.files;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Fills cells of an existing .xlsx (a form template) and returns a new .xlsx.
 *
 * It edits the sheet XML as text and touches ONLY the rows that receive a value; every other byte of the file —
 * styles, borders, merged cells, column widths, images, other sheets — is copied through unchanged, so the filled form
 * looks exactly like the template. Text is written as inline strings (no shared-strings bookkeeping). Cells that hold
 * a formula are never overwritten, and a cell inside a merged range is redirected to the range's top-left cell.
 */
public final class XlsxTemplateFiller {

    private static final int MAX_ENTRIES = 3000;
    private static final long MAX_TOTAL_BYTES = 120L * 1024 * 1024;
    private static final Pattern RELATIONSHIP_TAG = Pattern.compile("<Relationship\\b[^>]*>");
    private static final Pattern SHEET_TAG = Pattern.compile("<(?:\\w+:)?sheet\\b[^>]*>");
    private static final Pattern MERGE_TAG = Pattern.compile("<mergeCell\\b[^>]*>");
    private static final Pattern DIMENSION_TAG = Pattern.compile("<dimension\\s+ref\\s*=\\s*\"([^\"]*)\"\\s*/>");
    private static final Pattern CALC_PR = Pattern.compile("<calcPr\\b([^>]*?)(/?)>");
    private static final Pattern SHEET_DATA_OPEN = Pattern.compile("<sheetData\\b[^>]*?(/?)>");
    private static final Pattern FORMULA_TAG = Pattern.compile("<f[\\s>/]");

    private XlsxTemplateFiller() {
    }

    /** One value to write. {@code sheet} null/blank means the first sheet; a null {@code value} clears the cell. */
    public static final class Edit {
        public final String sheet;
        public final String cell;
        public final Object value;

        public Edit(String sheet, String cell, Object value) {
            this.sheet = sheet;
            this.cell = cell;
            this.value = value;
        }
    }

    public static final class Report {
        public byte[] bytes;
        public int applied;
        /** Cells written somewhere other than asked (inside a merged range). */
        public final List<String> redirected = new ArrayList<String>();
        /** Cells NOT written, each with the reason. */
        public final List<String> skipped = new ArrayList<String>();
    }

    // ------------------------------------------------------------------ public entry

    public static Report fill(byte[] template, List<Edit> edits) throws IOException {
        Report report = new Report();
        Map<String, byte[]> parts = readZip(template);
        Map<String, String> sheetParts = sheetParts(parts);
        if (sheetParts.isEmpty()) {
            throw new IOException("الملف ليس مصنّف Excel (.xlsx) صالحًا أو لا يحتوي أوراقًا");
        }

        Map<String, List<Edit>> bySheet = new LinkedHashMap<String, List<Edit>>();
        for (Edit edit : edits) {
            String sheetName = resolveSheetName(sheetParts, edit.sheet);
            if (sheetName == null) {
                report.skipped.add(edit.cell + ": الورقة \"" + edit.sheet + "\" غير موجودة. الأوراق: " + joinKeys(sheetParts));
                continue;
            }
            List<Edit> list = bySheet.get(sheetName);
            if (list == null) {
                list = new ArrayList<Edit>();
                bySheet.put(sheetName, list);
            }
            list.add(edit);
        }

        for (Map.Entry<String, List<Edit>> entry : bySheet.entrySet()) {
            String partName = sheetParts.get(entry.getKey());
            byte[] original = parts.get(partName);
            if (original == null) {
                for (Edit e : entry.getValue()) {
                    report.skipped.add(e.cell + ": تعذّر العثور على بيانات الورقة داخل الملف");
                }
                continue;
            }
            String xml = new String(original, StandardCharsets.UTF_8);
            boolean bom = xml.startsWith("\uFEFF");
            if (bom) {
                xml = xml.substring(1);
            }
            SheetXml sheet = SheetXml.parse(xml);
            for (Edit e : entry.getValue()) {
                sheet.apply(entry.getKey(), e, report);
            }
            String updated = sheet.toXml();
            parts.put(partName, (bom ? "\uFEFF" + updated : updated).getBytes(StandardCharsets.UTF_8));
        }

        if (report.applied > 0) {
            requestRecalculation(parts);
        }
        report.bytes = writeZip(parts);
        return report;
    }

    // ------------------------------------------------------------------ the sheet XML

    private static final class CellNode {
        int col;
        String attrs;
        String inner;

        String xml() {
            return "<c" + attrs + (inner.isEmpty() ? "/>" : ">" + inner + "</c>");
        }
    }

    private static final class RowNode {
        final int number;
        String attrs;
        String rawXml;
        String innerXml;
        List<CellNode> cells;
        boolean dirty;
        boolean unparseable;

        RowNode(int number, String attrs, String rawXml, String innerXml) {
            this.number = number;
            this.attrs = attrs;
            this.rawXml = rawXml;
            this.innerXml = innerXml;
        }
    }

    private static final class SheetXml {
        String prefix;
        String suffix;
        final TreeMap<Integer, RowNode> rows = new TreeMap<Integer, RowNode>();
        final List<A1.Range> merges = new ArrayList<A1.Range>();
        int minRow = Integer.MAX_VALUE;
        int minCol = Integer.MAX_VALUE;
        int maxRow = 0;
        int maxCol = -1;

        static SheetXml parse(String xml) throws IOException {
            SheetXml sheet = new SheetXml();
            Matcher open = SHEET_DATA_OPEN.matcher(xml);
            if (!open.find()) {
                throw new IOException("بيانات الورقة غير مفهومة (لا يوجد sheetData)");
            }
            boolean selfClosing = "/".equals(open.group(1));
            int dataStart = open.end();
            int dataEnd;
            int suffixStart;
            if (selfClosing) {
                dataEnd = dataStart;
                suffixStart = dataStart;
            } else {
                dataEnd = xml.indexOf("</sheetData>", dataStart);
                if (dataEnd < 0) {
                    throw new IOException("بيانات الورقة غير مكتملة");
                }
                suffixStart = dataEnd + "</sheetData>".length();
            }
            sheet.prefix = xml.substring(0, open.start());
            sheet.suffix = xml.substring(suffixStart);
            sheet.parseRows(xml.substring(dataStart, dataEnd));

            Matcher merge = MERGE_TAG.matcher(sheet.suffix);
            while (merge.find()) {
                String ref = attribute(merge.group(), "ref");
                A1.Range range = A1.parseRange(ref);
                if (range != null) {
                    sheet.merges.add(range);
                }
            }
            return sheet;
        }

        private void parseRows(String data) throws IOException {
            int i = 0;
            int lastRow = 0;
            while (i < data.length()) {
                int rowStart = data.indexOf("<row", i);
                if (rowStart < 0) {
                    break;
                }
                char after = rowStart + 4 < data.length() ? data.charAt(rowStart + 4) : '>';
                if (!(Character.isWhitespace(after) || after == '>' || after == '/')) {
                    i = rowStart + 4;
                    continue;
                }
                int tagEnd = findTagEnd(data, rowStart);
                if (tagEnd < 0) {
                    throw new IOException("بيانات الورقة تالفة");
                }
                String startTag = data.substring(rowStart, tagEnd + 1);
                boolean selfClosing = startTag.endsWith("/>");
                String inner = "";
                int end;
                if (selfClosing) {
                    end = tagEnd + 1;
                } else {
                    int close = data.indexOf("</row>", tagEnd + 1);
                    if (close < 0) {
                        throw new IOException("بيانات الورقة تالفة (صف غير مغلق)");
                    }
                    inner = data.substring(tagEnd + 1, close);
                    end = close + "</row>".length();
                }
                String attrs = startTag.substring("<row".length(), startTag.length() - (selfClosing ? 2 : 1));
                String rowRef = attribute(startTag, "r");
                int number = lastRow + 1;
                if (rowRef != null) {
                    try {
                        number = Integer.parseInt(rowRef.trim());
                    } catch (NumberFormatException e) {
                        number = lastRow + 1;
                    }
                }
                lastRow = number;
                rows.put(number, new RowNode(number, attrs, data.substring(rowStart, end), inner));
                i = end;
            }
        }

        void apply(String sheetName, Edit edit, Report report) {
            A1.Ref ref = A1.parse(edit.cell);
            if (ref == null) {
                report.skipped.add(edit.cell + ": عنوان خلية غير صالح (مثال صحيح: C7)");
                return;
            }
            int row = ref.row;
            int col = ref.col;
            A1.Range merge = null;
            for (A1.Range m : merges) {
                if (m.contains(row, col)) {
                    merge = m;
                    break;
                }
            }
            if (merge != null && (row != merge.firstRow || col != merge.firstCol)) {
                row = merge.firstRow;
                col = merge.firstCol;
                report.redirected.add(edit.cell + " ← داخل نطاق مدمج " + merge + "، كُتبت القيمة في " + A1.format(row, col));
            }
            String address = A1.format(row, col);

            RowNode node = rows.get(row);
            if (node == null) {
                node = new RowNode(row, " r=\"" + row + "\"", "", "");
                node.cells = new ArrayList<CellNode>();
                node.dirty = true;
                rows.put(row, node);
            }
            if (node.cells == null) {
                parseCells(node);
            }
            if (node.unparseable) {
                report.skipped.add(address + ": بنية الصف غير مدعومة للتعديل");
                return;
            }

            CellNode cell = null;
            int insertAt = node.cells.size();
            for (int i = 0; i < node.cells.size(); i++) {
                CellNode c = node.cells.get(i);
                if (c.col == col) {
                    cell = c;
                    break;
                }
                if (c.col > col) {
                    insertAt = i;
                    break;
                }
            }
            if (cell != null && FORMULA_TAG.matcher(cell.inner).find()) {
                report.skipped.add(address + ": الخلية تحتوي صيغة حسابية ولن تُستبدل");
                return;
            }

            String content = valueXml(edit.value);
            if (content == null) {
                report.skipped.add(address + ": قيمة غير مدعومة");
                return;
            }
            if (cell == null) {
                cell = new CellNode();
                cell.col = col;
                cell.attrs = " r=\"" + address + "\"";
                node.cells.add(insertAt, cell);
            }
            cell.attrs = stripAttribute(cell.attrs, "t");
            if (!hasAttribute(cell.attrs, "r")) {
                cell.attrs = " r=\"" + address + "\"" + cell.attrs;
            }
            if (content.startsWith("T:")) {
                cell.attrs = cell.attrs + " t=\"" + content.substring(2, content.indexOf('|')) + "\"";
                cell.inner = content.substring(content.indexOf('|') + 1);
            } else {
                cell.inner = content;
            }
            node.dirty = true;
            report.applied++;

            minRow = Math.min(minRow, row);
            minCol = Math.min(minCol, col);
            maxRow = Math.max(maxRow, row);
            maxCol = Math.max(maxCol, col);
        }

        private void parseCells(RowNode node) {
            node.cells = new ArrayList<CellNode>();
            String inner = node.innerXml;
            int i = 0;
            int lastCol = -1;
            while (i < inner.length()) {
                int start = inner.indexOf('<', i);
                if (start < 0) {
                    break;
                }
                boolean isCell = inner.startsWith("<c", start) && start + 2 < inner.length()
                        && (Character.isWhitespace(inner.charAt(start + 2)) || inner.charAt(start + 2) == '>' || inner.charAt(start + 2) == '/');
                if (!isCell) {
                    node.unparseable = true; // something other than cells inside the row: leave it alone
                    return;
                }
                int tagEnd = findTagEnd(inner, start);
                if (tagEnd < 0) {
                    node.unparseable = true;
                    return;
                }
                String startTag = inner.substring(start, tagEnd + 1);
                boolean selfClosing = startTag.endsWith("/>");
                CellNode cell = new CellNode();
                cell.attrs = startTag.substring("<c".length(), startTag.length() - (selfClosing ? 2 : 1));
                int end;
                if (selfClosing) {
                    cell.inner = "";
                    end = tagEnd + 1;
                } else {
                    int close = inner.indexOf("</c>", tagEnd + 1);
                    if (close < 0) {
                        node.unparseable = true;
                        return;
                    }
                    cell.inner = inner.substring(tagEnd + 1, close);
                    end = close + "</c>".length();
                }
                String ref = attribute(startTag, "r");
                A1.Ref parsed = ref == null ? null : A1.parse(ref);
                cell.col = parsed != null ? parsed.col : lastCol + 1;
                lastCol = cell.col;
                node.cells.add(cell);
                i = end;
            }
        }

        String toXml() {
            StringBuilder sb = new StringBuilder(prefix.length() + suffix.length() + 4096);
            sb.append(updateDimension(prefix)).append("<sheetData>");
            for (RowNode row : rows.values()) {
                if (!row.dirty) {
                    sb.append(row.rawXml);
                    continue;
                }
                sb.append("<row").append(stripAttribute(row.attrs, "spans")).append('>');
                for (CellNode cell : row.cells) {
                    sb.append(cell.xml());
                }
                sb.append("</row>");
            }
            sb.append("</sheetData>").append(suffix);
            return sb.toString();
        }

        private String updateDimension(String head) {
            Matcher m = DIMENSION_TAG.matcher(head);
            if (!m.find() || maxCol < 0) {
                return head;
            }
            A1.Range current = A1.parseRange(m.group(1));
            int r1 = minRow;
            int c1 = minCol;
            int r2 = maxRow;
            int c2 = maxCol;
            if (current != null) {
                r1 = Math.min(r1, current.firstRow);
                c1 = Math.min(c1, current.firstCol);
                r2 = Math.max(r2, current.lastRow);
                c2 = Math.max(c2, current.lastCol);
            }
            String ref = new A1.Range(r1, c1, r2, c2).toString();
            return head.substring(0, m.start()) + "<dimension ref=\"" + ref + "\"/>" + head.substring(m.end());
        }
    }

    // ------------------------------------------------------------------ cell content

    /**
     * The XML for a cell's content. Returns "T:inlineStr|..." / "T:b|..." when the cell also needs a t attribute,
     * plain "<v>..</v>" for numbers, "" to clear, and null for an unsupported value type.
     */
    private static String valueXml(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Boolean) {
            return "T:b|<v>" + (((Boolean) value) ? "1" : "0") + "</v>";
        }
        if (value instanceof Number) {
            double d = ((Number) value).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                return null;
            }
            return "<v>" + plainNumber(d) + "</v>";
        }
        String text = String.valueOf(value);
        if (text.isEmpty()) {
            return "";
        }
        return "T:inlineStr|<is><t xml:space=\"preserve\">" + XmlText.escape(text) + "</t></is>";
    }

    static String plainNumber(double d) {
        if (Math.abs(d) < 1e15 && d == Math.rint(d)) {
            return Long.toString((long) d);
        }
        return new BigDecimal(Double.toString(d)).stripTrailingZeros().toPlainString();
    }

    // ------------------------------------------------------------------ workbook structure

    private static Map<String, String> sheetParts(Map<String, byte[]> parts) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        String workbookPart = null;
        byte[] rootRels = parts.get("_rels/.rels");
        if (rootRels != null) {
            Matcher m = RELATIONSHIP_TAG.matcher(new String(rootRels, StandardCharsets.UTF_8));
            while (m.find()) {
                String type = attribute(m.group(), "Type");
                String target = attribute(m.group(), "Target");
                if (type != null && type.endsWith("/officeDocument") && target != null) {
                    workbookPart = resolvePart("", target);
                    break;
                }
            }
        }
        if (workbookPart == null || !parts.containsKey(workbookPart)) {
            workbookPart = "xl/workbook.xml";
        }
        byte[] workbook = parts.get(workbookPart);
        if (workbook == null) {
            return result;
        }
        String dir = workbookPart.contains("/") ? workbookPart.substring(0, workbookPart.lastIndexOf('/') + 1) : "";
        String fileName = workbookPart.substring(workbookPart.lastIndexOf('/') + 1);
        Map<String, String> targets = new LinkedHashMap<String, String>();
        byte[] rels = parts.get(dir + "_rels/" + fileName + ".rels");
        if (rels != null) {
            Matcher m = RELATIONSHIP_TAG.matcher(new String(rels, StandardCharsets.UTF_8));
            while (m.find()) {
                String id = attribute(m.group(), "Id");
                String target = attribute(m.group(), "Target");
                if (id != null && target != null) {
                    targets.put(id, resolvePart(dir, target));
                }
            }
        }
        Matcher sheets = SHEET_TAG.matcher(new String(workbook, StandardCharsets.UTF_8));
        int index = 0;
        while (sheets.find()) {
            index++;
            String tag = sheets.group();
            String name = attribute(tag, "name");
            if (name == null) {
                continue;
            }
            String relId = attribute(tag, "r:id");
            if (relId == null) {
                relId = attribute(tag, "id");
            }
            String part = relId == null ? null : targets.get(relId);
            if (part == null || !parts.containsKey(part)) {
                part = dir + "worksheets/sheet" + index + ".xml";
            }
            result.put(XmlText.unescape(name), part);
        }
        return result;
    }

    private static String resolveSheetName(Map<String, String> sheetParts, String wanted) {
        if (wanted == null || CellValues.isBlank(wanted)) {
            return sheetParts.keySet().iterator().next();
        }
        if (sheetParts.containsKey(wanted)) {
            return wanted;
        }
        String normalized = CellValues.normalizeForSearch(wanted);
        for (String name : sheetParts.keySet()) {
            if (CellValues.normalizeForSearch(name).equals(normalized)) {
                return name;
            }
        }
        return null;
    }

    private static String joinKeys(Map<String, String> map) {
        StringBuilder sb = new StringBuilder();
        for (String key : map.keySet()) {
            if (sb.length() > 0) {
                sb.append("، ");
            }
            sb.append(key);
        }
        return sb.toString();
    }

    private static String resolvePart(String baseDir, String target) {
        String t = target.replace('\\', '/');
        String full = t.startsWith("/") ? t.substring(1) : baseDir + t;
        List<String> kept = new ArrayList<String>();
        for (String segment : full.split("/")) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                if (!kept.isEmpty()) {
                    kept.remove(kept.size() - 1);
                }
                continue;
            }
            kept.add(segment);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < kept.size(); i++) {
            if (i > 0) {
                sb.append('/');
            }
            sb.append(kept.get(i));
        }
        return sb.toString();
    }

    /** Asks Excel/LibreOffice to recompute formulas on open, so totals that depend on filled cells are fresh. */
    private static void requestRecalculation(Map<String, byte[]> parts) {
        byte[] workbook = parts.get("xl/workbook.xml");
        if (workbook == null) {
            return;
        }
        String xml = new String(workbook, StandardCharsets.UTF_8);
        Matcher m = CALC_PR.matcher(xml);
        if (!m.find() || m.group(1).contains("fullCalcOnLoad")) {
            return;
        }
        String replacement = "<calcPr" + m.group(1) + " fullCalcOnLoad=\"1\"" + m.group(2) + ">";
        parts.put("xl/workbook.xml", (xml.substring(0, m.start()) + replacement + xml.substring(m.end())).getBytes(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------------ zip

    private static Map<String, byte[]> readZip(byte[] data) throws IOException {
        Map<String, byte[]> parts = new LinkedHashMap<String, byte[]>();
        long total = 0;
        ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data));
        try {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                if (parts.size() >= MAX_ENTRIES) {
                    throw new IOException("الملف يحتوي أجزاء أكثر من المعتاد");
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
                byte[] buffer = new byte[16384];
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_TOTAL_BYTES) {
                        throw new IOException("الملف أكبر من أن يُعالج");
                    }
                    out.write(buffer, 0, read);
                }
                parts.put(entry.getName(), out.toByteArray());
            }
        } finally {
            zip.close();
        }
        if (parts.isEmpty()) {
            throw new IOException("الملف ليس مصنّف Excel (.xlsx) صالحًا");
        }
        return parts;
    }

    static byte[] writeZip(Map<String, byte[]> parts) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(64 * 1024);
        ZipOutputStream zos = new ZipOutputStream(bos);
        try {
            for (Map.Entry<String, byte[]> e : parts.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue());
                zos.closeEntry();
            }
        } finally {
            zos.close();
        }
        return bos.toByteArray();
    }

    // ------------------------------------------------------------------ small XML-as-text helpers

    /** Index of the '>' that closes the tag starting at {@code from}, ignoring '>' inside quoted attribute values. */
    static int findTagEnd(String s, int from) {
        char quote = 0;
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '>') {
                return i;
            }
        }
        return -1;
    }

    private static Pattern attributePattern(String name) {
        return Pattern.compile("(?<![\\w:.-])" + Pattern.quote(name) + "\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')");
    }

    /** The (unescaped) value of an attribute inside a start tag or attribute text, or null. */
    static String attribute(String tag, String name) {
        Matcher m = attributePattern(name).matcher(tag);
        if (!m.find()) {
            return null;
        }
        return XmlText.unescape(m.group(1) != null ? m.group(1) : m.group(2));
    }

    private static boolean hasAttribute(String attrs, String name) {
        return attributePattern(name).matcher(attrs).find();
    }

    private static String stripAttribute(String attrs, String name) {
        return Pattern.compile("\\s+(?<![\\w:.-])" + Pattern.quote(name) + "\\s*=\\s*(?:\"[^\"]*\"|'[^']*')").matcher(attrs).replaceAll("");
    }
}
