package com.inventorysmartai.app.data.importing.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.xml.parsers.SAXParserFactory

/**
 * [XlsxSaxReader] is what [ExcelImportParser] falls back to on a device when fastexcel-reader's
 * StAX setup fails. The fixtures are tiny hand-written OOXML packages (so every edge case is
 * exact and reviewable). The SAX driver here is plain JAXP because `android.util.Xml`, which the
 * app uses on a device, does not exist on the JVM.
 */
class XlsxSaxReaderTest {

    private val reader = XlsxSaxReader(object : XlsxSaxReader.SaxRunner {
        override fun parse(input: InputStream, handler: DefaultHandler) {
            val factory = SAXParserFactory.newInstance()
            factory.isNamespaceAware = true
            factory.newSAXParser().parse(input, handler)
        }
    })

    private val relNs = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private val mainNs = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"

    // ---- fixture builders ----------------------------------------------------------------

    private fun zipOf(vararg parts: Pair<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            for ((name, content) in parts) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun relsXml(vararg rels: Triple<String, String, String>): String {
        val body = rels.joinToString("") { (id, type, target) ->
            """<Relationship Id="$id" Type="$type" Target="$target"/>"""
        }
        return """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">$body</Relationships>"""
    }

    private fun workbookXml(vararg sheetNames: String): String {
        val sheets = sheetNames.mapIndexed { i, name ->
            """<sheet name="$name" sheetId="${i + 1}" r:id="rId${i + 1}"/>"""
        }.joinToString("")
        return """<?xml version="1.0"?><workbook xmlns="$mainNs" xmlns:r="$relNs"><sheets>$sheets</sheets></workbook>"""
    }

    private fun sheetXml(rowsXml: String, extra: String = ""): String =
        """<?xml version="1.0"?><worksheet xmlns="$mainNs"><sheetData>$rowsXml</sheetData>$extra</worksheet>"""

    private val rootRels = relsXml(Triple("rId1", "$relNs/officeDocument", "xl/workbook.xml"))

    private fun row(r: Int, vararg cells: String): String =
        """<row r="$r">${cells.joinToString("")}</row>"""

    /** An inline-string cell. */
    private fun text(ref: String, value: String): String =
        """<c r="$ref" t="inlineStr"><is><t>$value</t></is></c>"""

    /** A complete minimal package: one worksheet called [name] holding [rowsXml]. */
    private fun singleSheetPackage(
        name: String,
        rowsXml: String,
        sharedStrings: String? = null,
        extra: String = ""
    ): ByteArray {
        val rels = mutableListOf(Triple("rId1", "$relNs/worksheet", "worksheets/sheet1.xml"))
        if (sharedStrings != null) {
            rels.add(Triple("rId2", "$relNs/sharedStrings", "sharedStrings.xml"))
        }
        val parts = mutableListOf(
            "_rels/.rels" to rootRels,
            "xl/workbook.xml" to workbookXml(name),
            "xl/_rels/workbook.xml.rels" to relsXml(*rels.toTypedArray()),
            "xl/worksheets/sheet1.xml" to sheetXml(rowsXml, extra)
        )
        if (sharedStrings != null) {
            parts.add("xl/sharedStrings.xml" to sharedStrings)
        }
        return zipOf(*parts.toTypedArray())
    }

    private fun assertRejected(block: () -> Unit) {
        try {
            block()
        } catch (e: IllegalStateException) {
            return
        }
        fail("expected an IllegalStateException")
    }

    // ---- tests ---------------------------------------------------------------------------

    @Test
    fun `reads every cell type`() {
        val sst = """<sst xmlns="$mainNs"><si><t>أرز</t></si>""" +
            """<si><r><t>Rich </t></r><r><t>Text</t></r></si>""" +
            """<si><t>base</t><rPh sb="0" eb="1"><t>PHONETIC</t></rPh></si></sst>"""
        val rows = row(
            1,
            """<c r="A1" t="s"><v>0</v></c>""",
            """<c r="B1" t="s"><v>1</v></c>""",
            """<c r="C1" t="s"><v>2</v></c>""",
            text("D1", "inline"),
            """<c r="E1" t="str"><f>A2&amp;"x"</f><v>formula text</v></c>""",
            """<c r="F1" t="b"><v>1</v></c>""",
            """<c r="G1" t="b"><v>0</v></c>""",
            """<c r="H1"><f>1+2</f><v>3</v></c>""",
            """<c r="I1"><f>1+2</f></c>"""
        )
        val table = reader.readSheet(singleSheetPackage("S", rows, sst), null)

        assertEquals("S", table.sheetName)
        assertEquals(
            listOf(listOf("أرز", "Rich Text", "base", "inline", "formula text", "TRUE", "FALSE", "3", "")),
            table.rows
        )
    }

    @Test
    fun `numbers become plain decimals and integers are never rounded`() {
        val rows = row(
            1,
            """<c r="A1"><v>1.5E+3</v></c>""",
            """<c r="B1"><v>19.989999999999998</v></c>""",
            """<c r="C1"><v>0.30000000000000004</v></c>""",
            """<c r="D1"><v>1E-5</v></c>""",
            """<c r="E1"><v>6281234567890</v></c>""",
            """<c r="F1"><v>1234567890123456789</v></c>""",
            """<c r="G1"><v>-0</v></c>""",
            """<c r="H1"><v>abc</v></c>"""
        )
        val table = reader.readSheet(singleSheetPackage("S", rows), null)

        assertEquals(
            listOf(listOf("1500", "19.99", "0.3", "0.00001", "6281234567890", "1234567890123456789", "0", "abc")),
            table.rows
        )
    }

    @Test
    fun `absent rows are not invented but gaps inside a row are filled`() {
        val rows = row(1, text("A1", "h")) +
            """<row r="4"/>""" +
            row(9, text("B9", "x"), """<c r="D9" s="3"/>""")
        val table = reader.readSheet(singleSheetPackage("S", rows), null)

        assertEquals(
            listOf(listOf("h"), emptyList<String>(), listOf("", "x", "", "")),
            table.rows
        )
    }

    @Test
    fun `cells without an r attribute continue after the previous cell`() {
        val rows = """<row><c t="inlineStr"><is><t>a</t></is></c><c><v>2</v></c><c r="E1"><v>5</v></c><c><v>6</v></c></row>"""
        val table = reader.readSheet(singleSheetPackage("S", rows), null)

        assertEquals(listOf(listOf("a", "2", "", "", "5", "6")), table.rows)
    }

    @Test
    fun `zip entries may appear in any order`() {
        val sst = """<sst xmlns="$mainNs"><si><t>late</t></si></sst>"""
        val bytes = zipOf(
            "xl/worksheets/sheet1.xml" to sheetXml(row(1, """<c r="A1" t="s"><v>0</v></c>""")),
            "xl/sharedStrings.xml" to sst,
            "xl/workbook.xml" to workbookXml("S"),
            "xl/_rels/workbook.xml.rels" to relsXml(
                Triple("rId1", "$relNs/worksheet", "worksheets/sheet1.xml"),
                Triple("rId2", "$relNs/sharedStrings", "sharedStrings.xml")
            ),
            "_rels/.rels" to rootRels
        )

        assertEquals(listOf("S"), reader.listSheets(bytes))
        assertEquals(listOf(listOf("late")), reader.readSheet(bytes, "S").rows)
    }

    @Test
    fun `lists sheets in workbook order and reads a named one`() {
        val bytes = zipOf(
            "_rels/.rels" to rootRels,
            "xl/workbook.xml" to workbookXml("جرد يناير", "جرد فبراير"),
            "xl/_rels/workbook.xml.rels" to relsXml(
                Triple("rId1", "$relNs/worksheet", "worksheets/sheet1.xml"),
                Triple("rId2", "$relNs/worksheet", "/xl/worksheets/sheet2.xml")
            ),
            "xl/worksheets/sheet1.xml" to sheetXml(row(1, text("A1", "one"))),
            "xl/worksheets/sheet2.xml" to sheetXml(row(1, text("A1", "two")))
        )

        assertEquals(listOf("جرد يناير", "جرد فبراير"), reader.listSheets(bytes))
        assertEquals(listOf(listOf("one")), reader.readSheet(bytes, null).rows)

        val second = reader.readSheet(bytes, "جرد فبراير")
        assertEquals("جرد فبراير", second.sheetName)
        assertEquals(listOf(listOf("two")), second.rows)
    }

    @Test
    fun `elements outside sheetData are never mistaken for cells`() {
        val decoy = """<extLst><ext><row xmlns="urn:x"><c r="Z1"><v>99</v></c></row></ext></extLst>"""
        val table = reader.readSheet(
            singleSheetPackage("S", row(1, text("A1", "real")), extra = decoy),
            null
        )

        assertEquals(listOf(listOf("real")), table.rows)
    }

    @Test
    fun `rejects non-xlsx input and unknown sheet names`() {
        assertRejected { reader.listSheets("this is not a zip".toByteArray()) }
        assertRejected { reader.listSheets(zipOf("hello.txt" to "hi")) }
        assertRejected { reader.readSheet(singleSheetPackage("S", row(1, text("A1", "x"))), "nope") }
    }
}
