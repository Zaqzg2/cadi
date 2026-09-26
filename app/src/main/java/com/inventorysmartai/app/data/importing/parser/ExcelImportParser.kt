package com.inventorysmartai.app.data.importing.parser

import com.inventorysmartai.app.domain.importing.ImportParser
import com.inventorysmartai.app.domain.importing.OpenedFile
import com.inventorysmartai.app.domain.importing.RawRow
import com.inventorysmartai.app.domain.importing.RawTable
import com.inventorysmartai.app.domain.model.ImportSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dhatim.fastexcel.reader.ReadableWorkbook
import java.util.stream.Collectors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Step 3 (Parser) for .xlsx, spec section 4. Uses org.dhatim:fastexcel-reader (see
 * libs.versions.toml for why this specific library) instead of Apache POI's XSSF reader directly
 * — XSSF resolves its StAX (javax.xml.stream) implementation via ServiceLoader, which on Android
 * finds the platform's non-functional bootclasspath stub (present for source compatibility, not
 * a real implementation) rather than a working one; this is the single most common cause of
 * "reads fine on the JVM, throws on-device" for Excel libraries on Android. fastexcel-reader
 * bundles and directly instantiates its own StAX implementation (aalto-xml) instead of relying on
 * that lookup.
 *
 * Deliberately does NOT assume a fixed column layout, a fixed header row, or a single sheet
 * (spec: "the importer must inspect workbook structure") — [listSheets] surfaces every sheet in
 * the workbook for the user to pick from; where the real header row sits within a chosen sheet is
 * [HeaderRowDetector]'s job, not this parser's.
 */
@Singleton
class ExcelImportParser @Inject constructor() : ImportParser {

    override val sourceType: ImportSourceType = ImportSourceType.EXCEL

    override fun supports(file: OpenedFile): Boolean {
        val name = file.displayName.lowercase()
        return name.endsWith(".xlsx") || file.mimeType?.contains("spreadsheetml") == true
    }

    override suspend fun listSheets(file: OpenedFile): List<String?> = withContext(Dispatchers.IO) {
        useAppClassLoaderForStax()
        ReadableWorkbook(file.inputStream()).use { workbook ->
            workbook.sheets.map { it.name as String? }.collect(Collectors.toList())
        }
    }

    override suspend fun parseSheet(file: OpenedFile, sheetName: String?): RawTable = withContext(Dispatchers.IO) {
        useAppClassLoaderForStax()
        ReadableWorkbook(file.inputStream()).use { workbook ->
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
     * `ReadableWorkbook` resolves its StAX implementation via
     * `javax.xml.stream.XMLInputFactory.newInstance()`, which loads the provider class
     * (`org.dhatim.fastexcel.reader.DefaultXMLInputFactory`) by name using the calling
     * **thread's context class loader** — not the app's own class loader. On Android, threads
     * that Kotlin coroutines' `Dispatchers.IO` pool creates internally do not inherit the app's
     * class loader as their context class loader (it defaults to the boot class loader, which
     * only knows Android's built-in stub classes). The provider class lives in the app's own APK,
     * so that lookup throws `ClassNotFoundException: org.dhatim.fastexcel.reader.
     * DefaultXMLInputFactory` — this is what surfaces as "تعذّر قراءة الملف: org.dhatim.fastexcel.
     * reader.DefaultXMLInputFactory" in the UI. Explicitly setting the context class loader to
     * this class's own loader (guaranteed to be the app's) before touching `ReadableWorkbook`
     * fixes the lookup. Must be called on the same (IO-dispatcher) thread that will run the
     * `ReadableWorkbook` call, since the context class loader is thread-local.
     */
    private fun useAppClassLoaderForStax() {
        Thread.currentThread().contextClassLoader = javaClass.classLoader
    }
}
