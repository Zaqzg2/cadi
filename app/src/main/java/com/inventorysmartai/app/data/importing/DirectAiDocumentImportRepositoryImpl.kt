package com.inventorysmartai.app.data.importing

import com.inventorysmartai.app.data.ai.AiConfig
import com.inventorysmartai.app.data.ai.AiTimeoutException
import com.inventorysmartai.app.data.ai.provider.DirectDocumentExtractor
import com.inventorysmartai.app.data.ai.provider.toProviderFailure
import com.inventorysmartai.app.domain.importing.ai.AiExtractionDocument
import com.inventorysmartai.app.domain.importing.ai.AiExtractionDocumentType
import com.inventorysmartai.app.domain.repository.AiDocumentImportRepository
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * [AiDocumentImportRepository] over the user's own Gemini / Mistral / Groq / OpenRouter keys and OCR.space key (see
 * [DirectDocumentExtractor]). `sessionId` belongs to the paused backend's audit log and is unused here.
 */
@Singleton
class DirectAiDocumentImportRepositoryImpl @Inject constructor(
    private val extractor: DirectDocumentExtractor
) : AiDocumentImportRepository {

    override suspend fun extract(
        fileBytes: ByteArray,
        mimeType: String,
        documentType: AiExtractionDocumentType,
        sessionId: String
    ): Result<AiExtractionDocument> = try {
        // OCR first, then up to a few providers in turn, so the budget is three single-request timeouts.
        val document = withTimeoutOrNull(AiConfig.DIRECT_IMPORT_BUDGET_MS) {
            extractor.extract(fileBytes, mimeType, documentType)
        } ?: throw AiTimeoutException()
        Result.success(document)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e.toProviderFailure())
    }
}
