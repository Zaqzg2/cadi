package com.inventorysmartai.app.data.importing

import com.inventorysmartai.app.data.importing.parser.CsvImportParser
import com.inventorysmartai.app.data.importing.parser.ExcelImportParser
import com.inventorysmartai.app.domain.importing.ColumnMappingResult
import com.inventorysmartai.app.domain.importing.ImportParser
import com.inventorysmartai.app.domain.importing.ImportPipeline
import com.inventorysmartai.app.domain.importing.ImportSource
import com.inventorysmartai.app.domain.importing.ImportType
import com.inventorysmartai.app.domain.importing.OpenedFile
import com.inventorysmartai.app.domain.importing.PipelineAnalysisResult
import com.inventorysmartai.app.domain.model.ImportErrorCode
import com.inventorysmartai.app.domain.model.ImportSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

/** The UX flow's steps 4-7 ("تحليل الملف" / "مطابقة الأعمدة" / "التحقق" / "مطابقة الأصناف",
 *  spec section 24), reported as they happen so the analyzing screen can show real progress
 *  (spec section 23) instead of a plain spinner. [Analyzing] is emitted once per row as
 *  ProductMatching/DuplicateDetection resolve it — the DB-hitting part, and the only part worth
 *  reporting granularly on a large file; header detection/column mapping run once and are cheap
 *  enough to not need their own row counter. */
sealed interface ImportProgress {
    data class Stage(val labelAr: String) : ImportProgress
    data class Analyzing(val processed: Int, val total: Int) : ImportProgress
    data class Done(val result: PipelineAnalysisResult) : ImportProgress
    data class Failed(val errorCode: ImportErrorCode, val messageAr: String) : ImportProgress
}

interface ImportEngine {
    suspend fun openFile(uriReference: String): OpenedFile
    fun detect(file: OpenedFile): FileDetectionResult
    suspend fun listSheets(file: OpenedFile, sourceType: ImportSourceType): List<String?>
    fun analyze(
        file: OpenedFile,
        sourceType: ImportSourceType,
        sheetName: String?,
        importType: ImportType,
        importJobId: Long,
        mappingOverride: ColumnMappingResult?
    ): Flow<ImportProgress>
}

@Singleton
class DefaultImportEngine @Inject constructor(
    private val importSource: ImportSource,
    private val csvParser: CsvImportParser,
    private val excelParser: ExcelImportParser,
    private val pipeline: ImportPipeline
) : ImportEngine {

    override suspend fun openFile(uriReference: String): OpenedFile = importSource.open(uriReference)

    override fun detect(file: OpenedFile): FileDetectionResult = FileDetector.detect(file)

    private fun parserFor(sourceType: ImportSourceType): ImportParser = when (sourceType) {
        ImportSourceType.CSV -> csvParser
        ImportSourceType.EXCEL -> excelParser
        else -> error("لا يوجد محلل لنوع المصدر $sourceType في هذه المرحلة (Excel/CSV فقط)")
    }

    override suspend fun listSheets(file: OpenedFile, sourceType: ImportSourceType): List<String?> =
        parserFor(sourceType).listSheets(file)

    override fun analyze(
        file: OpenedFile,
        sourceType: ImportSourceType,
        sheetName: String?,
        importType: ImportType,
        importJobId: Long,
        mappingOverride: ColumnMappingResult?
    ): Flow<ImportProgress> = flow {
        emit(ImportProgress.Stage("تحليل الملف"))
        val table = runCatching { parserFor(sourceType).parseSheet(file, sheetName) }
            .getOrElse { e ->
                emit(ImportProgress.Failed(ImportErrorCode.UNSUPPORTED_FORMAT, "تعذّر قراءة الملف: ${e.message ?: e.javaClass.simpleName}"))
                return@flow
            }

        if (table.rows.isEmpty()) {
            emit(ImportProgress.Failed(ImportErrorCode.EMPTY_FILE, "الملف/الورقة المحددة لا تحتوي على أي بيانات قابلة للقراءة"))
            return@flow
        }

        emit(ImportProgress.Stage("مطابقة الأعمدة والتحقق من البيانات"))
        val result = pipeline.analyze(table, importType, importJobId, mappingOverride) { processed, total ->
            emit(ImportProgress.Analyzing(processed, total))
        }
        emit(ImportProgress.Done(result))
    }.flowOn(Dispatchers.Default)
}
