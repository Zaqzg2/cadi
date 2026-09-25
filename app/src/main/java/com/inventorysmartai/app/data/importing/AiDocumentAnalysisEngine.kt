package com.inventorysmartai.app.data.importing

import com.inventorysmartai.app.data.remote.BackendFailure
import com.inventorysmartai.app.domain.importing.ImportPipeline
import com.inventorysmartai.app.domain.importing.ImportType
import com.inventorysmartai.app.domain.importing.ai.AiExtractionDocumentType
import com.inventorysmartai.app.domain.importing.ai.AiExtractionMapper
import com.inventorysmartai.app.domain.model.ImportErrorCode
import com.inventorysmartai.app.domain.repository.AiDocumentImportRepository
import com.inventorysmartai.app.domain.repository.ImportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The AI-sourced counterpart to [ImportEngine.analyze] — same [ImportProgress] states, so
 * `ImportFlowViewModel.runAnalysis()` can drive either one through identical progress-handling/
 * collection code (see that ViewModel: it picks this engine when the detected source is
 * PDF/IMAGE/CAMERA, [ImportEngine] otherwise). Starts one stage later than the tabular path — no
 * header-row detection or column mapping exists for a photographed document — but converges on
 * the exact same [ImportPipeline.analyzeRows] stage; see [AiExtractionMapper]'s doc comment for
 * why that convergence point is the important part of this design.
 */
@Singleton
class AiDocumentAnalysisEngine @Inject constructor(
    private val aiDocumentImportRepository: AiDocumentImportRepository,
    private val extractionMapper: AiExtractionMapper,
    private val importPipeline: ImportPipeline,
    private val importRepository: ImportRepository
) {
    fun analyzeDocument(
        fileBytes: ByteArray,
        mimeType: String,
        importType: ImportType,
        importJobId: Long,
        sessionId: String
    ): Flow<ImportProgress> = flow {
        emit(ImportProgress.Stage("جارٍ رفع المستند وتحليله بالذكاء الاصطناعي..."))

        val documentType = importType.toAiDocumentType()
            ?: run {
                emit(ImportProgress.Failed(ImportErrorCode.UNSUPPORTED_FORMAT, "نوع البيانات \"${importType.labelAr}\" غير مدعوم في الاستخراج بالذكاء الاصطناعي"))
                return@flow
            }

        val extraction = aiDocumentImportRepository.extract(fileBytes, mimeType, documentType, sessionId).getOrElse { error ->
            emit(ImportProgress.Failed(ImportErrorCode.AI_EXTRACTION_FAILED, describeAiFailure(error)))
            return@flow
        }

        emit(ImportProgress.Stage("جارٍ التحقق من البيانات ومطابقة الأصناف..."))

        val mapping = extractionMapper.toParsedRows(extraction, importType, importJobId)
        if (mapping.metadataJson != null) {
            importRepository.updateJobMetadata(importJobId, mapping.metadataJson)
        }

        if (mapping.rows.isEmpty()) {
            emit(ImportProgress.Failed(ImportErrorCode.EMPTY_FILE, "لم يتمكن الذكاء الاصطناعي من العثور على أي صفوف قابلة للاستيراد في هذا المستند"))
            return@flow
        }

        val result = importPipeline.analyzeRows(mapping.rows, importType) { processed, total ->
            emit(ImportProgress.Analyzing(processed, total))
        }
        emit(ImportProgress.Done(result))
    }

    private fun ImportType.toAiDocumentType(): AiExtractionDocumentType? = when (this) {
        ImportType.PRODUCTS -> AiExtractionDocumentType.PRODUCTS
        ImportType.INVENTORY -> AiExtractionDocumentType.INVENTORY
        ImportType.COUNTING -> AiExtractionDocumentType.COUNTING
        ImportType.PURCHASE_REQUESTS -> AiExtractionDocumentType.PURCHASE_REQUESTS
        ImportType.SALES_INVOICES -> AiExtractionDocumentType.SALES_INVOICES
        ImportType.GOALS -> AiExtractionDocumentType.GOALS
    }

    private fun describeAiFailure(error: Throwable): String =
        (error as? BackendFailure)?.messageAr
            ?: "تعذّر استخراج البيانات بالذكاء الاصطناعي، يرجى المحاولة مرة أخرى"
}
