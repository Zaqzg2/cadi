package com.inventorysmartai.app.data.assistant.files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DashboardBuilderTest {

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

    @Test
    public void everyTextIsEscapedAndNoScriptCanRun() {
        Map<String, Object> spec = map(
                "title", "<script>alert(1)</script>",
                "kpis", list(map("label", "<img src=x onerror=alert(1)>", "value", "\"><svg onload=alert(1)>")),
                "charts", list(map("type", "bar", "labels", list("<b>x</b>"), "values", list(5))),
                "tables", list(map("headers", list("<th>"), "rows", list(list("<td>", 3)))),
                "notes", list("<script>steal()</script>"));
        String html = DashboardBuilder.build(spec, "<now>");
        assertFalse(html, html.contains("<script"));
        assertFalse(html, html.contains("<img"));
        assertFalse(html, html.contains("<svg onload"));
        assertTrue(html, html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
        assertTrue(html, html.contains("Content-Security-Policy"));
        assertTrue(html, html.contains("default-src 'none'"));
    }

    @Test
    public void kpisChartsAndTablesAreRendered() {
        Map<String, Object> spec = map(
                "title", "لوحة الجرد",
                "kpis", list(map("label", "الكراتين", "value", 1234.5, "unit", "كرتون", "tone", "good")),
                "charts", list(
                        map("type", "bar", "title", "حسب الصنف", "labels", list("أ", "ب"), "values", list(10, 5)),
                        map("type", "donut", "labels", list("س", "ص"), "values", list(3, 1)),
                        map("type", "line", "labels", list("1", "2", "3"), "series", list(map("name", "أ", "values", list(1, 3, 2)), map("name", "ب", "values", list(2, 2, 2)))),
                        map("type", "column", "labels", list("Q1", "Q2"), "values", list(4, 6))),
                "tables", list(map("title", "التفاصيل", "headers", list("الصنف", "الكمية"), "rows", list(list("حليب", 12)))));
        String html = DashboardBuilder.build(spec, "2026-10-10");
        assertTrue(html.startsWith("<!DOCTYPE html>"));
        assertTrue(html, html.contains("dir=\"rtl\""));
        assertTrue(html, html.contains("1,234.5"));
        assertTrue(html, html.contains("class=\"kpi good\""));
        assertTrue(html, html.contains("class=\"hbars\""));
        assertTrue(html, html.contains("<svg viewBox=\"0 0 42 42\""));
        assertTrue(html, html.contains("<path d=\"M"));
        assertTrue(html, html.contains("<rect "));
        assertTrue(html, html.contains("<table>"));
        assertTrue(html, html.contains("75%"));   // 3 of 4
    }

    @Test
    public void kpiNumbersGivenAsTextAreFormattedToo() {
        String html = DashboardBuilder.build(map("kpis", list(map("label", "الإجمالي", "value", "1234.5"), map("label", "الكود", "value", "007"))), "");
        assertTrue(html, html.contains(">1,234.5<"));
        assertTrue(html, html.contains(">007<"));
    }

    @Test
    public void emptyAndBrokenInputStillGivesAValidPage() {
        String html = DashboardBuilder.build(new LinkedHashMap<String, Object>(), null);
        assertTrue(html.contains("لوحة البيانات"));
        String odd = DashboardBuilder.build(map("charts", list(map("type", "pie", "labels", list("a"), "values", list(0)), map("type", "bar")), "kpis", list(map())), "");
        assertTrue(odd.contains("</html>"));
        assertTrue(odd.contains("لا توجد قيم موجبة"));
    }

    @Test
    public void numbersAreFormattedForReading() {
        assertEquals("1,234,567.89", DashboardBuilder.format(1234567.891));
        assertEquals("-5", DashboardBuilder.format(-5));
        assertEquals("0", DashboardBuilder.format(0));
        assertEquals("12.5", DashboardBuilder.format(12.5));
        assertEquals("2,500", DashboardBuilder.compact(2500));
        assertEquals("12.5K", DashboardBuilder.compact(12500));
        assertEquals("3M", DashboardBuilder.compact(3000000));
        assertEquals(500.0, DashboardBuilder.niceStep(450), 0.0);
        assertEquals(1000.0, DashboardBuilder.niceStep(575), 0.0);
        assertEquals(1.0, DashboardBuilder.niceStep(0), 0.0);
        assertEquals(20.0, DashboardBuilder.niceStep(11), 0.0);
    }
}
