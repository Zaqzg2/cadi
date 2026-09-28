package com.inventorysmartai.app.data.importing.parser

import android.util.Log
import android.util.Xml
import com.inventorysmartai.app.domain.importing.ImportParser
import com.inventorysmartai.app.domain.importing.OpenedFile
import com.inventorysmartai.app.domain.importing.RawRow
import com.inventorysmartai.app.domain.importing.RawTable
import com.inventorysmartai.app.domain.model.ImportSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dhatim.fastexcel.reader.ReadableWorkbook
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.util.stream.Collectors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

private const val EXCEL_PARSER_LOG_TAG = "ExcelImportParser"

/**
 * SAX driver for [XlsxSaxReader] on a device: `android.util.Xml.parse` instantiates the
 * platform's Expat parser directly, so — unlike JAXP/StAX — there is no factory lookup, no
 * ServiceLoader and no dependence on the thread's context class loader.
 */
private object AndroidSaxRunner : XlsxSaxReader.SaxRunner {
    override fun parse(input: InputStream, handler: DefaultHandler) {
        Xml.parse(input, Xml.Encoding.UTF_8, handler)
    }
}

/**
 * Step 3 (Parser) for .xlsx, spec section 4.
 *
 * Two readers, tried in this order:
 *  1. `org.dhatim:fastexcel-reader` — full-featured, and what the unit tests exercise on the JVM.
 *  2. [XlsxSaxReader] — a small reader built only on `java.util.zip` and the platform's own SAX
 *     parser, used whenever (1) fails for any reason.
 *
 * Why (2) exists: fastexcel-reader parses XML through a StAX factory created in the static
 * initializer of its `DefaultXMLInputFactory`. On Android that initializer can fail, and ART
 * then remembers the failure for the life of the process: every later use throws a
 * `NoClassDefFoundError` whose message is just `org.dhatim.fastexcel.reader.DefaultXMLInputFactory`
 * — the exact text the import screen used to show ("تعذّر قراءة الملف: org.dhatim...").
 * Because that failure is permanent per process, retrying (or re-setting the context class
 * loader before each call, see [useAppClassLoaderForStax]) cannot recover from it; only not
 * depending on that class can. So the first [LinkageError] switches this parser to (2) for good,
 * and any other failure of (1) falls back for that one call. The real cause is written to Logcat
 * (tag `ExcelImportParser`) every time the fallback is used.
 *
 * Deliberately does NOT assume a fixed column layout, a fixed header row, or a single sheet
 * (spec: "the importer must inspect workbook structure") — [listSheets] surfaces every sheet in
 * the workbook for the user to pick from; where the real header row sits within a chosen sheet is
 * [HeaderRowDetector]'s job, not this parser's.
 */
@Singleton
class ExcelImportParser internal constructor(
    private val saxRunner: XlsxSaxReader.SaxRunner
) : ImportParser {

    /** Production constructor (Hilt): uses the platform's SAX parser. Tests may pass their own
     *  runner through the internal primary constructor. */
    @Inject
    constructor() : this(AndroidSaxRunner)

    override val sourceType: ImportSourceType = ImportSourceType.EXCEL

    /** Turned off for good the first time fastexcel-reader dies with a [LinkageError] (a failed
     *  class initializer cannot be retried within a process). */
    @Volatile
    private var fastExcelUsable = true

    override fun supports(file: OpenedFile): Boolean {
        val name = file.displayName.lowercase()
        return name.endsWith(".xlsx") || file.mimeType?.contains("spreadsheetml") == true
    }

    override suspend fun listSheets(file: OpenedFile): List<String?> = withContext(Dispatchers.IO) {
        withFallback(
            what = "listSheets",
            primary = { fastExcelListSheets(file) },
            fallback = { fallbackListSheets(file) }
        )
    }

    override suspend fun parseSheet(file: OpenedFile, sheetName: String?): RawTable = withContext(Dispatchers.IO) {
        withFallback(
            what = "parseSheet",
            primary = { fastExcelParseSheet(file, sheetName) },
            fallback = { fallbackParseSheet(file, sheetName) }
        )
    }

    /**
     * Runs [primary] while fastexcel-reader is usable, otherwise (or when it fails) [fallback].
     * If both fail, the fallback's exception is the one thrown — its message describes the file
     * itself — with the primary failure attached as a suppressed exception.
     */
    private inline fun <T> withFallback(what: String, primary: () -> T, fallback: () -> T): T {
        var primaryFailure: Throwable? = null
        if (fastExcelUsable) {
            try {
                return primary()
            } catch (e: CancellationException) {
                throw e
            } catch (e: LinkageError) {
                fastExcelUsable = false
                primaryFailure = e
            } catch (e: Exception) {
                primaryFailure = e
            }
            primaryFailure?.let { logFallback(what, it) }
        }
        try {
            return fallback()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            primaryFailure?.let { e.addSuppressed(it) }
            throw e
        }
    }

    private fun logFallback(what: String, error: Throwable) {
        // Log is a stub that throws in plain-JVM unit tests; diagnostics must never break a parse.
        runCatching {
            Log.w(EXCEL_PARSER_LOG_TAG, "fastexcel-reader failed in $what; using the built-in XLSX reader instead", error)
        }
    }

    // ---- 1. fastexcel-reader --------------------------------------------------------------

    private fun fastExcelListSheets(file: OpenedFile): List<String?> {
        useAppClassLoaderForStax()
        return ReadableWorkbook(file.inputStream()).use { workbook ->
            workbook.sheets.map { it.name as String? }.collect(Collectors.toList())
        }
    }

    private fun fastExcelParseSheet(file: OpenedFile, sheetName: String?): RawTable {
        useAppClassLoaderForStax()
        return ReadableWorkbook(file.inputStream()).use { workbook ->
            val sheet = if (sheetName != null) {
                workbook.sheets.filter { it.name == sheetName }.findFirst()
                    .orElseThrow { IllegalStateException("لم يتم العثور على الورقة \"$sheetName\" في هذا الملف") }
            } else {
                workbook.firstSheet
            }

            // sheet.read() loads this one sheet's rows into memory (the workbook's bytes are
            // already fully buffered by the InputStream constructor regardless — see
            // ReadableWorkbook's own doc comment — so this adds no extra I/O cost); each row's
            // logical cell count (its used range, not just non-empty cells) decides how many
            // columns to read, and getCellText() already safely returns "" for a gap/merged cell
            // rather than throwing, so a ragged/merged sheet never crashes this parser.
            val rows: List<RawRow> = sheet.read().map { row ->
                (0 until row.cellCount).map { colIndex -> row.getCellText(colIndex).trim() }
            }
            RawTable(sheetName = sheet.name, rows = rows)
        }
    }

    /**
     * Best-effort: makes this thread's context class loader the app's own before touching
     * `ReadableWorkbook`, in case its StAX provider lookup consults it (threads created by
     * `Dispatchers.IO` don't necessarily carry the app's loader). This helps only when the very
     * first initialization happens here; it cannot undo an initialization that already failed —
     * that is what [XlsxSaxReader] is for.
     */
    private fun useAppClassLoaderForStax() {
        Thread.currentThread().contextClassLoader = javaClass.classLoader
    }

    // ---- 2. built-in reader (java.util.zip + platform SAX) ---------------------------------

    private fun fallbackListSheets(file: OpenedFile): List<String?> =
        XlsxSaxReader(saxRunner).listSheets(readAllBytes(file))

    private fun fallbackParseSheet(file: OpenedFile, sheetName: String?): RawTable {
        val table = XlsxSaxReader(saxRunner).readSheet(readAllBytes(file), sheetName)
        // XlsxSaxReader returns cell text as stored; trimming is applied here so both readers
        // hand the pipeline identically trimmed cells.
        val rows: List<RawRow> = table.rows.map { row -> row.map { cell -> cell.trim() } }
        return RawTable(sheetName = table.sheetName, rows = rows)
    }

    private fun readAllBytes(file: OpenedFile): ByteArray = file.inputStream().use { it.readBytes() }
}
