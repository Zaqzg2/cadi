package com.inventorysmartai.app.data.assistant.files;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The pure logic behind the assistant's file tools: it takes already-parsed workbooks and the tool's arguments (parsed
 * JSON as maps/lists) and returns plain maps for the model — or the bytes / HTML to save. No Android, no I/O, so all of
 * it is unit-tested on the JVM. The Kotlin side only finds the attachment, calls this, and turns the result into JSON.
 * A bad argument throws {@link IllegalArgumentException} with an Arabic message the model can act on.
 */
public final class FileToolLogic {

    public static final int DEFAULT_TEXT_CHARS = 6000;
    private static final int MAX_TEXT_CHARS = 12000;
    private static final int MAX_EXPRESSIONS = 20;
    private static final int MAX_EDITS = 400;
    private static final int MAX_SHEETS_WRITTEN = 10;

    private FileToolLogic() {
    }

    // ------------------------------------------------------------------ reading

    /** One-line Arabic summary shown on the attachment chip. */
    public static String workbookSummary(WorkbookData wb) {
        int rows = 0;
        for (SheetData s : wb.sheets()) {
            rows += s.rowNumbers().size();
        }
        int sheets = wb.sheets().size();
        String sheetText = sheets == 1 ? "ورقة واحدة" : sheets + " أوراق";
        return sheetText + " · " + rows + " صف";
    }

    public static Map<String, Object> describeWorkbook(WorkbookData wb) {
        List<Object> sheets = new ArrayList<Object>();
        for (SheetData s : wb.sheets()) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("name", s.name);
            m.put("usedRows", s.rowNumbers().size());
            m.put("lastRow", s.maxRow());
            m.put("lastColumn", s.maxCol() < 0 ? "" : A1.columnLetters(s.maxCol()));
            sheets.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("sheets", sheets);
        return out;
    }

    public static SheetData requireSheet(WorkbookData wb, Object sheetName) {
        String wanted = Specs.string(sheetName).trim();
        SheetData sheet = wb.sheet(wanted);
        if (sheet == null) {
            throw new IllegalArgumentException(wb.isEmpty()
                    ? "الملف لا يحتوي جداول"
                    : "الورقة \"" + wanted + "\" غير موجودة أو الاسم ملتبس. الأوراق المتاحة: " + join(wb.sheetNames()));
        }
        return sheet;
    }

    public static Map<String, Object> readWorkbook(WorkbookData wb, Map<String, ?> args) {
        SheetData sheet = requireSheet(wb, args.get("sheet"));
        Map<String, Object> out = SheetRenderer.render(sheet,
                Specs.integer(args.get("fromRow"), 1),
                Specs.integer(args.get("maxRows"), 0),
                Specs.integer(args.get("maxChars"), 0),
                Specs.string(args.get("find")));
        if (wb.sheets().size() > 1) {
            out.put("allSheets", wb.sheetNames());
        }
        return out;
    }

    /** Text of a Word/OCR/plain-text attachment, paged by characters. {@code tables} (nullable) lists tables found inside it. */
    public static Map<String, Object> readText(String text, WorkbookData tables, Map<String, ?> args) {
        int from = Math.max(0, Specs.integer(args.get("fromChar"), 0));
        int size = Specs.integer(args.get("maxChars"), DEFAULT_TEXT_CHARS);
        size = Math.max(500, Math.min(MAX_TEXT_CHARS, size));
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("type", "TEXT");
        out.put("totalChars", text.length());
        int start = Math.min(from, text.length());
        int end = Math.min(text.length(), start + size);
        out.put("from", start);
        out.put("text", text.substring(start, end));
        if (end < text.length()) {
            out.put("nextChar", end);
        }
        if (tables != null && !tables.isEmpty()) {
            out.put("tables", tables.sheetNames());
            out.put("tablesNote", "يمكنك قراءة أي جدول كخلايا بتمرير اسمه في sheet، أو إجراء حسابات عليه بـ queryTable");
        }
        return out;
    }

    // ------------------------------------------------------------------ calculating

    public static Map<String, Object> queryTable(WorkbookData wb, Map<String, ?> args) {
        return TableQuery.run(requireSheet(wb, args.get("sheet")), args);
    }

    public static Map<String, Object> calculate(Map<String, ?> args) {
        List<Object> expressions = Specs.list(args.get("expressions"));
        if (expressions.isEmpty() && args.get("expression") != null) {
            Map<String, Object> single = new LinkedHashMap<String, Object>();
            String e = Specs.string(args.get("expression"));
            single.put("expression", e);
            single.put("value", CellValues.numberObject(ExpressionEvaluator.evaluate(e)));
            return single;
        }
        if (expressions.isEmpty()) {
            throw new IllegalArgumentException("مطلوب expression أو expressions");
        }
        List<Object> results = new ArrayList<Object>();
        for (int i = 0; i < Math.min(expressions.size(), MAX_EXPRESSIONS); i++) {
            String e = Specs.string(expressions.get(i));
            Map<String, Object> one = new LinkedHashMap<String, Object>();
            one.put("expression", e);
            try {
                one.put("value", CellValues.numberObject(ExpressionEvaluator.evaluate(e)));
            } catch (IllegalArgumentException bad) {
                one.put("error", bad.getMessage());
            }
            results.add(one);
        }
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("results", results);
        return out;
    }

    // ------------------------------------------------------------------ writing

    /** Cells to fill: {cells: [{cell, value, sheet?}], sheet?}. Values stay as given: numbers numeric, strings text. */
    public static List<XlsxTemplateFiller.Edit> parseEdits(Map<String, ?> args) {
        List<Object> cells = Specs.list(args.get("cells"));
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("لا توجد خلايا لتعبئتها: مرّر cells كقائمة من {cell, value}");
        }
        if (cells.size() > MAX_EDITS) {
            throw new IllegalArgumentException("عدد الخلايا كبير (" + cells.size() + "). الحد الأقصى " + MAX_EDITS + " في الاستدعاء الواحد؛ قسّمها على عدة استدعاءات");
        }
        String defaultSheet = Specs.string(args.get("sheet")).trim();
        List<XlsxTemplateFiller.Edit> edits = new ArrayList<XlsxTemplateFiller.Edit>();
        for (Object raw : cells) {
            Map<String, Object> c = Specs.map(raw);
            String address = Specs.string(c.get("cell")).trim();
            if (address.isEmpty()) {
                throw new IllegalArgumentException("عنصر في cells بلا عنوان خلية (cell)");
            }
            String sheet = Specs.string(c.get("sheet")).trim();
            Object value = c.get("value");
            if (value instanceof Map || value instanceof List) {
                value = String.valueOf(value);
            }
            edits.add(new XlsxTemplateFiller.Edit(sheet.isEmpty() ? (defaultSheet.isEmpty() ? null : defaultSheet) : sheet, address, value));
        }
        return edits;
    }

    /** New workbook content: {sheets: [{name, headers, rows}]} or the one-sheet shorthand {headers, rows, sheetName}. */
    public static List<XlsxWriter.SheetSpec> parseSheets(Map<String, ?> args) {
        List<XlsxWriter.SheetSpec> out = new ArrayList<XlsxWriter.SheetSpec>();
        List<Object> given = Specs.list(args.get("sheets"));
        if (given.isEmpty()) {
            given = new ArrayList<Object>();
            Map<String, Object> single = new LinkedHashMap<String, Object>();
            single.put("name", args.get("sheetName") != null ? args.get("sheetName") : args.get("title"));
            single.put("headers", args.get("headers"));
            single.put("rows", args.get("rows"));
            given.add(single);
        }
        for (int i = 0; i < Math.min(given.size(), MAX_SHEETS_WRITTEN); i++) {
            Map<String, Object> s = Specs.map(given.get(i));
            List<String> headers = Specs.strings(s.get("headers"));
            List<List<Object>> rows = new ArrayList<List<Object>>();
            for (Object rawRow : Specs.list(s.get("rows"))) {
                List<Object> row = new ArrayList<Object>();
                for (Object cell : Specs.list(rawRow)) {
                    if (cell instanceof Map || cell instanceof List) {
                        row.add(String.valueOf(cell));
                    } else if (cell instanceof String && CellValues.plainNumber((String) cell) != null) {
                        row.add(CellValues.plainNumber((String) cell)); // "120" -> a real number, so the sheet can be summed
                    } else {
                        row.add(cell);
                    }
                }
                rows.add(row);
            }
            if (headers.isEmpty() && rows.isEmpty()) {
                continue;
            }
            out.add(new XlsxWriter.SheetSpec(Specs.string(s.get("name")), headers, rows));
        }
        if (out.isEmpty()) {
            throw new IllegalArgumentException("لا توجد بيانات لكتابتها: مرّر headers وrows (أو sheets)");
        }
        return out;
    }

    // ------------------------------------------------------------------ dashboards

    /** Every attachment id mentioned anywhere in the arguments (under the key "attachmentId"). */
    public static Set<String> referencedAttachmentIds(Object args) {
        Set<String> ids = new LinkedHashSet<String>();
        collectIds(args, ids);
        return ids;
    }

    private static void collectIds(Object node, Set<String> ids) {
        if (node instanceof Map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) node).entrySet()) {
                if ("attachmentId".equals(String.valueOf(e.getKey())) && e.getValue() != null) {
                    String id = Specs.string(e.getValue()).trim();
                    if (!id.isEmpty()) {
                        ids.add(id);
                    }
                } else {
                    collectIds(e.getValue(), ids);
                }
            }
        } else if (node instanceof List) {
            for (Object o : (List<?>) node) {
                collectIds(o, ids);
            }
        }
    }

    /**
     * Builds the dashboard page. KPIs, charts and tables may carry a {@code source} (the same arguments as queryTable plus
     * attachmentId): the numbers are then computed here from the file instead of being copied by the model.
     * A chart's source needs groupBy + aggregates (first column = labels, the others = series); a KPI's source needs
     * aggregates (the first value is shown); a table's source is a plain query (its columns and rows are shown).
     */
    public static String dashboardHtml(Map<String, ?> args, Function<String, WorkbookData> workbooks, String generatedAt) {
        Map<String, Object> spec = new LinkedHashMap<String, Object>(Specs.map(args));

        List<Object> kpis = new ArrayList<Object>();
        for (Object raw : Specs.list(args.get("kpis"))) {
            Map<String, Object> kpi = new LinkedHashMap<String, Object>(Specs.map(raw));
            if (kpi.get("source") != null) {
                Map<String, Object> result = runSource(kpi.remove("source"), workbooks);
                List<?> rows = (List<?>) result.get("rows");
                if (rows.isEmpty() || ((List<?>) rows.get(0)).isEmpty()) {
                    throw new IllegalArgumentException("مصدر المؤشر \"" + Specs.string(kpi.get("label")) + "\" لم يُرجع أي قيمة");
                }
                kpi.put("value", ((List<?>) rows.get(0)).get(0));
            }
            kpis.add(kpi);
        }
        spec.put("kpis", kpis);

        List<Object> charts = new ArrayList<Object>();
        for (Object raw : Specs.list(args.get("charts"))) {
            Map<String, Object> chart = new LinkedHashMap<String, Object>(Specs.map(raw));
            if (chart.get("source") != null) {
                Map<String, Object> result = runSource(chart.remove("source"), workbooks);
                List<?> columns = (List<?>) result.get("columns");
                List<?> rows = (List<?>) result.get("rows");
                if (columns.size() < 2) {
                    throw new IllegalArgumentException("مصدر الرسم يحتاج groupBy وaggregates (عمود للتسميات وعمود للقيم على الأقل)");
                }
                List<Object> labels = new ArrayList<Object>();
                for (Object row : rows) {
                    labels.add(Specs.string(((List<?>) row).get(0)));
                }
                if (columns.size() == 2) {
                    List<Object> values = new ArrayList<Object>();
                    for (Object row : rows) {
                        values.add(((List<?>) row).get(1));
                    }
                    chart.put("values", values);
                } else {
                    List<Object> series = new ArrayList<Object>();
                    for (int c = 1; c < columns.size(); c++) {
                        Map<String, Object> one = new LinkedHashMap<String, Object>();
                        one.put("name", String.valueOf(columns.get(c)));
                        List<Object> values = new ArrayList<Object>();
                        for (Object row : rows) {
                            values.add(((List<?>) row).get(c));
                        }
                        one.put("values", values);
                        series.add(one);
                    }
                    chart.put("series", series);
                }
                chart.put("labels", labels);
            }
            charts.add(chart);
        }
        spec.put("charts", charts);

        List<Object> tables = new ArrayList<Object>();
        for (Object raw : Specs.list(args.get("tables"))) {
            Map<String, Object> table = new LinkedHashMap<String, Object>(Specs.map(raw));
            if (table.get("source") != null) {
                Map<String, Object> result = runSource(table.remove("source"), workbooks);
                table.put("headers", result.get("columns"));
                table.put("rows", result.get("rows"));
            }
            tables.add(table);
        }
        spec.put("tables", tables);

        return DashboardBuilder.build(spec, generatedAt);
    }

    private static Map<String, Object> runSource(Object source, Function<String, WorkbookData> workbooks) {
        Map<String, Object> src = Specs.map(source);
        String id = Specs.string(src.get("attachmentId")).trim();
        WorkbookData wb = workbooks.apply(id);
        if (wb == null || wb.isEmpty()) {
            throw new IllegalArgumentException("الملف \"" + id + "\" غير موجود أو لا يحتوي جدولًا يمكن الحساب عليه");
        }
        return TableQuery.run(requireSheet(wb, src.get("sheet")), src);
    }

    private static String join(List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (String s : items) {
            if (sb.length() > 0) {
                sb.append("، ");
            }
            sb.append(s);
        }
        return sb.toString();
    }
}
