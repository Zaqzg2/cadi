package com.inventorysmartai.app.data.importing.parser;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * A small, dependency-free .xlsx reader: {@code java.util.zip} for the container plus a SAX
 * parser for the XML parts. Nothing here touches StAX ({@code javax.xml.stream}),
 * {@code ServiceLoader}, or the thread's context class loader.
 *
 * <p><b>Why this exists.</b> {@code org.dhatim:fastexcel-reader} parses XML through a StAX
 * factory ({@code DefaultXMLInputFactory}) that is created in a static initializer. On Android
 * that initializer can fail (StAX is not part of the platform, and the provider lookup depends
 * on class loaders that thread pools such as {@code Dispatchers.IO} do not set up). The JVM/ART
 * cache a failed static initializer permanently, so after the first failure every later use of
 * the class throws {@code NoClassDefFoundError: org.dhatim.fastexcel.reader.DefaultXMLInputFactory}
 * — which is exactly the message the import screen showed. This reader is what
 * {@link ExcelImportParser} falls back to when that happens.
 *
 * <p><b>Why Java.</b> Written in Java (not Kotlin) so it could be compiled and run against real
 * workbooks produced by Excel-compatible writers outside the Android toolchain.
 *
 * <p>The XML parsing itself is delegated to a {@link SaxRunner}: on Android that is
 * {@code android.util.Xml.parse(...)} (the platform's Expat parser, instantiated directly — no
 * factory lookup); in JVM unit tests it is plain JAXP.
 *
 * <p>Behavior intentionally mirrors what the fastexcel path returns: every {@code <row>} present
 * in the sheet XML becomes one row (rows that are absent are not invented), the row's width is
 * its highest used column + 1, gaps are empty strings, and cell text is the cell's stored value
 * (shared/inline/formula strings as text, numbers as plain decimals, booleans as TRUE/FALSE).
 */
public final class XlsxSaxReader {

    /** Runs one SAX parse. Must feed {@code handler} the document read from {@code input}. */
    public interface SaxRunner {
        void parse(InputStream input, DefaultHandler handler) throws Exception;
    }

    /** One sheet's rows, exactly as stored (cell text is NOT trimmed here). */
    public static final class Table {
        public final String sheetName;
        public final List<List<String>> rows;
        /** The real 1-based sheet row number of each entry in {@link #rows} (parallel list; absent rows are skipped). */
        public final List<Integer> rowNumbers;
        /** Merged ranges the sheet declares, as written ("A1:C3"). */
        public final List<String> mergedRanges;

        Table(String sheetName, List<List<String>> rows) {
            this(sheetName, rows, null, null);
        }

        Table(String sheetName, List<List<String>> rows, List<Integer> rowNumbers, List<String> mergedRanges) {
            this.sheetName = sheetName;
            this.rows = rows;
            if (rowNumbers == null) {
                rowNumbers = new ArrayList<>();
                for (int i = 0; i < rows.size(); i++) {
                    rowNumbers.add(i + 1);
                }
            }
            this.rowNumbers = rowNumbers;
            this.mergedRanges = mergedRanges == null ? new ArrayList<String>() : mergedRanges;
        }
    }

    /** Excel's hard column limit is 16384 (XFD); anything past it is corrupt input, not data. */
    private static final int MAX_COLUMNS = 16384;

    private final SaxRunner sax;

    public XlsxSaxReader(SaxRunner sax) {
        if (sax == null) {
            throw new IllegalArgumentException("SaxRunner is required");
        }
        this.sax = sax;
    }

    // ------------------------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------------------------

    /** Sheet names in workbook order (hidden sheets included, like fastexcel). Never empty. */
    public List<String> listSheets(byte[] xlsx) throws Exception {
        WorkbookLayout layout = openLayout(xlsx);
        List<String> names = new ArrayList<>();
        for (SheetRef sheet : layout.sheets) {
            names.add(sheet.name);
        }
        return names;
    }

    /**
     * Reads one sheet. A null {@code sheetName} means "the first sheet in workbook order".
     *
     * @throws IllegalStateException if the sheet is not in the workbook or the file is not a
     *                               valid xlsx (message is user-facing Arabic).
     */
    public Table readSheet(byte[] xlsx, String sheetName) throws Exception {
        WorkbookLayout layout = openLayout(xlsx);

        SheetRef target = null;
        if (sheetName == null) {
            target = layout.sheets.get(0);
        } else {
            for (SheetRef sheet : layout.sheets) {
                if (sheetName.equals(sheet.name)) {
                    target = sheet;
                    break;
                }
            }
        }
        if (target == null) {
            throw new IllegalStateException("لم يتم العثور على الورقة \"" + sheetName + "\" في هذا الملف");
        }

        final List<String> sharedStrings = readSharedStrings(xlsx, layout.sharedStringsKey);
        final SheetHandler handler = new SheetHandler(sharedStrings);
        final String sheetKey = target.partKey;
        final boolean[] found = new boolean[1];

        forEachEntry(xlsx, new EntryVisitor() {
            @Override
            public boolean visit(String key, InputStream in) throws Exception {
                if (!key.equals(sheetKey)) {
                    return true;
                }
                found[0] = true;
                sax.parse(new NonClosingInputStream(in), handler);
                return false;
            }
        });

        if (!found[0]) {
            throw new IllegalStateException("تعذّر العثور على بيانات الورقة \"" + target.name + "\" داخل الملف");
        }
        return new Table(target.name, handler.rows, handler.rowNumbers, handler.mergedRanges);
    }

    // ------------------------------------------------------------------------------------
    // Package structure (workbook.xml + relationships)
    // ------------------------------------------------------------------------------------

    private static final class SheetRef {
        final String name;
        final String relId;
        String partKey;

        SheetRef(String name, String relId) {
            this.name = name;
            this.relId = relId;
        }
    }

    private static final class Relationship {
        String id;
        String type;
        String target;
    }

    private static final class WorkbookLayout {
        final List<SheetRef> sheets = new ArrayList<>();
        String sharedStringsKey;
    }

    private WorkbookLayout openLayout(byte[] xlsx) throws Exception {
        // Pass 1: buffer only the tiny structural parts (every .rels + any workbook.xml). They
        // can appear anywhere in the zip, so this must be a full scan, not "stop at the first".
        final Map<String, byte[]> small = new HashMap<>();
        forEachEntry(xlsx, new EntryVisitor() {
            @Override
            public boolean visit(String key, InputStream in) throws Exception {
                if (key.endsWith(".rels") || key.endsWith("workbook.xml")) {
                    small.put(key, readFully(in));
                }
                return true;
            }
        });
        if (small.isEmpty()) {
            throw new IllegalStateException("الملف ليس مصنّف Excel (.xlsx) صالحاً");
        }

        // Locate the workbook part: the root relationships file names it; fall back to the
        // conventional location for generators that omit or mangle it.
        String workbookKey = null;
        byte[] rootRels = small.get("_rels/.rels");
        if (rootRels != null) {
            for (Relationship rel : parseRelationships(rootRels)) {
                if (rel.type != null && rel.type.endsWith("/officeDocument") && rel.target != null) {
                    workbookKey = resolve("", rel.target);
                    break;
                }
            }
        }
        if (workbookKey == null || !small.containsKey(workbookKey)) {
            workbookKey = small.containsKey("xl/workbook.xml") ? "xl/workbook.xml" : null;
            if (workbookKey == null) {
                for (String candidate : small.keySet()) {
                    if (candidate.endsWith("workbook.xml")) {
                        workbookKey = candidate;
                        break;
                    }
                }
            }
        }
        if (workbookKey == null) {
            throw new IllegalStateException("ملف xlsx غير صالح: لم يتم العثور على workbook.xml");
        }

        String dir = dirOf(workbookKey);
        String relsKey = dir + "_rels/" + fileNameOf(workbookKey) + ".rels";

        Map<String, Relationship> relsById = new HashMap<>();
        String sharedStringsKey = null;
        byte[] relsBytes = small.get(relsKey);
        if (relsBytes != null) {
            for (Relationship rel : parseRelationships(relsBytes)) {
                if (rel.id != null) {
                    relsById.put(rel.id, rel);
                }
                if (sharedStringsKey == null && rel.type != null
                        && rel.type.endsWith("/sharedStrings") && rel.target != null) {
                    sharedStringsKey = resolve(dir, rel.target);
                }
            }
        }

        List<SheetRef> sheets = parseSheets(small.get(workbookKey));
        if (sheets.isEmpty()) {
            throw new IllegalStateException("المصنّف لا يحتوي على أي أوراق");
        }
        for (int i = 0; i < sheets.size(); i++) {
            SheetRef sheet = sheets.get(i);
            Relationship rel = sheet.relId == null ? null : relsById.get(sheet.relId);
            sheet.partKey = (rel != null && rel.target != null)
                    ? resolve(dir, rel.target)
                    : key(dir + "worksheets/sheet" + (i + 1) + ".xml");
        }

        WorkbookLayout layout = new WorkbookLayout();
        layout.sheets.addAll(sheets);
        layout.sharedStringsKey = sharedStringsKey != null ? sharedStringsKey : key(dir + "sharedStrings.xml");
        return layout;
    }

    private List<Relationship> parseRelationships(byte[] bytes) throws Exception {
        final List<Relationship> out = new ArrayList<>();
        sax.parse(new ByteArrayInputStream(bytes), new DefaultHandler() {
            @Override
            public void startElement(String uri, String localName, String qName, Attributes atts) {
                if (!"Relationship".equals(name(localName, qName))) {
                    return;
                }
                if ("External".equalsIgnoreCase(attr(atts, "TargetMode"))) {
                    return;
                }
                Relationship rel = new Relationship();
                rel.id = attr(atts, "Id");
                rel.type = attr(atts, "Type");
                rel.target = attr(atts, "Target");
                out.add(rel);
            }
        });
        return out;
    }

    private List<SheetRef> parseSheets(byte[] workbookXml) throws Exception {
        final List<SheetRef> out = new ArrayList<>();
        sax.parse(new ByteArrayInputStream(workbookXml), new DefaultHandler() {
            @Override
            public void startElement(String uri, String localName, String qName, Attributes atts) {
                if (!"sheet".equals(name(localName, qName))) {
                    return;
                }
                String sheetName = attr(atts, "name");
                if (sheetName == null) {
                    return;
                }
                // The relationship id is the namespaced attribute r:id — its local name is "id"
                // whatever prefix the generator chose.
                out.add(new SheetRef(sheetName, attr(atts, "id")));
            }
        });
        return out;
    }

    // ------------------------------------------------------------------------------------
    // Shared strings
    // ------------------------------------------------------------------------------------

    private List<String> readSharedStrings(byte[] xlsx, final String sharedStringsKey) throws Exception {
        final SharedStringsHandler handler = new SharedStringsHandler();
        forEachEntry(xlsx, new EntryVisitor() {
            @Override
            public boolean visit(String key, InputStream in) throws Exception {
                if (!key.equals(sharedStringsKey)) {
                    return true;
                }
                sax.parse(new NonClosingInputStream(in), handler);
                return false;
            }
        });
        // A workbook with no shared-strings part (numbers / inline strings only) is valid.
        return handler.strings;
    }

    private static final class SharedStringsHandler extends DefaultHandler {
        final List<String> strings = new ArrayList<>();
        private StringBuilder current;
        private boolean inText;
        private int phoneticDepth;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts) {
            String name = name(localName, qName);
            if (name.equals("si")) {
                current = new StringBuilder();
                inText = false;
                phoneticDepth = 0;
            } else if (current != null) {
                if (name.equals("rPh")) {
                    phoneticDepth++;            // furigana / phonetic guide: not part of the value
                } else if (name.equals("t") && phoneticDepth == 0) {
                    inText = true;
                }
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (inText && current != null) {
                current.append(ch, start, length);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            String name = name(localName, qName);
            if (name.equals("t")) {
                inText = false;
            } else if (name.equals("rPh")) {
                if (phoneticDepth > 0) {
                    phoneticDepth--;
                }
            } else if (name.equals("si")) {
                strings.add(current == null ? "" : current.toString());
                current = null;
            }
        }
    }

    // ------------------------------------------------------------------------------------
    // Sheet data
    // ------------------------------------------------------------------------------------

    private static final class SheetHandler extends DefaultHandler {
        final List<List<String>> rows = new ArrayList<>();
        final List<Integer> rowNumbers = new ArrayList<>();
        final List<String> mergedRanges = new ArrayList<>();
        private final List<String> sharedStrings;

        private boolean inSheetData;
        private ArrayList<String> row;
        private int rowNumber;
        private int lastRowNumber;
        private int nextColumn;

        private boolean inCell;
        private String cellType;
        private int cellColumn;
        private final StringBuilder valueText = new StringBuilder();
        private final StringBuilder inlineText = new StringBuilder();
        private boolean inValue;
        private boolean inInline;
        private boolean inInlineText;
        private int phoneticDepth;

        SheetHandler(List<String> sharedStrings) {
            this.sharedStrings = sharedStrings;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts) {
            String name = name(localName, qName);
            if (!inSheetData) {
                // Everything outside <sheetData> (extLst, conditional formatting, ...) is ignored,
                // so same-named elements from extension namespaces can never be mistaken for cells.
                if (name.equals("sheetData")) {
                    inSheetData = true;
                } else if (name.equals("mergeCell")) {
                    String ref = attr(atts, "ref");
                    if (ref != null && !ref.isEmpty()) {
                        mergedRanges.add(ref);
                    }
                }
                return;
            }
            switch (name) {
                case "row": {
                    row = new ArrayList<>();
                    nextColumn = 0;
                    String rowRef = attr(atts, "r");
                    int parsedRow = -1;
                    if (rowRef != null) {
                        try {
                            parsedRow = Integer.parseInt(rowRef.trim());
                        } catch (NumberFormatException e) {
                            parsedRow = -1;
                        }
                    }
                    rowNumber = parsedRow > 0 ? parsedRow : lastRowNumber + 1;
                    break;
                }
                case "c": {
                    inCell = true;
                    cellType = attr(atts, "t");
                    String ref = attr(atts, "r");
                    int column = ref != null ? columnIndex(ref) : -1;
                    cellColumn = column >= 0 ? column : nextColumn;
                    valueText.setLength(0);
                    inlineText.setLength(0);
                    inValue = false;
                    inInline = false;
                    inInlineText = false;
                    phoneticDepth = 0;
                    break;
                }
                case "v": {
                    if (inCell) {
                        inValue = true;
                    }
                    break;
                }
                case "is": {
                    if (inCell) {
                        inInline = true;
                    }
                    break;
                }
                case "rPh": {
                    if (inInline) {
                        phoneticDepth++;
                    }
                    break;
                }
                case "t": {
                    if (inInline && phoneticDepth == 0) {
                        inInlineText = true;
                    }
                    break;
                }
                default:
                    break;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (inValue) {
                valueText.append(ch, start, length);
            } else if (inInlineText) {
                inlineText.append(ch, start, length);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if (!inSheetData) {
                return;
            }
            String name = name(localName, qName);
            switch (name) {
                case "sheetData": {
                    inSheetData = false;
                    break;
                }
                case "v": {
                    inValue = false;
                    break;
                }
                case "t": {
                    inInlineText = false;
                    break;
                }
                case "rPh": {
                    if (phoneticDepth > 0) {
                        phoneticDepth--;
                    }
                    break;
                }
                case "is": {
                    inInline = false;
                    break;
                }
                case "c": {
                    if (inCell) {
                        finishCell();
                    }
                    inCell = false;
                    break;
                }
                case "row": {
                    if (row != null) {
                        rows.add(row);
                        rowNumbers.add(rowNumber);
                        lastRowNumber = rowNumber;
                    }
                    row = null;
                    break;
                }
                default:
                    break;
            }
        }

        private void finishCell() {
            int column = cellColumn;
            nextColumn = Math.min(column, MAX_COLUMNS) + 1;
            if (row == null || column < 0 || column >= MAX_COLUMNS) {
                return;
            }
            String text = cellText();
            while (row.size() <= column) {
                row.add("");
            }
            row.set(column, text);
        }

        private String cellText() {
            String type = cellType == null ? "n" : cellType;
            switch (type) {
                case "s": {
                    String v = valueText.toString().trim();
                    if (v.isEmpty()) {
                        return "";
                    }
                    try {
                        int index = Integer.parseInt(v);
                        return (index >= 0 && index < sharedStrings.size()) ? sharedStrings.get(index) : "";
                    } catch (NumberFormatException e) {
                        return "";
                    }
                }
                case "inlineStr":
                    return inlineText.toString();
                case "b": {
                    String v = valueText.toString().trim();
                    if (v.isEmpty()) {
                        return "";
                    }
                    return ("1".equals(v) || "true".equalsIgnoreCase(v)) ? "TRUE" : "FALSE";
                }
                case "str":
                case "e":
                case "d":
                    return valueText.toString();
                default:
                    return canonicalNumber(valueText.toString());
            }
        }
    }

    // ------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------

    /**
     * Excel writes numbers as they were computed ({@code 19.989999999999998}, {@code 1E-3},
     * {@code 1.5E+3}). Return a plain decimal instead so barcodes, quantities and prices reach the
     * validator as ordinary digits. Fractional values with more than 15 significant digits are
     * rounded to 15 — Excel's own display precision — so floating-point noise never leaks into a
     * price; integers are never rounded (an ID stays exactly what was stored).
     */
    static String canonicalNumber(String raw) {
        String s = raw.trim();
        if (s.isEmpty() || s.length() > 40) {
            return s;
        }
        try {
            BigDecimal d = new BigDecimal(s);
            if (d.signum() == 0) {
                return "0";
            }
            if (d.scale() > 0 && d.precision() > 15) {
                d = d.round(new MathContext(15, RoundingMode.HALF_EVEN));
            }
            if (Math.abs(d.scale()) > 60) {
                return s;
            }
            return d.stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
            return s;
        }
    }

    /** "A1" -> 0, "AA5" -> 26, "XFD1" -> 16383. Returns -1 when there are no leading letters. */
    static int columnIndex(String ref) {
        int column = 0;
        int i = 0;
        for (; i < ref.length(); i++) {
            char c = ref.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                column = column * 26 + (c - 'A' + 1);
            } else if (c >= 'a' && c <= 'z') {
                column = column * 26 + (c - 'a' + 1);
            } else {
                break;
            }
            if (column > MAX_COLUMNS + 1) {
                return MAX_COLUMNS;             // absurdly wide: out of range, and no int overflow
            }
        }
        return i == 0 ? -1 : column - 1;
    }

    /** Local element/attribute name, whether or not the parser reports namespaces. */
    private static String name(String localName, String qName) {
        if (localName != null && !localName.isEmpty()) {
            return localName;
        }
        return stripPrefix(qName);
    }

    private static String stripPrefix(String qName) {
        if (qName == null) {
            return "";
        }
        int colon = qName.indexOf(':');
        return colon >= 0 ? qName.substring(colon + 1) : qName;
    }

    private static String attr(Attributes atts, String wanted) {
        for (int i = 0; i < atts.getLength(); i++) {
            String local = atts.getLocalName(i);
            String candidate = (local != null && !local.isEmpty()) ? local : stripPrefix(atts.getQName(i));
            if (candidate.equals(wanted)) {
                return atts.getValue(i);
            }
        }
        return null;
    }

    /** Normalized, lower-cased zip-entry key: forward slashes, no leading slash, no "./" or "../". */
    static String key(String path) {
        String p = path.replace('\\', '/');
        String[] segments = p.split("/");
        List<String> kept = new ArrayList<>();
        for (String segment : segments) {
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
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < kept.size(); i++) {
            if (i > 0) {
                joined.append('/');
            }
            joined.append(kept.get(i));
        }
        return joined.toString().toLowerCase(Locale.ROOT);
    }

    /** Resolves a relationship target against the directory of the part that declares it. */
    static String resolve(String baseDir, String target) {
        String t = target.replace('\\', '/');
        if (t.startsWith("/")) {
            return key(t);
        }
        return key(baseDir + t);
    }

    private static String dirOf(String key) {
        int slash = key.lastIndexOf('/');
        return slash >= 0 ? key.substring(0, slash + 1) : "";
    }

    private static String fileNameOf(String key) {
        int slash = key.lastIndexOf('/');
        return slash >= 0 ? key.substring(slash + 1) : key;
    }

    private interface EntryVisitor {
        /** Return true to keep scanning, false to stop. */
        boolean visit(String key, InputStream in) throws Exception;
    }

    private static void forEachEntry(byte[] xlsx, EntryVisitor visitor) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(xlsx))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                if (!visitor.visit(key(entry.getName()), zip)) {
                    break;
                }
            }
        }
    }

    private static byte[] readFully(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    /** SAX parsers close the stream they are given; the zip stream must stay open across entries. */
    private static final class NonClosingInputStream extends FilterInputStream {
        NonClosingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public void close() {
            // intentionally empty
        }
    }
}
