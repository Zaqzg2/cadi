package com.inventorysmartai.app.data.importing.parser

import com.inventorysmartai.app.domain.importing.OpenedFile
import kotlinx.coroutines.runBlocking
import org.dhatim.fastexcel.Workbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Builds a REAL .xlsx file in memory with the fastexcel writer (test-only dependency — see
 *  libs.versions.toml) so [ExcelImportParser] is tested against genuine OOXML bytes, not a
 *  hand-faked stand-in. */
class ExcelImportParserTest {

    private val parser = ExcelImportParser()

    private fun buildWorkbook(vararg sheets: Pair<String, List<List<String>>>): ByteArray {
        val output = ByteArrayOutputStream()
        val workbook = Workbook(output, "InventorySmartAI Tests", "1.0")
        sheets.forEach { (sheetName, rows) ->
            val worksheet = workbook.newWorksheet(sheetName)
            rows.forEachIndexed { r, row -> row.forEachIndexed { c, value -> worksheet.value(r, c, value) } }
        }
        workbook.finish()
        return output.toByteArray()
    }

    private fun fileOf(bytes: ByteArray, name: String = "test.xlsx"): OpenedFile = object : OpenedFile {
        override val displayName = name
        override val sizeBytes = bytes.size.toLong()
        override val mimeType: String? = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        override fun inputStream() = ByteArrayInputStream(bytes)
    }

    @Test
    fun `reads back a simple single-sheet workbook with Arabic content`() = runBlocking {
        val bytes = buildWorkbook(
            "المنتجات" to listOf(
                listOf("اسم الصنف", "الباركود", "الكمية"),
                listOf("أرز بسمتي", "123456", "10")
            )
        )
        val table = parser.parseSheet(fileOf(bytes), sheetName = null)
        assertEquals("المنتجات", table.sheetName)
        assertEquals(2, table.rows.size)
        assertEquals("اسم الصنف", table.rows[0][0])
        assertEquals("أرز بسمتي", table.rows[1][0])
        assertEquals("123456", table.rows[1][1])
    }

    @Test
    fun `lists every sheet in a multi-sheet workbook`() = runBlocking {
        val bytes = buildWorkbook(
            "جرد يناير" to listOf(listOf("a")),
            "جرد فبراير" to listOf(listOf("b"))
        )
        val sheets = parser.listSheets(fileOf(bytes))
        assertEquals(listOf("جرد يناير", "جرد فبراير"), sheets)
    }

    @Test
    fun `parses a specific named sheet, not just the first one`() = runBlocking {
        val bytes = buildWorkbook(
            "الفرع الاول" to listOf(listOf("فرع1")),
            "الفرع الثاني" to listOf(listOf("فرع2"))
        )
        val table = parser.parseSheet(fileOf(bytes), sheetName = "الفرع الثاني")
        assertEquals("الفرع الثاني", table.sheetName)
        assertEquals("فرع2", table.rows[0][0])
    }

    @Test
    fun `a ragged row shorter than the header is read without crashing`() = runBlocking {
        val bytes = buildWorkbook(
            "Sheet1" to listOf(
                listOf("a", "b", "c"),
                listOf("1") // only one cell -- b and c are simply absent for this row
            )
        )
        val table = parser.parseSheet(fileOf(bytes), null)
        assertEquals(2, table.rows.size)
        assertTrue(table.rows[1].isNotEmpty())
    }

    @Test
    fun `supports recognizes an xlsx extension`() {
        assertEquals(true, parser.supports(fileOf(buildWorkbook("s" to listOf(listOf("a"))), "data.xlsx")))
    }

    @Test
    fun `supports rejects a csv file`() {
        val file = object : OpenedFile {
            override val displayName = "data.csv"
            override val sizeBytes = 10L
            override val mimeType: String? = "text/csv"
            override fun inputStream() = ByteArrayInputStream(ByteArray(0))
        }
        assertEquals(false, parser.supports(file))
    }
}
