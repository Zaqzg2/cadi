package com.inventorysmartai.app.data.assistant.files;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

/**
 * Turns part of a sheet into compact text lines the model can read — with REAL cell addresses, so what the model
 * says ("C7") is what the form filler later writes to.
 *
 * Two layouts, picked by how full the sheet is:
 *  - DENSE (a flat table): "12| v1 | v2 | v3", columns in order starting at A;
 *  - SPARSE (a form with scattered cells): "C5=9 حبه | D5=17 حبه", one line per row.
 */
public final class SheetRenderer {

    public static final int DEFAULT_MAX_ROWS = 40;
    public static final int DEFAULT_MAX_CHARS = 5500;
    private static final int CELL_MAX_CHARS = 80;
    private static final int MAX_MATCHES = 25;
    private static final int MAX_MERGES_LISTED = 60;

    private SheetRenderer() {
    }

    public static Map<String, Object> render(SheetData sheet, int fromRow, int maxRows, int maxChars, String find) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        List<Integer> usedRows = sheet.rowNumbers();
        out.put("sheet", sheet.name);
        out.put("usedRows", usedRows.size());
        out.put("lastRow", sheet.maxRow());
        out.put("lastColumn", sheet.maxCol() < 0 ? "" : A1.columnLetters(sheet.maxCol()));

        if (find != null && !CellValues.isBlank(find)) {
            out.put("matches", findCells(sheet, find, out));
            return out;
        }

        double density = usedRows.isEmpty() || sheet.maxCol() < 0
                ? 1.0
                : sheet.nonEmptyCount() / (double) (usedRows.size() * (sheet.maxCol() + 1));
        boolean dense = density >= 0.5;
        out.put("mode", dense ? "DENSE" : "SPARSE");
        out.put("howToRead", dense
                ? "كل سطر: رقم الصف ثم القيم بالترتيب ابتداءً من العمود A"
                : "كل سطر: عناوين الخلايا غير الفارغة مع قيمها (مثل C5=9 حبه)");

        int limitRows = maxRows <= 0 ? DEFAULT_MAX_ROWS : Math.min(maxRows, 200);
        int limitChars = maxChars <= 0 ? DEFAULT_MAX_CHARS : Math.min(maxChars, 12000);
        int start = Math.max(1, fromRow);

        List<String> lines = new ArrayList<String>();
        int chars = 0;
        int firstShown = -1;
        int lastShown = -1;
        Integer next = null;
        for (int rowNumber : usedRows) {
            if (rowNumber < start) {
                continue;
            }
            String line = dense ? denseLine(sheet, rowNumber) : sparseLine(sheet, rowNumber);
            if (!lines.isEmpty() && (lines.size() >= limitRows || chars + line.length() > limitChars)) {
                next = rowNumber;
                break;
            }
            lines.add(line);
            chars += line.length() + 1;
            if (firstShown < 0) {
                firstShown = rowNumber;
            }
            lastShown = rowNumber;
        }
        out.put("lines", lines);
        if (next != null) {
            out.put("nextRow", next);
        }
        if (!dense && firstShown > 0) {
            List<String> merged = new ArrayList<String>();
            for (A1.Range range : sheet.mergesInRows(firstShown, lastShown)) {
                if (merged.size() >= MAX_MERGES_LISTED) {
                    break;
                }
                merged.add(range.toString());
            }
            if (!merged.isEmpty()) {
                out.put("mergedRanges", merged);
                out.put("mergedNote", "الخلية المدمجة تحمل قيمتها في أول خلية فيها (أعلى اليسار)؛ اكتب دائمًا في تلك الخلية");
            }
        }
        return out;
    }

    private static List<Map<String, Object>> findCells(SheetData sheet, String find, Map<String, Object> out) {
        String wanted = CellValues.normalizeForSearch(find);
        List<Map<String, Object>> matches = new ArrayList<Map<String, Object>>();
        boolean more = false;
        for (int rowNumber : sheet.rowNumbers()) {
            for (Map.Entry<Integer, String> cell : sheet.row(rowNumber).entrySet()) {
                if (!CellValues.normalizeForSearch(cell.getValue()).contains(wanted)) {
                    continue;
                }
                if (matches.size() >= MAX_MATCHES) {
                    more = true;
                    break;
                }
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("cell", A1.format(rowNumber, cell.getKey()));
                m.put("text", shorten(CellValues.clean(cell.getValue())));
                A1.Range merge = sheet.mergeAt(rowNumber, cell.getKey());
                if (merge != null) {
                    m.put("mergedRange", merge.toString());
                }
                matches.add(m);
            }
            if (more) {
                break;
            }
        }
        if (more) {
            out.put("matchesTruncated", true);
        }
        return matches;
    }

    private static String denseLine(SheetData sheet, int rowNumber) {
        SortedMap<Integer, String> cells = sheet.row(rowNumber);
        StringBuilder sb = new StringBuilder();
        sb.append(rowNumber).append('|');
        int last = cells.isEmpty() ? -1 : cells.lastKey();
        for (int c = 0; c <= last; c++) {
            sb.append(' ').append(shorten(CellValues.clean(cells.get(c))));
            if (c < last) {
                sb.append(" |");
            }
        }
        return sb.toString();
    }

    private static String sparseLine(SheetData sheet, int rowNumber) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, String> cell : sheet.row(rowNumber).entrySet()) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(A1.format(rowNumber, cell.getKey())).append('=').append(shorten(CellValues.clean(cell.getValue())));
        }
        return sb.toString();
    }

    private static String shorten(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= CELL_MAX_CHARS ? text : text.substring(0, CELL_MAX_CHARS) + "…";
    }
}
