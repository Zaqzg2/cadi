package com.inventorysmartai.app.data.assistant.files;

import java.util.Locale;

/**
 * Spreadsheet cell addresses: {@code "B3"} is (row 3, column index 1). Rows are 1-based as in Excel,
 * column indexes are 0-based. Plain JDK only (no Android classes), so the whole file-handling engine in this
 * package is compiled and tested on the plain JVM.
 */
public final class A1 {

    public static final int MAX_COLUMNS = 16384; // Excel's limit (XFD)
    public static final int MAX_ROWS = 1048576;

    private A1() {
    }

    /** One cell position. */
    public static final class Ref {
        public final int row;
        public final int col;

        public Ref(int row, int col) {
            this.row = row;
            this.col = col;
        }

        @Override
        public String toString() {
            return format(row, col);
        }
    }

    /** A rectangular block of cells such as {@code A1:C3}; a single cell has the same first and last corner. */
    public static final class Range {
        public final int firstRow;
        public final int firstCol;
        public final int lastRow;
        public final int lastCol;

        public Range(int firstRow, int firstCol, int lastRow, int lastCol) {
            this.firstRow = Math.min(firstRow, lastRow);
            this.lastRow = Math.max(firstRow, lastRow);
            this.firstCol = Math.min(firstCol, lastCol);
            this.lastCol = Math.max(firstCol, lastCol);
        }

        public boolean contains(int row, int col) {
            return row >= firstRow && row <= lastRow && col >= firstCol && col <= lastCol;
        }

        public boolean isSingleCell() {
            return firstRow == lastRow && firstCol == lastCol;
        }

        /** True when any row of this range lies inside [fromRow, toRow]. */
        public boolean touchesRows(int fromRow, int toRow) {
            return lastRow >= fromRow && firstRow <= toRow;
        }

        @Override
        public String toString() {
            String a = format(firstRow, firstCol);
            return isSingleCell() ? a : a + ":" + format(lastRow, lastCol);
        }
    }

    /** "A" -> 0, "AA" -> 26, "XFD" -> 16383; -1 when the text is not 1-3 letters or is out of range. */
    public static int columnIndex(String letters) {
        if (letters == null || letters.isEmpty() || letters.length() > 3) {
            return -1;
        }
        int value = 0;
        for (int i = 0; i < letters.length(); i++) {
            char c = letters.charAt(i);
            if (c >= 'a' && c <= 'z') {
                c = (char) (c - 'a' + 'A');
            }
            if (c < 'A' || c > 'Z') {
                return -1;
            }
            value = value * 26 + (c - 'A' + 1);
        }
        int index = value - 1;
        return index < MAX_COLUMNS ? index : -1;
    }

    /** 0 -> "A", 26 -> "AA". */
    public static String columnLetters(int index) {
        if (index < 0) {
            return "?";
        }
        StringBuilder sb = new StringBuilder();
        int n = index + 1;
        while (n > 0) {
            int rem = (n - 1) % 26;
            sb.append((char) ('A' + rem));
            n = (n - 1) / 26;
        }
        return sb.reverse().toString();
    }

    public static String format(int row, int col) {
        return columnLetters(col) + row;
    }

    /** "B3", "$B$3" or "b3" -> Ref(3, 1); null when this is not a valid cell address. */
    public static Ref parse(String address) {
        if (address == null) {
            return null;
        }
        String s = address.replace("$", "").trim().toUpperCase(Locale.ROOT);
        int i = 0;
        while (i < s.length() && s.charAt(i) >= 'A' && s.charAt(i) <= 'Z') {
            i++;
        }
        if (i == 0 || i > 3 || i == s.length() || s.length() - i > 7) {
            return null;
        }
        int col = columnIndex(s.substring(0, i));
        if (col < 0) {
            return null;
        }
        int row = 0;
        for (int j = i; j < s.length(); j++) {
            char c = s.charAt(j);
            if (c < '0' || c > '9') {
                return null;
            }
            row = row * 10 + (c - '0');
        }
        if (row < 1 || row > MAX_ROWS) {
            return null;
        }
        return new Ref(row, col);
    }

    /** "A1:C3" or "A1"; null when invalid. */
    public static Range parseRange(String text) {
        if (text == null) {
            return null;
        }
        String s = text.trim();
        int colon = s.indexOf(':');
        if (colon < 0) {
            Ref one = parse(s);
            return one == null ? null : new Range(one.row, one.col, one.row, one.col);
        }
        Ref a = parse(s.substring(0, colon));
        Ref b = parse(s.substring(colon + 1));
        if (a == null || b == null) {
            return null;
        }
        return new Range(a.row, a.col, b.row, b.col);
    }
}
