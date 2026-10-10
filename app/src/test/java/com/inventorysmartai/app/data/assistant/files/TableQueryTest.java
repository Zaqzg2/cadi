package com.inventorysmartai.app.data.assistant.files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TableQueryTest {

    private static List<String> row(String... cells) {
        return new ArrayList<String>(Arrays.asList(cells));
    }

    private static SheetData sales() {
        List<List<String>> rows = new ArrayList<List<String>>();
        rows.add(row("المنتج", "المروج", "الكمية", "السعر"));
        rows.add(row("حليب كامل الدسم", "أحمد", "10 كرتون", "25.5"));
        rows.add(row("حليب كامل الدسم", "سعيد", "5 كرتون", "25.5"));
        rows.add(row("عصير فاخر", "أحمد", "7", "40"));
        rows.add(row("عصير فاخر", "سعيد", "3", "40"));
        rows.add(row("طحينية", "أحمد", "4", "18"));
        return SheetData.fromRows("المبيعات", rows);
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static List<Object> list(Object... items) {
        return new ArrayList<Object>(Arrays.asList(items));
    }

    @SuppressWarnings("unchecked")
    private static List<List<Object>> rowsOf(Map<String, Object> result) {
        return (List<List<Object>>) result.get("rows");
    }

    @Test
    public void groupsAndSumsExactly() {
        Map<String, Object> spec = map("groupBy", list("المنتج"), "aggregates", list(map("column", "الكمية", "op", "sum")));
        Map<String, Object> result = TableQuery.run(sales(), spec);
        List<List<Object>> rows = rowsOf(result);
        assertEquals(3, rows.size());
        assertEquals("حليب كامل الدسم", rows.get(0).get(0));
        assertEquals(15L, ((Number) rows.get(0).get(1)).longValue());
        assertEquals(10L, ((Number) rows.get(1).get(1)).longValue());
        assertEquals(4L, ((Number) rows.get(2).get(1)).longValue());
        @SuppressWarnings("unchecked")
        Map<String, Object> totals = (Map<String, Object>) result.get("totals");
        assertEquals(29L, ((Number) totals.get("sum(الكمية)")).longValue());
    }

    @Test
    public void filtersByTextAndNumber() {
        Map<String, Object> milk = TableQuery.run(sales(), map(
                "filters", list(map("column", "المنتج", "op", "contains", "value", "حليب")),
                "aggregates", list(map("column", "الكمية", "op", "sum"))));
        assertEquals(15L, ((Number) rowsOf(milk).get(0).get(0)).longValue());

        Map<String, Object> big = TableQuery.run(sales(), map("filters", list(map("column", "الكمية", "op", "gt", "value", 5))));
        assertEquals(2, ((Number) big.get("matchedRows")).intValue()); // 10 كرتون and 7

        Map<String, Object> ahmad = TableQuery.run(sales(), map("filters", list(map("column", "المروج", "op", "eq", "value", "احمد"))));
        assertEquals(3, ((Number) ahmad.get("matchedRows")).intValue()); // spelling variant أحمد/احمد still matches
    }

    @Test
    public void sortsAndLimits() {
        Map<String, Object> spec = map(
                "groupBy", list("المنتج"),
                "aggregates", list(map("column", "الكمية", "op", "sum", "as", "الإجمالي")),
                "sortBy", map("column", "الإجمالي", "direction", "desc"),
                "limit", 2);
        Map<String, Object> result = TableQuery.run(sales(), spec);
        List<List<Object>> rows = rowsOf(result);
        assertEquals(2, rows.size());
        assertEquals("حليب كامل الدسم", rows.get(0).get(0));
        assertEquals("عصير فاخر", rows.get(1).get(0));
        assertEquals(Boolean.TRUE, result.get("truncated"));
        assertEquals(3, ((Number) result.get("resultRows")).intValue());
    }

    @Test
    public void plainListingKeepsRealRowNumbers() {
        Map<String, Object> result = TableQuery.run(sales(), map("select", list("المنتج", "الكمية"), "filters", list(map("column", "المروج", "op", "eq", "value", "سعيد"))));
        List<List<Object>> rows = rowsOf(result);
        assertEquals(3, ((List<?>) result.get("columns")).size()); // الصف + 2 selected
        assertEquals(3, ((Number) rows.get(0).get(0)).intValue());
        assertEquals(5, ((Number) rows.get(1).get(0)).intValue());
    }

    @Test
    public void mixedUnitsAreReportedNotHidden() {
        List<List<String>> rows = new ArrayList<List<String>>();
        rows.add(row("الصنف", "الكمية"));
        rows.add(row("a", "2 كرتون"));
        rows.add(row("a", "5 حبه"));
        rows.add(row("a", "غير معروف"));
        Map<String, Object> result = TableQuery.run(SheetData.fromRows("t", rows), map("aggregates", list(map("column", "الكمية", "op", "sum"))));
        assertEquals(7L, ((Number) rowsOf(result).get(0).get(0)).longValue());
        String notes = String.valueOf(result.get("notes"));
        assertTrue(notes, notes.contains("كرتون") && notes.contains("حبه"));
        assertTrue(notes, notes.contains("غير رقمية"));
    }

    @Test
    public void headerRowIsFoundBelowATitle() {
        List<List<String>> rows = new ArrayList<List<String>>();
        rows.add(row("تقرير المبيعات"));
        rows.add(row());
        rows.add(row("الصنف", "الكمية"));
        rows.add(row("a", "3"));
        rows.add(row("b", "4"));
        SheetData sheet = SheetData.fromRows("t", rows);
        assertEquals(3, TableQuery.guessHeaderRow(sheet));
        Map<String, Object> result = TableQuery.run(sheet, map("aggregates", list(map("column", "الكمية", "op", "sum"))));
        assertEquals(7L, ((Number) rowsOf(result).get(0).get(0)).longValue());
    }

    @Test
    public void columnsCanBeNamedByLetterAndUnknownOnesAreExplained() {
        Map<String, Object> byLetter = TableQuery.run(sales(), map("aggregates", list(map("column", "C", "op", "sum"))));
        assertEquals(29L, ((Number) rowsOf(byLetter).get(0).get(0)).longValue());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, new org.junit.Assert.ThrowingRunnable() {
            public void run() {
                TableQuery.run(sales(), map("groupBy", list("لا يوجد")));
            }
        });
        assertTrue(e.getMessage(), e.getMessage().contains("الأعمدة المتاحة"));
    }

    @Test
    public void rendererPicksDenseForTablesAndSparseForForms() {
        Map<String, Object> dense = SheetRenderer.render(sales(), 1, 0, 0, null);
        assertEquals("DENSE", dense.get("mode"));
        assertEquals("2| حليب كامل الدسم | أحمد | 10 كرتون | 25.5", ((List<?>) dense.get("lines")).get(1));

        SheetData form = new SheetData("نموذج");
        form.put(5, 2, "9 حبه");
        form.put(5, 3, "17 حبه");
        form.put(9, 30, "سيتي مارت");
        form.addMerge(A1.parseRange("A1:C1"));
        form.put(1, 0, "العنوان");
        Map<String, Object> sparse = SheetRenderer.render(form, 1, 0, 0, null);
        assertEquals("SPARSE", sparse.get("mode"));
        List<?> lines = (List<?>) sparse.get("lines");
        assertEquals("A1=العنوان", lines.get(0));
        assertEquals("C5=9 حبه | D5=17 حبه", lines.get(1));
        assertEquals("AE9=سيتي مارت", lines.get(2));
        assertNotNull(sparse.get("mergedRanges"));
    }

    @Test
    public void rendererPagesAndSearches() {
        Map<String, Object> page = SheetRenderer.render(sales(), 1, 2, 0, null);
        assertEquals(2, ((List<?>) page.get("lines")).size());
        assertEquals(3, ((Number) page.get("nextRow")).intValue());

        SheetData form = new SheetData("نموذج");
        form.put(5, 30, "سيتي مارت الدائري");
        form.put(7, 30, "توفير عصر");
        Map<String, Object> found = SheetRenderer.render(form, 1, 0, 0, "سيتي  مارت");
        List<?> matches = (List<?>) found.get("matches");
        assertEquals(1, matches.size());
        assertEquals("AE5", ((Map<?, ?>) matches.get(0)).get("cell"));
        assertFalse(found.containsKey("lines"));
    }
}
