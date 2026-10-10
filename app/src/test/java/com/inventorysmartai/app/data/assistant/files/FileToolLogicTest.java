package com.inventorysmartai.app.data.assistant.files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Attachment detection, file names, and the argument-handling logic of every file tool. */
public class FileToolLogicTest {

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

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }

    private static byte[] zipWith(String entryName) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(bos);
        zip.putNextEntry(new ZipEntry(entryName));
        zip.write("<x/>".getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
        zip.close();
        return bos.toByteArray();
    }

    private static WorkbookData salesBook() {
        List<List<String>> rows = new ArrayList<List<String>>();
        rows.add(new ArrayList<String>(Arrays.asList("المنتج", "المروج", "الكمية")));
        rows.add(new ArrayList<String>(Arrays.asList("حليب", "أحمد", "10 كرتون")));
        rows.add(new ArrayList<String>(Arrays.asList("حليب", "سعيد", "5 كرتون")));
        rows.add(new ArrayList<String>(Arrays.asList("عصير", "أحمد", "7")));
        rows.add(new ArrayList<String>(Arrays.asList("طحينية", "أحمد", "4")));
        return new WorkbookData(Arrays.asList(SheetData.fromRows("المبيعات", rows)));
    }

    // ------------------------------------------------------------------ FileKinds

    @Test
    public void filesAreRecognisedByTheirBytesNotTheirNames() throws Exception {
        assertEquals(FileKinds.Kind.PDF, FileKinds.detect("photo.jpg", "image/jpeg", "%PDF-1.7 ...".getBytes(StandardCharsets.US_ASCII)).kind);
        assertEquals(FileKinds.Kind.IMAGE, FileKinds.detect("x", null, bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0)).kind);
        assertEquals("image/png", FileKinds.detect("x", null, bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A)).mimeType);
        assertEquals("image/webp", FileKinds.detect("x", null, "RIFF....WEBPVP8 ".getBytes(StandardCharsets.US_ASCII)).mimeType);
        assertEquals("image/heic", FileKinds.detect("x", null, bytes(0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c')).mimeType);
        assertEquals(FileKinds.Kind.SPREADSHEET, FileKinds.detect("a.bin", null, zipWith("xl/workbook.xml")).kind);
        assertEquals(FileKinds.Kind.TEXT_DOCUMENT, FileKinds.detect("a.bin", null, zipWith("word/document.xml")).kind);
    }

    @Test
    public void textFilesAreRecognisedByNameWhenThereAreNoMagicBytes() {
        byte[] text = "a,b\n1,2".getBytes(StandardCharsets.UTF_8);
        assertEquals(FileKinds.Kind.SPREADSHEET, FileKinds.detect("sales.CSV", null, text).kind);
        assertEquals(FileKinds.Kind.SPREADSHEET, FileKinds.detect("export", "text/csv", text).kind);
        assertEquals(FileKinds.Kind.TEXT_DOCUMENT, FileKinds.detect("notes.txt", null, text).kind);
    }

    @Test
    public void unusableFilesAreRefusedWithAHint() throws Exception {
        final byte[] ole = bytes(0xD0, 0xCF, 0x11, 0xE0, 1, 2, 3, 4);
        assertTrue(assertThrows(IllegalArgumentException.class, new org.junit.Assert.ThrowingRunnable() {
            public void run() {
                FileKinds.detect("old.xls", null, ole);
            }
        }).getMessage().contains(".xlsx"));
        final byte[] pptx = zipWith("ppt/presentation.xml");
        assertThrows(IllegalArgumentException.class, new org.junit.Assert.ThrowingRunnable() {
            public void run() throws Throwable {
                FileKinds.detect("deck.pptx", null, pptx);
            }
        });
        assertThrows(IllegalArgumentException.class, new org.junit.Assert.ThrowingRunnable() {
            public void run() {
                FileKinds.detect("data.bin", null, bytes(1, 2, 3, 4));
            }
        });
        assertThrows(IllegalArgumentException.class, new org.junit.Assert.ThrowingRunnable() {
            public void run() {
                FileKinds.detect("empty.txt", null, new byte[0]);
            }
        });
        assertThrows(IllegalArgumentException.class, new org.junit.Assert.ThrowingRunnable() {
            public void run() {
                FileKinds.detect("anim.gif", null, "GIF89a....".getBytes(StandardCharsets.US_ASCII));
            }
        });
    }

    // ------------------------------------------------------------------ FileNames

    @Test
    public void fileNamesAreSafeAndKeepArabic() {
        assertEquals("تقرير- المبيعات-1.xlsx", FileNames.safe("تقرير: المبيعات/1", "ملف", "xlsx"));
        assertEquals("a.xlsx", FileNames.safe("a.XLSX", "ملف", "xlsx"));
        assertEquals("ملف.html", FileNames.safe("   ", "ملف", "html"));
        assertEquals("ملف.xlsx", FileNames.safe("..", "ملف", "xlsx"));
        assertEquals("جرد المروجين.xlsx", FileNames.safe("جرد المروجين.xlsx", "ملف", ""));
        assertEquals("ab", FileNames.safe("a\u0000b", "ملف", ""));
        assertTrue(FileNames.safe(new String(new char[200]).replace('\0', 'x'), "ملف", "xlsx").length() <= 65);
    }

    // ------------------------------------------------------------------ reading

    @Test
    public void summariesAndDescriptions() {
        WorkbookData book = salesBook();
        assertEquals("ورقة واحدة · 5 صف", FileToolLogic.workbookSummary(book));
        List<?> sheets = (List<?>) FileToolLogic.describeWorkbook(book).get("sheets");
        assertEquals("المبيعات", ((Map<?, ?>) sheets.get(0)).get("name"));
        assertEquals("C", ((Map<?, ?>) sheets.get(0)).get("lastColumn"));
        assertEquals("2 أوراق · 10 صف".replace("2 أوراق", "2 أوراق"), FileToolLogic.workbookSummary(new WorkbookData(Arrays.asList(book.sheets().get(0), book.sheets().get(0)))));
    }

    @Test
    public void readingAnUnknownSheetListsTheRealOnes() {
        final WorkbookData book = salesBook();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, new org.junit.Assert.ThrowingRunnable() {
            public void run() {
                FileToolLogic.readWorkbook(book, map("sheet", "لا توجد"));
            }
        });
        assertTrue(e.getMessage(), e.getMessage().contains("المبيعات"));
        Map<String, Object> ok = FileToolLogic.readWorkbook(book, map("sheet", "مبيعات"));
        assertEquals("المبيعات", ok.get("sheet"));
        assertEquals("DENSE", ok.get("mode"));
    }

    @Test
    public void longTextIsPagedByCharacters() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1500; i++) {
            sb.append("كلمة ");
        }
        String text = sb.toString();
        Map<String, Object> first = FileToolLogic.readText(text, null, map("maxChars", 1000));
        assertEquals(1000, ((String) first.get("text")).length());
        assertEquals(1000, ((Number) first.get("nextChar")).intValue());
        Map<String, Object> last = FileToolLogic.readText(text, null, map("fromChar", 7000, "maxChars", 1000));
        assertFalse(last.containsKey("nextChar"));
        assertEquals(text.length(), ((Number) last.get("totalChars")).intValue());
        Map<String, Object> withTables = FileToolLogic.readText("x", salesBook(), map());
        assertTrue(withTables.containsKey("tables"));
    }

    // ------------------------------------------------------------------ calculating

    @Test
    public void calculateAnswersOneOrManyExpressions() {
        assertEquals(30L, ((Number) FileToolLogic.calculate(map("expression", "200*15%")).get("value")).longValue());
        Map<String, Object> many = FileToolLogic.calculate(map("expressions", list("1+1", "1/0", "sum(2,3)")));
        List<?> results = (List<?>) many.get("results");
        assertEquals(2L, ((Number) ((Map<?, ?>) results.get(0)).get("value")).longValue());
        assertTrue(((Map<?, ?>) results.get(1)).containsKey("error"));
        assertEquals(5L, ((Number) ((Map<?, ?>) results.get(2)).get("value")).longValue());
        assertThrows(IllegalArgumentException.class, new org.junit.Assert.ThrowingRunnable() {
            public void run() {
                FileToolLogic.calculate(map());
            }
        });
    }

    @Test
    public void queryGoesThroughTheNamedSheet() {
        Map<String, Object> result = FileToolLogic.queryTable(salesBook(), map(
                "groupBy", list("المنتج"), "aggregates", list(map("column", "الكمية", "op", "sum"))));
        assertEquals(3, ((Number) result.get("resultRows")).intValue());
    }

    // ------------------------------------------------------------------ writing

    @Test
    public void editsKeepTheirTypesAndSheetDefaults() {
        List<XlsxTemplateFiller.Edit> edits = FileToolLogic.parseEdits(map(
                "sheet", "نموذج",
                "cells", list(
                        map("cell", "C5", "value", "9 حبه"),
                        map("cell", "D5", "value", 12),
                        map("cell", "E5", "value", null),
                        map("cell", "F5", "value", true, "sheet", "أخرى"),
                        map("cell", "G5", "value", list(1, 2)))));
        assertEquals(5, edits.size());
        assertEquals("نموذج", edits.get(0).sheet);
        assertEquals("9 حبه", edits.get(0).value);
        assertEquals(12, ((Number) edits.get(1).value).intValue());
        assertNull(edits.get(2).value);
        assertEquals("أخرى", edits.get(3).sheet);
        assertEquals("[1, 2]", edits.get(4).value);
        assertNull(FileToolLogic.parseEdits(map("cells", list(map("cell", "A1", "value", "x")))).get(0).sheet);
    }

    @Test
    public void badEditRequestsAreExplained() {
        assertThrows(IllegalArgumentException.class, new org.junit.Assert.ThrowingRunnable() {
            public void run() {
                FileToolLogic.parseEdits(map());
            }
        });
        assertThrows(IllegalArgumentException.class, new org.junit.Assert.ThrowingRunnable() {
            public void run() {
                FileToolLogic.parseEdits(map("cells", list(map("value", "x"))));
            }
        });
        final List<Object> many = new ArrayList<Object>();
        for (int i = 0; i < 401; i++) {
            many.add(map("cell", "A" + (i + 1), "value", i));
        }
        assertThrows(IllegalArgumentException.class, new org.junit.Assert.ThrowingRunnable() {
            public void run() {
                FileToolLogic.parseEdits(map("cells", many));
            }
        });
    }

    @Test
    public void sheetsCanBeGivenInFullOrAsAShorthand() {
        List<XlsxWriter.SheetSpec> shorthand = FileToolLogic.parseSheets(map(
                "title", "المبيعات", "headers", list("الصنف", "الكمية"), "rows", list(list("حليب", 5), list("عصير", map("a", 1)))));
        assertEquals(1, shorthand.size());
        assertEquals("المبيعات", shorthand.get(0).name);
        assertEquals("{a=1}", shorthand.get(0).rows.get(1).get(1));

        List<XlsxWriter.SheetSpec> full = FileToolLogic.parseSheets(map(
                "sheets", list(map("name", "أ", "headers", list("x"), "rows", list(list(1))), map("name", "فارغة"), map("name", "ب", "rows", list(list(2))))));
        assertEquals(2, full.size()); // the empty sheet is dropped
        assertThrows(IllegalArgumentException.class, new org.junit.Assert.ThrowingRunnable() {
            public void run() {
                FileToolLogic.parseSheets(map("headers", list(), "rows", list()));
            }
        });
    }

    @Test
    public void numericLookingTextBecomesNumbersButCodesStayText() {
        List<XlsxWriter.SheetSpec> sheets = FileToolLogic.parseSheets(map(
                "headers", list("الصنف", "الكمية", "الكود", "الباركود"),
                "rows", list(list("حليب", "120", "007", "6281001234567"), list("عصير", "15.5", "A-12", "1,250"))));
        List<Object> first = sheets.get(0).rows.get(0);
        assertEquals(120.0, ((Number) first.get(1)).doubleValue(), 0.0);
        assertEquals("007", first.get(2));
        assertEquals("6281001234567", first.get(3));
        List<Object> second = sheets.get(0).rows.get(1);
        assertEquals(15.5, ((Number) second.get(1)).doubleValue(), 0.0);
        assertEquals("A-12", second.get(2));
        assertEquals(1250.0, ((Number) second.get(3)).doubleValue(), 0.0);
    }

    // ------------------------------------------------------------------ dashboards

    @Test
    public void dashboardNumbersCanBeComputedFromTheFile() {
        final WorkbookData book = salesBook();
        Function<String, WorkbookData> resolver = new Function<String, WorkbookData>() {
            @Override
            public WorkbookData apply(String id) {
                return id.equals("f1") ? book : null;
            }
        };
        Map<String, Object> args = map(
                "title", "لوحة",
                "kpis", list(map("label", "إجمالي الكمية", "unit", "كرتون",
                        "source", map("attachmentId", "f1", "aggregates", list(map("column", "الكمية", "op", "sum"))))),
                "charts", list(
                        map("type", "bar", "title", "حسب المنتج",
                                "source", map("attachmentId", "f1", "groupBy", list("المنتج"), "aggregates", list(map("column", "الكمية", "op", "sum")))),
                        map("type", "line", "labels", list("1", "2"), "values", list(3, 4))),
                "tables", list(map("title", "المروجون",
                        "source", map("attachmentId", "f1", "groupBy", list("المروج"), "aggregates", list(map("column", "الكمية", "op", "sum"), map("op", "count"))))));
        String html = FileToolLogic.dashboardHtml(args, resolver, "now");
        assertTrue(html, html.contains(">26 <span class=\"ku\">كرتون</span>"));   // 10 + 5 + 7 + 4 computed, not typed by the model
        assertTrue(html, html.contains("حليب"));
        assertTrue(html, html.contains("<b>15</b>"));                            // milk total (10 + 5) in the bar chart
        assertTrue(html, html.contains("<th>sum(الكمية)</th>"));
        assertTrue(html, html.contains("<path d=\"M"));                           // the inline-data line chart still renders
    }

    @Test
    public void dashboardSourcesFailLoudlyWhenTheFileIsUnknown() {
        final Map<String, Object> args = map("kpis", list(map("label", "x", "source", map("attachmentId", "zzz", "aggregates", list(map("op", "count"))))));
        assertThrows(IllegalArgumentException.class, new org.junit.Assert.ThrowingRunnable() {
            public void run() {
                FileToolLogic.dashboardHtml(args, new Function<String, WorkbookData>() {
                    @Override
                    public WorkbookData apply(String id) {
                        return null;
                    }
                }, "");
            }
        });
    }

    @Test
    public void referencedAttachmentsAreFoundAnywhere() {
        Map<String, Object> args = map(
                "kpis", list(map("source", map("attachmentId", "f2"))),
                "charts", list(map("source", map("attachmentId", "f3")), map("source", map("attachmentId", "f2"))),
                "attachmentId", "f1");
        Set<String> ids = FileToolLogic.referencedAttachmentIds(args);
        assertEquals(3, ids.size());
        assertTrue(ids.contains("f1") && ids.contains("f2") && ids.contains("f3"));
    }
}
