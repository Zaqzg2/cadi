package com.inventorysmartai.app.data.assistant.files;

import com.inventorysmartai.app.data.importing.parser.XlsxSaxReader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * One sheet held as sparse cells keyed by their real address (row 1-based, column 0-based), plus its merged
 * ranges. Keeping the true row numbers is what lets the assistant say "put 12 in C7" and have it land in C7.
 */
public final class SheetData {

    public final String name;
    private final TreeMap<Integer, TreeMap<Integer, String>> rows = new TreeMap<Integer, TreeMap<Integer, String>>();
    private final List<A1.Range> merges = new ArrayList<A1.Range>();
    private int maxCol = -1;

    public SheetData(String name) {
        this.name = name == null ? "" : name;
    }

    /** Blank (or whitespace-only) text is not stored. */
    public void put(int row, int col, String text) {
        if (row < 1 || col < 0 || CellValues.isBlank(text)) {
            return;
        }
        TreeMap<Integer, String> r = rows.get(row);
        if (r == null) {
            r = new TreeMap<Integer, String>();
            rows.put(row, r);
        }
        r.put(col, text);
        if (col > maxCol) {
            maxCol = col;
        }
    }

    /** The raw cell text, or "" when the cell is empty. */
    public String get(int row, int col) {
        TreeMap<Integer, String> r = rows.get(row);
        if (r == null) {
            return "";
        }
        String v = r.get(col);
        return v == null ? "" : v;
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    public int maxRow() {
        return rows.isEmpty() ? 0 : rows.lastKey();
    }

    public int firstRow() {
        return rows.isEmpty() ? 0 : rows.firstKey();
    }

    /** Highest used column index, -1 when empty. */
    public int maxCol() {
        return maxCol;
    }

    /** Row numbers that contain at least one non-empty cell, ascending. */
    public List<Integer> rowNumbers() {
        return new ArrayList<Integer>(rows.keySet());
    }

    /** The non-empty cells of one row (column index -> text); empty map when the row has none. */
    public SortedMap<Integer, String> row(int row) {
        TreeMap<Integer, String> r = rows.get(row);
        return r == null ? new TreeMap<Integer, String>() : Collections.unmodifiableSortedMap(r);
    }

    public int nonEmptyCount() {
        int n = 0;
        for (TreeMap<Integer, String> r : rows.values()) {
            n += r.size();
        }
        return n;
    }

    public void addMerge(A1.Range range) {
        if (range != null && !range.isSingleCell()) {
            merges.add(range);
        }
    }

    public List<A1.Range> merges() {
        return Collections.unmodifiableList(merges);
    }

    /** The merged range that covers this cell, or null. */
    public A1.Range mergeAt(int row, int col) {
        for (A1.Range range : merges) {
            if (range.contains(row, col)) {
                return range;
            }
        }
        return null;
    }

    public List<A1.Range> mergesInRows(int fromRow, int toRow) {
        List<A1.Range> out = new ArrayList<A1.Range>();
        for (A1.Range range : merges) {
            if (range.touchesRows(fromRow, toRow)) {
                out.add(range);
            }
        }
        return out;
    }

    /** From the SAX reader's table (rows keep their real sheet row numbers; merged ranges are kept). */
    public static SheetData fromTable(XlsxSaxReader.Table table) {
        SheetData sheet = new SheetData(table.sheetName);
        for (int i = 0; i < table.rows.size(); i++) {
            int rowNumber = i < table.rowNumbers.size() ? table.rowNumbers.get(i) : i + 1;
            List<String> cells = table.rows.get(i);
            for (int c = 0; c < cells.size(); c++) {
                sheet.put(rowNumber, c, cells.get(c));
            }
        }
        for (String ref : table.mergedRanges) {
            sheet.addMerge(A1.parseRange(ref));
        }
        return sheet;
    }

    /** Rows numbered 1..n in order (CSV, tables read from a photo). */
    public static SheetData fromRows(String name, List<List<String>> data) {
        SheetData sheet = new SheetData(name);
        for (int i = 0; i < data.size(); i++) {
            List<String> cells = data.get(i);
            for (int c = 0; c < cells.size(); c++) {
                sheet.put(i + 1, c, cells.get(c));
            }
        }
        return sheet;
    }
}
