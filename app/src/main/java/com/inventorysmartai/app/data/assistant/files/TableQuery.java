package com.inventorysmartai.app.data.assistant.files;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

/**
 * Exact filtering, grouping and totals over a sheet that has a header row. This exists so the model never adds up
 * numbers in its head: it describes WHAT to compute and this class computes it.
 *
 * Spec (all optional): headerRow, filters [{column, op, value}], groupBy [column...], aggregates [{column, op, as}],
 * sortBy {column, direction}, select [column...], limit. A column is named by its header text or by its letter ("C").
 * Cells such as "10 كرتون" count as the number 10.
 */
public final class TableQuery {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 200;

    private TableQuery() {
    }

    private static final class Column {
        final int index;
        final String name;

        Column(int index, String name) {
            this.index = index;
            this.name = name;
        }
    }

    private static final class Filter {
        final Column column;
        final String op;
        final String value;
        final List<String> values;

        Filter(Column column, String op, String value, List<String> values) {
            this.column = column;
            this.op = op;
            this.value = value;
            this.values = values;
        }

        boolean matches(SheetData sheet, int row) {
            String cell = CellValues.clean(sheet.get(row, column.index));
            if (op.equals("empty")) {
                return cell.isEmpty();
            }
            if (op.equals("notEmpty")) {
                return !cell.isEmpty();
            }
            if (op.equals("contains")) {
                return CellValues.normalizeForSearch(cell).contains(CellValues.normalizeForSearch(value));
            }
            if (op.equals("notContains")) {
                return !CellValues.normalizeForSearch(cell).contains(CellValues.normalizeForSearch(value));
            }
            if (op.equals("in")) {
                for (String candidate : values) {
                    if (same(cell, candidate)) {
                        return true;
                    }
                }
                return false;
            }
            if (op.equals("eq")) {
                return same(cell, value);
            }
            if (op.equals("ne")) {
                return !same(cell, value);
            }
            int cmp = compare(cell, value);
            if (op.equals("gt")) {
                return cmp > 0;
            }
            if (op.equals("gte")) {
                return cmp >= 0;
            }
            if (op.equals("lt")) {
                return cmp < 0;
            }
            return cmp <= 0; // lte
        }
    }

    private static boolean same(String a, String b) {
        Double x = CellValues.parseNumber(a);
        Double y = CellValues.parseNumber(b);
        if (x != null && y != null) {
            return Math.abs(x - y) < 1e-9;
        }
        return CellValues.normalizeForSearch(a).equals(CellValues.normalizeForSearch(b));
    }

    private static int compare(String a, String b) {
        Double x = CellValues.parseNumber(a);
        Double y = CellValues.parseNumber(b);
        if (x != null && y != null) {
            return Double.compare(x, y);
        }
        return CellValues.normalizeForSearch(a).compareTo(CellValues.normalizeForSearch(b));
    }

    private static final class Agg {
        final Column column; // null only for a plain row count
        final String op;
        final String label;

        Agg(Column column, String op, String label) {
            this.column = column;
            this.op = op;
            this.label = label;
        }
    }

    private static final class Acc {
        double sum;
        int numeric;
        int nonEmpty;
        int rows;
        int skippedText;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        final Set<String> distinct = new HashSet<String>();
        final Set<String> units = new LinkedHashSet<String>();
    }

    // ------------------------------------------------------------------ header detection

    /** The first of the leading rows that looks like column titles: several cells, mostly text, with data below it. */
    public static int guessHeaderRow(SheetData sheet) {
        List<Integer> rowNumbers = sheet.rowNumbers();
        for (int i = 0; i < rowNumbers.size() && i < 20; i++) {
            SortedMap<Integer, String> cells = sheet.row(rowNumbers.get(i));
            if (cells.size() < 2 || i + 1 >= rowNumbers.size()) {
                continue;
            }
            int textual = 0;
            for (String v : cells.values()) {
                if (CellValues.parseNumber(v) == null) {
                    textual++;
                }
            }
            if (textual * 10 >= cells.size() * 7) {
                return rowNumbers.get(i);
            }
        }
        return rowNumbers.isEmpty() ? 1 : rowNumbers.get(0);
    }

    private static List<Column> buildColumns(SheetData sheet, int headerRow) {
        List<Column> columns = new ArrayList<Column>();
        Set<String> used = new HashSet<String>();
        int last = sheet.maxCol();
        SortedMap<Integer, String> header = sheet.row(headerRow);
        for (int c = 0; c <= last; c++) {
            boolean hasData = false;
            for (int r : sheet.rowNumbers()) {
                if (r > headerRow && !sheet.get(r, c).isEmpty()) {
                    hasData = true;
                    break;
                }
            }
            String title = CellValues.clean(header.get(c));
            if (title.isEmpty() && !hasData) {
                continue;
            }
            if (title.isEmpty()) {
                title = "عمود " + A1.columnLetters(c);
            }
            String unique = title;
            int n = 2;
            while (!used.add(CellValues.normalizeForSearch(unique))) {
                unique = title + " (" + n++ + ")";
            }
            columns.add(new Column(c, unique));
        }
        return columns;
    }

    private static Column resolve(String ref, List<Column> columns) {
        String wanted = CellValues.normalizeForSearch(ref);
        for (Column c : columns) {
            if (CellValues.normalizeForSearch(c.name).equals(wanted)) {
                return c;
            }
        }
        List<Column> partial = new ArrayList<Column>();
        for (Column c : columns) {
            if (!wanted.isEmpty() && CellValues.normalizeForSearch(c.name).contains(wanted)) {
                partial.add(c);
            }
        }
        if (partial.size() == 1) {
            return partial.get(0);
        }
        if (partial.size() > 1) {
            throw new IllegalArgumentException("اسم العمود \"" + ref + "\" ينطبق على أكثر من عمود: " + names(partial) + ". استخدم الاسم الكامل أو حرف العمود");
        }
        int letterIndex = A1.columnIndex(ref.trim());
        if (letterIndex >= 0) {
            for (Column c : columns) {
                if (c.index == letterIndex) {
                    return c;
                }
            }
        }
        throw new IllegalArgumentException("العمود \"" + ref + "\" غير موجود. الأعمدة المتاحة: " + names(columns));
    }

    private static String names(List<Column> columns) {
        StringBuilder sb = new StringBuilder();
        for (Column c : columns) {
            if (sb.length() > 0) {
                sb.append("، ");
            }
            sb.append(c.name).append(" (").append(A1.columnLetters(c.index)).append(')');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ the query

    public static Map<String, Object> run(SheetData sheet, Map<String, ?> spec) {
        if (sheet.isEmpty()) {
            throw new IllegalArgumentException("الورقة فارغة");
        }
        int headerRow = Specs.integer(spec.get("headerRow"), guessHeaderRow(sheet));
        List<Column> columns = buildColumns(sheet, headerRow);
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("لا توجد عناوين أعمدة في الصف " + headerRow + ". حدّد headerRow الصحيح");
        }

        List<Filter> filters = new ArrayList<Filter>();
        for (Object raw : Specs.list(spec.get("filters"))) {
            Map<String, Object> f = Specs.map(raw);
            String op = Specs.string(f.get("op")).trim();
            if (op.isEmpty()) {
                op = "eq";
            }
            if (!isFilterOp(op)) {
                throw new IllegalArgumentException("عملية التصفية \"" + op + "\" غير معروفة. المسموح: eq, ne, contains, notContains, gt, gte, lt, lte, in, empty, notEmpty");
            }
            Column column = resolve(Specs.string(f.get("column")), columns);
            filters.add(new Filter(column, op, Specs.string(f.get("value")), Specs.strings(f.get("value"))));
        }

        List<Integer> rows = new ArrayList<Integer>();
        for (int r : sheet.rowNumbers()) {
            if (r <= headerRow) {
                continue;
            }
            boolean keep = true;
            for (Filter f : filters) {
                if (!f.matches(sheet, r)) {
                    keep = false;
                    break;
                }
            }
            if (keep) {
                rows.add(r);
            }
        }

        List<Column> groupBy = new ArrayList<Column>();
        for (String ref : Specs.strings(spec.get("groupBy"))) {
            groupBy.add(resolve(ref, columns));
        }
        List<Agg> aggs = new ArrayList<Agg>();
        for (Object raw : Specs.list(spec.get("aggregates"))) {
            Map<String, Object> a = Specs.map(raw);
            String op = Specs.string(a.get("op")).trim();
            if (op.isEmpty()) {
                op = "sum";
            }
            if (!isAggOp(op)) {
                throw new IllegalArgumentException("عملية التجميع \"" + op + "\" غير معروفة. المسموح: sum, avg, min, max, count, countDistinct");
            }
            String colRef = Specs.string(a.get("column")).trim();
            Column column = colRef.isEmpty() ? null : resolve(colRef, columns);
            if (column == null && !op.equals("count")) {
                throw new IllegalArgumentException("العملية " + op + " تحتاج عمودًا");
            }
            String label = Specs.string(a.get("as")).trim();
            if (label.isEmpty()) {
                label = column == null ? "count" : op + "(" + column.name + ")";
            }
            aggs.add(new Agg(column, op, label));
        }

        int limit = Math.max(1, Math.min(MAX_LIMIT, Specs.integer(spec.get("limit"), DEFAULT_LIMIT)));
        List<String> notes = new ArrayList<String>();
        List<String> outColumns = new ArrayList<String>();
        List<List<Object>> outRows = new ArrayList<List<Object>>();
        Map<String, Object> totals = new LinkedHashMap<String, Object>();

        if (groupBy.isEmpty() && aggs.isEmpty()) {
            listRows(sheet, columns, spec, rows, outColumns, outRows);
        } else {
            if (aggs.isEmpty()) {
                aggs.add(new Agg(null, "count", "count"));
            }
            aggregate(sheet, rows, groupBy, aggs, outColumns, outRows, totals, notes);
        }

        sort(spec.get("sortBy"), outColumns, outRows);

        int total = outRows.size();
        boolean truncated = total > limit;
        List<List<Object>> shown = truncated ? new ArrayList<List<Object>>(outRows.subList(0, limit)) : outRows;

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("sheet", sheet.name);
        result.put("headerRow", headerRow);
        result.put("matchedRows", rows.size());
        result.put("columns", outColumns);
        result.put("rows", shown);
        result.put("resultRows", total);
        if (truncated) {
            result.put("truncated", true);
        }
        if (!totals.isEmpty()) {
            result.put("totals", totals);
        }
        if (!notes.isEmpty()) {
            result.put("notes", notes);
        }
        return result;
    }

    private static boolean isFilterOp(String op) {
        return op.equals("eq") || op.equals("ne") || op.equals("contains") || op.equals("notContains") || op.equals("gt")
                || op.equals("gte") || op.equals("lt") || op.equals("lte") || op.equals("in") || op.equals("empty") || op.equals("notEmpty");
    }

    private static boolean isAggOp(String op) {
        return op.equals("sum") || op.equals("avg") || op.equals("min") || op.equals("max") || op.equals("count") || op.equals("countDistinct");
    }

    private static void listRows(SheetData sheet, List<Column> columns, Map<String, ?> spec, List<Integer> rows,
                                 List<String> outColumns, List<List<Object>> outRows) {
        List<Column> selected = new ArrayList<Column>();
        for (String ref : Specs.strings(spec.get("select"))) {
            selected.add(resolve(ref, columns));
        }
        if (selected.isEmpty()) {
            selected.addAll(columns);
        }
        outColumns.add("الصف");
        for (Column c : selected) {
            outColumns.add(c.name);
        }
        for (int r : rows) {
            List<Object> line = new ArrayList<Object>();
            line.add(r);
            for (Column c : selected) {
                line.add(CellValues.clean(sheet.get(r, c.index)));
            }
            outRows.add(line);
        }
    }

    private static void aggregate(SheetData sheet, List<Integer> rows, List<Column> groupBy, List<Agg> aggs,
                                  List<String> outColumns, List<List<Object>> outRows, Map<String, Object> totals, List<String> notes) {
        for (Column g : groupBy) {
            outColumns.add(g.name);
        }
        for (Agg a : aggs) {
            outColumns.add(a.label);
        }
        Map<String, List<Acc>> groups = new LinkedHashMap<String, List<Acc>>();
        Map<String, List<String>> groupKeys = new LinkedHashMap<String, List<String>>();
        List<Acc> overall = newAccs(aggs.size());
        for (int r : rows) {
            List<String> key = new ArrayList<String>();
            StringBuilder id = new StringBuilder();
            for (Column g : groupBy) {
                String v = CellValues.clean(sheet.get(r, g.index));
                if (v.isEmpty()) {
                    v = "(فارغ)";
                }
                key.add(v);
                id.append(CellValues.normalizeForSearch(v)).append('\u0001');
            }
            String groupId = id.toString();
            List<Acc> accs = groups.get(groupId);
            if (accs == null) {
                accs = newAccs(aggs.size());
                groups.put(groupId, accs);
                groupKeys.put(groupId, key);
            }
            for (int i = 0; i < aggs.size(); i++) {
                feed(accs.get(i), aggs.get(i), sheet, r);
                feed(overall.get(i), aggs.get(i), sheet, r);
            }
        }
        if (groupBy.isEmpty()) {
            List<Object> line = new ArrayList<Object>();
            for (int i = 0; i < aggs.size(); i++) {
                line.add(value(overall.get(i), aggs.get(i)));
            }
            outRows.add(line);
        } else {
            for (Map.Entry<String, List<Acc>> e : groups.entrySet()) {
                List<Object> line = new ArrayList<Object>(groupKeys.get(e.getKey()));
                for (int i = 0; i < aggs.size(); i++) {
                    line.add(value(e.getValue().get(i), aggs.get(i)));
                }
                outRows.add(line);
            }
            for (int i = 0; i < aggs.size(); i++) {
                if (aggs.get(i).op.equals("sum") || aggs.get(i).op.equals("count")) {
                    totals.put(aggs.get(i).label, value(overall.get(i), aggs.get(i)));
                }
            }
        }
        for (int i = 0; i < aggs.size(); i++) {
            Agg a = aggs.get(i);
            Acc acc = overall.get(i);
            if (a.column == null || a.op.equals("count") || a.op.equals("countDistinct")) {
                continue;
            }
            if (acc.units.size() > 1) {
                notes.add("العمود \"" + a.column.name + "\" فيه وحدات مختلفة (" + join(acc.units) + ") — جُمعت الأرقام كما هي دون تحويل بين الوحدات");
            }
            if (acc.skippedText > 0) {
                notes.add("تم تجاهل " + acc.skippedText + " خلية غير رقمية في العمود \"" + a.column.name + "\"");
            }
        }
    }

    private static List<Acc> newAccs(int n) {
        List<Acc> list = new ArrayList<Acc>();
        for (int i = 0; i < n; i++) {
            list.add(new Acc());
        }
        return list;
    }

    private static void feed(Acc acc, Agg agg, SheetData sheet, int row) {
        acc.rows++;
        if (agg.column == null) {
            return;
        }
        String cell = CellValues.clean(sheet.get(row, agg.column.index));
        if (cell.isEmpty()) {
            return;
        }
        acc.nonEmpty++;
        if (agg.op.equals("countDistinct")) {
            acc.distinct.add(CellValues.normalizeForSearch(cell));
            return;
        }
        if (agg.op.equals("count")) {
            return;
        }
        Double n = CellValues.parseNumber(cell);
        if (n == null) {
            acc.skippedText++;
            return;
        }
        acc.numeric++;
        acc.sum += n;
        acc.min = Math.min(acc.min, n);
        acc.max = Math.max(acc.max, n);
        String unit = CellValues.unitOf(cell);
        if (!unit.isEmpty()) {
            acc.units.add(unit);
        }
    }

    private static Object value(Acc acc, Agg agg) {
        if (agg.op.equals("count")) {
            return agg.column == null ? acc.rows : acc.nonEmpty;
        }
        if (agg.op.equals("countDistinct")) {
            return acc.distinct.size();
        }
        if (acc.numeric == 0) {
            return agg.op.equals("sum") ? (Object) Long.valueOf(0) : null;
        }
        if (agg.op.equals("sum")) {
            return CellValues.numberObject(acc.sum);
        }
        if (agg.op.equals("avg")) {
            return CellValues.numberObject(acc.sum / acc.numeric);
        }
        if (agg.op.equals("min")) {
            return CellValues.numberObject(acc.min);
        }
        return CellValues.numberObject(acc.max);
    }

    private static String join(Set<String> items) {
        StringBuilder sb = new StringBuilder();
        for (String s : items) {
            if (sb.length() > 0) {
                sb.append("، ");
            }
            sb.append(s);
        }
        return sb.toString();
    }

    private static void sort(Object sortSpec, final List<String> columns, List<List<Object>> rows) {
        Map<String, Object> s = Specs.map(sortSpec);
        String ref = Specs.string(s.get("column")).trim();
        if (ref.isEmpty() || rows.size() < 2) {
            return;
        }
        int found = -1;
        String wanted = CellValues.normalizeForSearch(ref);
        for (int i = 0; i < columns.size(); i++) {
            if (CellValues.normalizeForSearch(columns.get(i)).equals(wanted)) {
                found = i;
                break;
            }
        }
        if (found < 0) {
            for (int i = 0; i < columns.size(); i++) {
                if (CellValues.normalizeForSearch(columns.get(i)).contains(wanted)) {
                    found = i;
                    break;
                }
            }
        }
        if (found < 0) {
            return;
        }
        final int index = found;
        final boolean descending = Specs.string(s.get("direction")).trim().equalsIgnoreCase("desc");
        Collections.sort(rows, new Comparator<List<Object>>() {
            @Override
            public int compare(List<Object> a, List<Object> b) {
                Object x = a.get(index);
                Object y = b.get(index);
                int cmp;
                if (x == null || y == null) {
                    cmp = x == null ? (y == null ? 0 : -1) : 1;
                } else if (x instanceof Number && y instanceof Number) {
                    cmp = Double.compare(((Number) x).doubleValue(), ((Number) y).doubleValue());
                } else {
                    cmp = CellValues.normalizeForSearch(String.valueOf(x)).compareTo(CellValues.normalizeForSearch(String.valueOf(y)));
                }
                return descending ? -cmp : cmp;
            }
        });
    }
}
