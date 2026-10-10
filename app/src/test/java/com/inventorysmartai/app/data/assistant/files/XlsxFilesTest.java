package com.inventorysmartai.app.data.assistant.files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class XlsxFilesTest {

    private static final String NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";

    private static byte[] zip(Map<String, String> parts) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(bos);
        for (Map.Entry<String, String> e : parts.entrySet()) {
            zip.putNextEntry(new ZipEntry(e.getKey()));
            zip.write(e.getValue().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        zip.close();
        return bos.toByteArray();
    }

    private static Map<String, byte[]> unzip(byte[] data) throws Exception {
        Map<String, byte[]> out = new LinkedHashMap<String, byte[]>();
        ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data));
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = zip.read(buffer)) != -1) {
                bos.write(buffer, 0, n);
            }
            out.put(entry.getName(), bos.toByteArray());
        }
        return out;
    }

    private static String text(Map<String, byte[]> parts, String name) {
        return new String(parts.get(name), StandardCharsets.UTF_8);
    }

    /** A hand-built form: shared strings, a merged title (B1:C1), styled input cells, and a formula the filler must not touch. */
    private static byte[] template() throws Exception {
        Map<String, String> p = new LinkedHashMap<String, String>();
        p.put("[Content_Types].xml", "<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                + "<Override PartName=\"/xl/sharedStrings.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml\"/>"
                + "</Types>");
        p.put("_rels/.rels", "<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
        p.put("xl/workbook.xml", "<?xml version=\"1.0\"?><workbook xmlns=\"" + NS + "\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<sheets><sheet name=\"نموذج\" sheetId=\"1\" r:id=\"rId1\"/></sheets><calcPr calcId=\"191029\"/></workbook>");
        p.put("xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings\" Target=\"sharedStrings.xml\"/></Relationships>");
        p.put("xl/sharedStrings.xml", "<?xml version=\"1.0\"?><sst xmlns=\"" + NS + "\" count=\"2\" uniqueCount=\"2\"><si><t>العنوان</t></si><si><t>المجموع</t></si></sst>");
        p.put("xl/worksheets/sheet1.xml", "<?xml version=\"1.0\"?><worksheet xmlns=\"" + NS + "\"><dimension ref=\"A1:C3\"/><sheetData>"
                + "<row r=\"1\" spans=\"1:3\"><c r=\"A1\" s=\"1\" t=\"s\"><v>0</v></c><c r=\"B1\" s=\"2\"/><c r=\"C1\" s=\"2\"/></row>"
                + "<row r=\"2\"><c r=\"A2\" s=\"3\"/><c r=\"B2\" s=\"4\"><v>10</v></c></row>"
                + "<row r=\"3\"><c r=\"A3\" t=\"s\"><v>1</v></c><c r=\"B3\" s=\"4\"><f>SUM(B2:B2)</f><v>10</v></c></row>"
                + "</sheetData><mergeCells count=\"1\"><mergeCell ref=\"B1:C1\"/></mergeCells></worksheet>");
        return zip(p);
    }

    @Test
    public void writtenWorkbooksReadBackExactly() throws Exception {
        List<List<Object>> rows = new ArrayList<List<Object>>();
        rows.add(new ArrayList<Object>(Arrays.<Object>asList("حليب", 120, 15.5, "<&>")));
        rows.add(new ArrayList<Object>(Arrays.<Object>asList("عصير", 85L, null, "=SUM(A1)")));
        List<XlsxWriter.SheetSpec> sheets = new ArrayList<XlsxWriter.SheetSpec>();
        sheets.add(new XlsxWriter.SheetSpec("تقرير: المبيعات/1", Arrays.asList("الصنف", "الكمية", "السعر", "ملاحظة"), rows));
        sheets.add(new XlsxWriter.SheetSpec("تقرير: المبيعات/1", Arrays.asList("x"), null));
        byte[] bytes = XlsxWriter.write(sheets, true);

        WorkbookData book = WorkbookData.fromXlsx(bytes, TestSax.RUNNER);
        assertEquals(2, book.sheets().size());
        assertEquals("تقرير- المبيعات-1", book.sheets().get(0).name);
        assertEquals("تقرير- المبيعات-1 (2)", book.sheets().get(1).name);
        SheetData first = book.sheets().get(0);
        assertEquals("الصنف", first.get(1, 0));
        assertEquals("120", first.get(2, 1));
        assertEquals("15.5", first.get(2, 2));
        assertEquals("<&>", first.get(2, 3));
        assertEquals("=SUM(A1)", first.get(3, 3)); // stays text: never evaluated
        assertEquals("", first.get(3, 2));
        assertTrue(text(unzip(bytes), "xl/worksheets/sheet1.xml").contains("rightToLeft=\"1\""));
    }

    @Test
    public void fillingKeepsEverythingElseAndProtectsFormulas() throws Exception {
        byte[] original = template();
        List<XlsxTemplateFiller.Edit> edits = new ArrayList<XlsxTemplateFiller.Edit>();
        edits.add(new XlsxTemplateFiller.Edit(null, "A2", "علي & <ابن>"));      // styled empty cell
        edits.add(new XlsxTemplateFiller.Edit("نموذج", "B2", Double.valueOf(25))); // number replaces number
        edits.add(new XlsxTemplateFiller.Edit(null, "C1", "داخل الدمج"));        // inside merged B1:C1 -> B1
        edits.add(new XlsxTemplateFiller.Edit(null, "B3", Double.valueOf(1)));   // formula: refused
        edits.add(new XlsxTemplateFiller.Edit(null, "A5", "صف جديد"));           // a row that does not exist yet
        edits.add(new XlsxTemplateFiller.Edit(null, "D4", Boolean.TRUE));        // new row 4, new column D
        edits.add(new XlsxTemplateFiller.Edit("غير موجودة", "A1", "x"));
        edits.add(new XlsxTemplateFiller.Edit(null, "عنوان خاطئ", "x"));
        XlsxTemplateFiller.Report report = XlsxTemplateFiller.fill(original, edits);

        assertEquals(5, report.applied);
        assertEquals(1, report.redirected.size());
        assertTrue(report.redirected.get(0), report.redirected.get(0).contains("B1"));
        assertEquals(3, report.skipped.size());
        String skipped = report.skipped.toString();
        assertTrue(skipped, skipped.contains("صيغة"));
        assertTrue(skipped, skipped.contains("غير موجودة"));

        Map<String, byte[]> after = unzip(report.bytes);
        Map<String, byte[]> before = unzip(original);
        // every part except the sheet and the workbook (recalculation flag) is untouched
        for (String name : before.keySet()) {
            if (name.equals("xl/worksheets/sheet1.xml") || name.equals("xl/workbook.xml")) {
                continue;
            }
            assertTrue(name, Arrays.equals(before.get(name), after.get(name)));
        }
        assertTrue(text(after, "xl/workbook.xml").contains("fullCalcOnLoad=\"1\""));

        String sheet = text(after, "xl/worksheets/sheet1.xml");
        assertTrue(sheet, sheet.contains("<c r=\"B3\" s=\"4\"><f>SUM(B2:B2)</f><v>10</v></c>")); // formula intact
        assertTrue(sheet, sheet.contains("<c r=\"A2\" s=\"3\" t=\"inlineStr\">"));               // style kept
        assertTrue(sheet, sheet.contains("علي &amp; &lt;ابن&gt;"));                               // escaped
        assertTrue(sheet, sheet.contains("<c r=\"B2\" s=\"4\"><v>25</v></c>"));
        assertTrue(sheet, sheet.contains("<dimension ref=\"A1:D5\"/>"));
        assertFalse(sheet, sheet.contains("spans=\"1:3\""));                                      // stale hint dropped from the edited row
        assertTrue(sheet, sheet.indexOf("<row r=\"3\"") < sheet.indexOf("<row r=\"4\""));
        assertTrue(sheet, sheet.indexOf("<row r=\"4\"") < sheet.indexOf("<row r=\"5\""));
        assertTrue(sheet, sheet.contains("<mergeCells count=\"1\"><mergeCell ref=\"B1:C1\"/></mergeCells>"));

        WorkbookData book = WorkbookData.fromXlsx(report.bytes, TestSax.RUNNER);
        SheetData filled = book.sheets().get(0);
        assertEquals("العنوان", filled.get(1, 0));          // shared string untouched
        assertEquals("داخل الدمج", filled.get(1, 1));        // landed in B1
        assertEquals("", filled.get(1, 2));
        assertEquals("علي & <ابن>", filled.get(2, 0));
        assertEquals("25", filled.get(2, 1));
        assertEquals("10", filled.get(3, 1));                // formula's cached value
        assertEquals("TRUE", filled.get(4, 3));
        assertEquals("صف جديد", filled.get(5, 0));
        assertEquals(1, filled.merges().size());
    }

    @Test
    public void clearingACellEmptiesItButKeepsItsStyle() throws Exception {
        List<XlsxTemplateFiller.Edit> edits = new ArrayList<XlsxTemplateFiller.Edit>();
        edits.add(new XlsxTemplateFiller.Edit(null, "B2", null));
        String sheet = text(unzip(XlsxTemplateFiller.fill(template(), edits).bytes), "xl/worksheets/sheet1.xml");
        assertTrue(sheet, sheet.contains("<c r=\"B2\" s=\"4\"/>"));
    }

    @Test
    public void notAnXlsxIsRejectedClearly() throws Exception {
        try {
            XlsxTemplateFiller.fill(zip(new LinkedHashMap<String, String>() {{
                put("hello.txt", "hi");
            }}), new ArrayList<XlsxTemplateFiller.Edit>());
            assertTrue("should have failed", false);
        } catch (java.io.IOException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    // ------------------------------------------------------------------ the real promoters' inventory form

    private static byte[] realForm() throws Exception {
        InputStream in = XlsxFilesTest.class.getResourceAsStream("/fixtures/promoter_inventory_form.xlsx");
        assertNotNull("fixture missing", in);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) {
            bos.write(buffer, 0, n);
        }
        in.close();
        return bos.toByteArray();
    }

    @Test
    public void theRealFormIsReadWithTrueAddresses() throws Exception {
        WorkbookData book = WorkbookData.fromXlsx(realForm(), TestSax.RUNNER);
        SheetData sheet = book.sheet(null);
        assertEquals("Table 1", sheet.name);
        assertEquals("2026 \\ 07 \\ 30", CellValues.clean(sheet.get(1, 0)));
        assertEquals("9 حبه", sheet.get(5, 2));            // C5
        assertEquals("17 حبه", sheet.get(5, 3));           // D5
        assertEquals("سيتي مارت الدائري", CellValues.clean(sheet.get(5, 30))); // AE5
        assertEquals(102, sheet.merges().size());
        assertEquals(9.0, CellValues.parseNumber(sheet.get(5, 2)), 0.0);
        assertEquals("حبه", CellValues.unitOf(sheet.get(5, 2)));
        Map<String, Object> view = SheetRenderer.render(sheet, 1, 0, 0, null);
        assertEquals("SPARSE", view.get("mode"));
        Map<String, Object> hit = SheetRenderer.render(sheet, 1, 0, 0, "توفير عصر");
        assertFalse(((List<?>) hit.get("matches")).isEmpty());
    }

    @Test
    public void theRealFormIsFilledWithoutDamage() throws Exception {
        byte[] original = realForm();
        List<XlsxTemplateFiller.Edit> edits = new ArrayList<XlsxTemplateFiller.Edit>();
        edits.add(new XlsxTemplateFiller.Edit(null, "C6", "12 كرتون"));
        edits.add(new XlsxTemplateFiller.Edit(null, "AE7", "سوبر ماركت تجريبي"));
        edits.add(new XlsxTemplateFiller.Edit(null, "C1", "داخل نطاق مدمج"));
        XlsxTemplateFiller.Report report = XlsxTemplateFiller.fill(original, edits);
        assertEquals(3, report.applied);
        assertEquals(1, report.redirected.size());

        Map<String, byte[]> before = unzip(original);
        Map<String, byte[]> after = unzip(report.bytes);
        assertEquals(before.keySet(), after.keySet());
        for (String name : before.keySet()) {
            if (!name.equals("xl/worksheets/sheet1.xml") && !name.equals("xl/workbook.xml")) {
                assertTrue(name, Arrays.equals(before.get(name), after.get(name)));
            }
        }
        WorkbookData reread = WorkbookData.fromXlsx(report.bytes, TestSax.RUNNER);
        SheetData sheet = reread.sheet(null);
        assertEquals("12 كرتون", sheet.get(6, 2));
        assertEquals("سوبر ماركت تجريبي", sheet.get(7, 30));
        assertEquals("داخل نطاق مدمج", sheet.get(1, 1));
        assertEquals("9 حبه", sheet.get(5, 2));            // an untouched neighbour is unchanged
        assertEquals(102, sheet.merges().size());
    }
}
