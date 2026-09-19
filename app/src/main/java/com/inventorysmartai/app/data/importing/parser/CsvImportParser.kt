package com.inventorysmartai.app.data.importing.parser

import com.inventorysmartai.app.domain.importing.ImportParser
import com.inventorysmartai.app.domain.importing.OpenedFile
import com.inventorysmartai.app.domain.importing.RawRow
import com.inventorysmartai.app.domain.importing.RawTable
import com.inventorysmartai.app.domain.model.ImportSourceType
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Step 3 (Parser) for CSV, spec section 5. Hand-rolled rather than a library (opencsv, etc.) —
 * this project has zero third-party CSV/JSON dependencies so far (see SimpleJson's doc comment
 * for the same reasoning), and a correct CSV tokenizer is genuinely simple to write directly:
 * one pass, one small state machine, no dependency-version risk at all.
 *
 * Handles: UTF-8 (including a leading BOM), Arabic text (no special-casing needed — this is a
 * byte/character tokenizer, agnostic to what alphabet the text uses), comma AND semicolon
 * delimiters (auto-detected from the first line), quoted values (including embedded delimiters,
 * embedded newlines, and doubled `""` escaped quotes), and empty fields.
 */
@Singleton
class CsvImportParser @Inject constructor() : ImportParser {

    override val sourceType: ImportSourceType = ImportSourceType.CSV

    override fun supports(file: OpenedFile): Boolean {
        val name = file.displayName.lowercase()
        return name.endsWith(".csv") || file.mimeType?.contains("csv") == true
    }

    /** CSV has no sheet concept — always exactly one implicit "sheet", with no name. */
    override suspend fun listSheets(file: OpenedFile): List<String?> = listOf(null)

    override suspend fun parseSheet(file: OpenedFile, sheetName: String?): RawTable {
        val text = readTextStrippingBom(file.inputStream())
        val delimiter = detectDelimiter(text)
        return RawTable(sheetName = null, rows = tokenize(text, delimiter))
    }

    private fun readTextStrippingBom(stream: InputStream): String {
        val bytes = stream.use { it.readBytes() }
        val hasBom = bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        val content = if (hasBom) bytes.copyOfRange(3, bytes.size) else bytes
        return content.toString(Charsets.UTF_8)
    }

    /** Counts unquoted commas vs semicolons on the first physical line only — a real header line
     *  always uses its true delimiter consistently, so one line is enough, and it keeps
     *  detection cheap even for a very large file. Ties (including "neither appears at all", a
     *  single-column file) default to comma. */
    private fun detectDelimiter(text: String): Char {
        val firstLineEnd = text.indexOf('\n').let { if (it < 0) text.length else it }
        val firstLine = text.substring(0, firstLineEnd)
        var commaCount = 0
        var semicolonCount = 0
        var inQuotes = false
        for (c in firstLine) {
            when {
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> commaCount++
                c == ';' && !inQuotes -> semicolonCount++
            }
        }
        return if (semicolonCount > commaCount) ';' else ','
    }

    private fun tokenize(text: String, delimiter: Char): List<RawRow> {
        if (text.isEmpty()) return emptyList()

        val rows = mutableListOf<RawRow>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0

        fun endField() {
            row.add(field.toString())
            field.clear()
        }
        fun endRow() {
            endField()
            rows.add(row)
            row = mutableListOf()
        }

        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
                c == '"' -> inQuotes = true
                c == delimiter -> endField()
                c == '\r' -> { /* no-op — the following '\n' (or end of file) ends the row */ }
                c == '\n' -> endRow()
                else -> field.append(c)
            }
            i++
        }
        // A file that doesn't end with a newline still has one final row/field to flush; a file
        // that DOES end with a newline must NOT get a spurious trailing empty row — by the time
        // the loop hits that last '\n', endRow() already ran and left field/row both empty, so
        // this check naturally does the right thing in both cases.
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()

        return rows
    }
}
