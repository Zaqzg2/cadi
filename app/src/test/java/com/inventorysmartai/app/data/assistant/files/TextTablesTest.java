package com.inventorysmartai.app.data.assistant.files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class TextTablesTest {

    @Test
    public void csvHandlesQuotesCommasAndLineBreaksInsideCells() {
        String csv = "الاسم,الملاحظة\n\"أحمد, علي\",\"قال \"\"مرحبا\"\"\"\n\"سطر1\nسطر2\",x\n";
        List<List<String>> rows = TextTables.parseCsv(csv, ',');
        assertEquals(3, rows.size());
        assertEquals("أحمد, علي", rows.get(1).get(0));
        assertEquals("قال \"مرحبا\"", rows.get(1).get(1));
        assertEquals("سطر1\nسطر2", rows.get(2).get(0));
    }

    @Test
    public void csvDelimiterIsDetected() {
        assertEquals(';', TextTables.detectDelimiter("a;b;c\n1;2;3"));
        assertEquals('\t', TextTables.detectDelimiter("a\tb\tc\n1\t2\t3"));
        assertEquals(',', TextTables.detectDelimiter("a,b,c"));
        assertEquals(',', TextTables.detectDelimiter("\"a;b\",c"));
        assertEquals(',', TextTables.detectDelimiter("single"));
    }

    @Test
    public void csvDecodesUtf8WithBomAndWindows1256() throws Exception {
        byte[] body = "الصنف,الكمية\nحليب,5".getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[body.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(body, 0, withBom, 3, body.length);
        assertEquals("الصنف,الكمية\nحليب,5", TextTables.decode(withBom));

        byte[] legacy = "الصنف,الكمية\nحليب,5".getBytes("windows-1256");
        assertEquals("الصنف,الكمية\nحليب,5", TextTables.decode(legacy));
    }

    @Test
    public void csvSheetKeepsRowsAndColumns() {
        SheetData sheet = TextTables.csvSheet("ورقة", "a;b\n1;2\n".getBytes(StandardCharsets.UTF_8));
        assertEquals("b", sheet.get(1, 1));
        assertEquals("2", sheet.get(2, 1));
    }

    @Test
    public void markdownTablesFromOcrTextBecomeSheets() {
        String text = "نص قبل الجدول\n\n"
                + "| الصنف | الكمية |\n| --- | ---: |\n| حليب<br>كامل | 10 |\n| عصير \\| فاخر | 7 |\n\n"
                + "فقرة بين الجدولين\n\n"
                + "| a | b |\n|---|---|\n| 1 | 2 |\n";
        List<SheetData> tables = TextTables.markdownTables(text);
        assertEquals(2, tables.size());
        assertEquals("جدول 1", tables.get(0).name);
        assertEquals("الكمية", tables.get(0).get(1, 1));
        assertEquals("حليب كامل", tables.get(0).get(2, 0));
        assertEquals("عصير | فاخر", tables.get(0).get(3, 0));
        assertEquals("2", tables.get(1).get(2, 1));
        assertTrue(TextTables.markdownTables("لا جداول هنا").isEmpty());
    }

    @Test
    public void docxTextKeepsParagraphsAndTableCells() throws Exception {
        String xml = "<?xml version=\"1.0\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>"
                + "<w:p><w:r><w:t>السطر</w:t></w:r><w:r><w:t xml:space=\"preserve\"> الأول &amp; الأهم</w:t></w:r></w:p>"
                + "<w:p><w:r><w:t>الثاني</w:t></w:r></w:p>"
                + "<w:tbl><w:tr><w:tc><w:p><w:r><w:t>خلية1</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>خلية2</w:t></w:r></w:p></w:tc></w:tr></w:tbl>"
                + "</w:body></w:document>";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(bos);
        zip.putNextEntry(new ZipEntry("word/document.xml"));
        zip.write(xml.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
        zip.close();
        String text = TextTables.docxText(bos.toByteArray());
        assertTrue(text, text.startsWith("السطر الأول & الأهم\nالثاني"));
        assertTrue(text, text.contains("خلية1\tخلية2"));
    }

    @Test
    public void aFileThatIsNotDocxIsRejected() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(bos);
        zip.putNextEntry(new ZipEntry("other.txt"));
        zip.write(1);
        zip.closeEntry();
        zip.close();
        final byte[] bytes = bos.toByteArray();
        assertThrows(IOException.class, new org.junit.Assert.ThrowingRunnable() {
            public void run() throws Throwable {
                TextTables.docxText(bytes);
            }
        });
    }
}
