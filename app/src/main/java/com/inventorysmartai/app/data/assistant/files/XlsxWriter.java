package com.inventorysmartai.app.data.assistant.files;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Writes a brand-new .xlsx from tables of values: a styled header row, frozen and filterable, optional right-to-left
 * sheets for Arabic. Text goes in as plain text (never as a formula), numbers as numbers. Plain JDK only.
 */
public final class XlsxWriter {

    public static final int MAX_ROWS = 20000;
    public static final int MAX_COLUMNS = 100;
    private static final int MAX_CELL_CHARS = 32000;

    private static final String NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String NS_R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String XML_HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n";

    private XlsxWriter() {
    }

    public static final class SheetSpec {
        public final String name;
        public final List<String> headers;
        public final List<List<Object>> rows;

        public SheetSpec(String name, List<String> headers, List<List<Object>> rows) {
            this.name = name;
            this.headers = headers == null ? new ArrayList<String>() : headers;
            this.rows = rows == null ? new ArrayList<List<Object>>() : rows;
        }
    }

    public static byte[] write(List<SheetSpec> sheets, boolean rightToLeft) throws IOException {
        if (sheets == null || sheets.isEmpty()) {
            throw new IOException("لا توجد أوراق لكتابتها");
        }
        Map<String, byte[]> parts = new LinkedHashMap<String, byte[]>();
        List<String> names = uniqueNames(sheets);

        StringBuilder types = new StringBuilder(XML_HEAD);
        types.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">");
        types.append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>");
        types.append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>");
        types.append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>");
        types.append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
        for (int i = 0; i < sheets.size(); i++) {
            types.append("<Override PartName=\"/xl/worksheets/sheet").append(i + 1)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        types.append("</Types>");
        parts.put("[Content_Types].xml", bytes(types.toString()));

        parts.put("_rels/.rels", bytes(XML_HEAD
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "</Relationships>"));

        StringBuilder workbook = new StringBuilder(XML_HEAD);
        workbook.append("<workbook xmlns=\"").append(NS).append("\" xmlns:r=\"").append(NS_R).append("\">");
        workbook.append("<bookViews><workbookView/></bookViews><sheets>");
        StringBuilder rels = new StringBuilder(XML_HEAD);
        rels.append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int i = 0; i < sheets.size(); i++) {
            workbook.append("<sheet name=\"").append(XmlText.escape(names.get(i))).append("\" sheetId=\"").append(i + 1)
                    .append("\" r:id=\"rId").append(i + 1).append("\"/>");
            rels.append("<Relationship Id=\"rId").append(i + 1)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
                    .append(i + 1).append(".xml\"/>");
        }
        workbook.append("</sheets></workbook>");
        rels.append("<Relationship Id=\"rId").append(sheets.size() + 1)
                .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
        rels.append("</Relationships>");
        parts.put("xl/workbook.xml", bytes(workbook.toString()));
        parts.put("xl/_rels/workbook.xml.rels", bytes(rels.toString()));
        parts.put("xl/styles.xml", bytes(stylesXml()));

        for (int i = 0; i < sheets.size(); i++) {
            parts.put("xl/worksheets/sheet" + (i + 1) + ".xml", bytes(sheetXml(sheets.get(i), rightToLeft, i == 0)));
        }
        return XlsxTemplateFiller.writeZip(parts);
    }

    // ------------------------------------------------------------------ parts

    private static String stylesXml() {
        return XML_HEAD
                + "<styleSheet xmlns=\"" + NS + "\">"
                + "<fonts count=\"2\">"
                + "<font><sz val=\"11\"/><name val=\"Calibri\"/><family val=\"2\"/></font>"
                + "<font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/><family val=\"2\"/></font>"
                + "</fonts>"
                + "<fills count=\"3\">"
                + "<fill><patternFill patternType=\"none\"/></fill>"
                + "<fill><patternFill patternType=\"gray125\"/></fill>"
                + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF1F4E79\"/><bgColor indexed=\"64\"/></patternFill></fill>"
                + "</fills>"
                + "<borders count=\"2\">"
                + "<border><left/><right/><top/><bottom/><diagonal/></border>"
                + "<border><left style=\"thin\"><color rgb=\"FFBFBFBF\"/></left><right style=\"thin\"><color rgb=\"FFBFBFBF\"/></right>"
                + "<top style=\"thin\"><color rgb=\"FFBFBFBF\"/></top><bottom style=\"thin\"><color rgb=\"FFBFBFBF\"/></bottom><diagonal/></border>"
                + "</borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + "<cellXfs count=\"5\">"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\">"
                + "<alignment horizontal=\"center\" vertical=\"center\" wrapText=\"1\"/></xf>"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyBorder=\"1\" applyAlignment=\"1\">"
                + "<alignment vertical=\"top\" wrapText=\"1\"/></xf>"
                + "<xf numFmtId=\"3\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyNumberFormat=\"1\" applyBorder=\"1\" applyAlignment=\"1\">"
                + "<alignment vertical=\"top\"/></xf>"
                + "<xf numFmtId=\"4\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyNumberFormat=\"1\" applyBorder=\"1\" applyAlignment=\"1\">"
                + "<alignment vertical=\"top\"/></xf>"
                + "</cellXfs>"
                + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
                + "</styleSheet>";
    }

    private static String sheetXml(SheetSpec sheet, boolean rtl, boolean selected) {
        boolean hasHeader = !sheet.headers.isEmpty();
        int columns = sheet.headers.size();
        for (List<Object> row : sheet.rows) {
            columns = Math.max(columns, row.size());
        }
        columns = Math.min(Math.max(columns, 1), MAX_COLUMNS);
        int rowCount = Math.min(sheet.rows.size(), MAX_ROWS);
        int lastRow = rowCount + (hasHeader ? 1 : 0);
        if (lastRow == 0) {
            lastRow = 1;
        }

        int[] widths = new int[columns];
        for (int c = 0; c < columns; c++) {
            widths[c] = c < sheet.headers.size() ? textLength(sheet.headers.get(c)) : 0;
        }
        for (int r = 0; r < Math.min(rowCount, 300); r++) {
            List<Object> row = sheet.rows.get(r);
            for (int c = 0; c < Math.min(row.size(), columns); c++) {
                widths[c] = Math.max(widths[c], textLength(row.get(c)));
            }
        }

        StringBuilder sb = new StringBuilder(8192);
        sb.append(XML_HEAD).append("<worksheet xmlns=\"").append(NS).append("\">");
        sb.append("<dimension ref=\"A1:").append(A1.format(lastRow, columns - 1)).append("\"/>");
        sb.append("<sheetViews><sheetView workbookViewId=\"0\"");
        if (rtl) {
            sb.append(" rightToLeft=\"1\"");
        }
        if (selected) {
            sb.append(" tabSelected=\"1\"");
        }
        sb.append('>');
        if (hasHeader) {
            sb.append("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/><selection pane=\"bottomLeft\"/>");
        }
        sb.append("</sheetView></sheetViews>");
        sb.append("<sheetFormatPr defaultRowHeight=\"15\"/>");
        sb.append("<cols>");
        for (int c = 0; c < columns; c++) {
            int width = Math.max(10, Math.min(55, widths[c] + 4));
            sb.append("<col min=\"").append(c + 1).append("\" max=\"").append(c + 1).append("\" width=\"").append(width).append("\" customWidth=\"1\"/>");
        }
        sb.append("</cols><sheetData>");

        int rowNumber = 0;
        if (hasHeader) {
            rowNumber++;
            sb.append("<row r=\"").append(rowNumber).append("\">");
            for (int c = 0; c < columns; c++) {
                String title = c < sheet.headers.size() ? sheet.headers.get(c) : "";
                appendText(sb, rowNumber, c, title, 1);
            }
            sb.append("</row>");
        }
        for (int r = 0; r < rowCount; r++) {
            rowNumber++;
            List<Object> row = sheet.rows.get(r);
            sb.append("<row r=\"").append(rowNumber).append("\">");
            for (int c = 0; c < Math.min(row.size(), columns); c++) {
                appendValue(sb, rowNumber, c, row.get(c));
            }
            sb.append("</row>");
        }
        sb.append("</sheetData>");
        if (hasHeader && rowCount > 0) {
            sb.append("<autoFilter ref=\"A1:").append(A1.format(lastRow, columns - 1)).append("\"/>");
        }
        sb.append("<pageMargins left=\"0.7\" right=\"0.7\" top=\"0.75\" bottom=\"0.75\" header=\"0.3\" footer=\"0.3\"/>");
        sb.append("</worksheet>");
        return sb.toString();
    }

    private static void appendValue(StringBuilder sb, int row, int col, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Number) {
            double d = ((Number) value).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                return;
            }
            boolean whole = Math.abs(d) < 1e15 && d == Math.rint(d);
            sb.append("<c r=\"").append(A1.format(row, col)).append("\" s=\"").append(whole ? 3 : 4).append("\"><v>")
                    .append(XlsxTemplateFiller.plainNumber(d)).append("</v></c>");
            return;
        }
        if (value instanceof Boolean) {
            sb.append("<c r=\"").append(A1.format(row, col)).append("\" s=\"2\" t=\"b\"><v>").append(((Boolean) value) ? 1 : 0).append("</v></c>");
            return;
        }
        String text = String.valueOf(value);
        if (text.isEmpty()) {
            return;
        }
        appendText(sb, row, col, text, 2);
    }

    private static void appendText(StringBuilder sb, int row, int col, String text, int style) {
        String t = text.length() > MAX_CELL_CHARS ? text.substring(0, MAX_CELL_CHARS) : text;
        sb.append("<c r=\"").append(A1.format(row, col)).append("\" s=\"").append(style).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                .append(XmlText.escape(t)).append("</t></is></c>");
    }

    private static int textLength(Object value) {
        if (value == null) {
            return 0;
        }
        String s = value instanceof Number ? CellValues.formatNumber(((Number) value).doubleValue()) : String.valueOf(value);
        int longestLine = 0;
        for (String line : s.split("\n")) {
            longestLine = Math.max(longestLine, line.length());
        }
        return longestLine;
    }

    /** Valid, unique sheet names: at most 31 characters, none of [ ] : * ? / \ , never empty. */
    static List<String> uniqueNames(List<SheetSpec> sheets) {
        List<String> out = new ArrayList<String>();
        Set<String> used = new HashSet<String>();
        for (int i = 0; i < sheets.size(); i++) {
            String name = sheets.get(i).name == null ? "" : sheets.get(i).name;
            name = CellValues.clean(name.replaceAll("[\\[\\]:*?/\\\\]", "-"));
            if (name.startsWith("'")) {
                name = name.substring(1);
            }
            if (name.endsWith("'")) {
                name = name.substring(0, name.length() - 1);
            }
            if (name.isEmpty()) {
                name = "Sheet" + (i + 1);
            }
            if (name.length() > 31) {
                name = name.substring(0, 31);
            }
            String candidate = name;
            int n = 2;
            while (!used.add(candidate.toLowerCase(Locale.ROOT))) {
                String suffix = " (" + n++ + ")";
                candidate = name.substring(0, Math.min(name.length(), 31 - suffix.length())) + suffix;
            }
            out.add(candidate);
        }
        return out;
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
