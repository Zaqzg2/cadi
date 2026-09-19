package com.inventorysmartai.app.data.importing.parser

import com.inventorysmartai.app.domain.importing.OpenedFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream

class CsvImportParserTest {

    private val parser = CsvImportParser()

    private fun fileOf(bytes: ByteArray, name: String = "test.csv"): OpenedFile = object : OpenedFile {
        override val displayName = name
        override val sizeBytes = bytes.size.toLong()
        override val mimeType: String? = "text/csv"
        override fun inputStream() = ByteArrayInputStream(bytes)
    }

    private fun fileOf(content: String, name: String = "test.csv") = fileOf(content.toByteArray(Charsets.UTF_8), name)

    @Test
    fun `parses a simple comma-delimited file`() = runBlocking {
        val table = parser.parseSheet(fileOf("اسم الصنف,الباركود,الكمية\nأرز بسمتي,123456,10"), null)
        assertEquals(2, table.rows.size)
        assertEquals(listOf("اسم الصنف", "الباركود", "الكمية"), table.rows[0])
        assertEquals(listOf("أرز بسمتي", "123456", "10"), table.rows[1])
    }

    @Test
    fun `auto-detects a semicolon delimiter`() = runBlocking {
        val table = parser.parseSheet(fileOf("اسم الصنف;الباركود;الكمية\nأرز بسمتي;123456;10"), null)
        assertEquals(listOf("اسم الصنف", "الباركود", "الكمية"), table.rows[0])
    }

    @Test
    fun `strips a UTF-8 byte order mark before parsing`() = runBlocking {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val content = "اسم,الكمية\nأرز,5".toByteArray(Charsets.UTF_8)
        val table = parser.parseSheet(fileOf(bom + content), null)
        assertEquals("اسم", table.rows[0][0])
    }

    @Test
    fun `quoted values may contain the delimiter itself`() = runBlocking {
        val table = parser.parseSheet(fileOf("اسم,ملاحظات\nأرز,\"جيد, ومناسب للتصدير\""), null)
        assertEquals("جيد, ومناسب للتصدير", table.rows[1][1])
    }

    @Test
    fun `doubled quotes inside a quoted value are unescaped to one quote`() = runBlocking {
        val table = parser.parseSheet(fileOf("\"صنف \"\"ممتاز\"\"\""), null)
        assertEquals("صنف \"ممتاز\"", table.rows[0][0])
    }

    @Test
    fun `empty fields are preserved as empty strings, not skipped`() = runBlocking {
        val table = parser.parseSheet(fileOf("a,b,c\n1,,3"), null)
        assertEquals(listOf("1", "", "3"), table.rows[1])
    }

    @Test
    fun `a trailing newline does not produce a spurious empty row`() = runBlocking {
        val table = parser.parseSheet(fileOf("a,b\n1,2\n"), null)
        assertEquals(2, table.rows.size)
    }

    @Test
    fun `CRLF line endings are handled the same as LF`() = runBlocking {
        val table = parser.parseSheet(fileOf("a,b\r\n1,2\r\n"), null)
        assertEquals(2, table.rows.size)
        assertEquals(listOf("1", "2"), table.rows[1])
    }

    @Test
    fun `a completely empty file parses to zero rows`() = runBlocking {
        val table = parser.parseSheet(fileOf(""), null)
        assertEquals(0, table.rows.size)
    }

    @Test
    fun `csv has exactly one implicit sheet with no name`() = runBlocking {
        val sheets = parser.listSheets(fileOf("a,b\n1,2"))
        assertEquals(1, sheets.size)
        assertNull(sheets.first())
    }

    @Test
    fun `supports recognizes a csv extension`() {
        assertEquals(true, parser.supports(fileOf("a,b", "products.csv")))
    }

    @Test
    fun `supports rejects an unrelated extension with no csv mime type`() {
        val file = object : OpenedFile {
            override val displayName = "image.png"
            override val sizeBytes = 10L
            override val mimeType: String? = "image/png"
            override fun inputStream() = ByteArrayInputStream(ByteArray(0))
        }
        assertEquals(false, parser.supports(file))
    }
}
