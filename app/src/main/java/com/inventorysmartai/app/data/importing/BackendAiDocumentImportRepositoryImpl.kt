package com.inventorysmartai.app.data.importing

import com.inventorysmartai.app.data.ai.AiConfig
import com.inventorysmartai.app.data.ai.AiFileTooLargeException
import com.inventorysmartai.app.data.ai.provider.PdfRasterizer
import com.inventorysmartai.app.data.ai.provider.toProviderFailure
import com.inventorysmartai.app.data.backend.BackendConfig
import com.inventorysmartai.app.data.remote.BackendApi
import com.inventorysmartai.app.data.remote.BackendFailure
import com.inventorysmartai.app.data.remote.safeApiCall
import com.inventorysmartai.app.domain.importing.ai.AiExtractionDocument
import com.inventorysmartai.app.domain.importing.ai.AiExtractionDocumentType
import com.inventorysmartai.app.domain.repository.AiDocumentImportRepository
import com.squareup.moshi.Moshi
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Document -> extraction JSON, done by the app's own backend (the DEFAULT route): the server reads the file with Mistral OCR
 * when it can, structures the text with whichever free provider answers, and falls back to vision models for photos.
 *
 * What stays on the phone: turning a PDF into page images WHEN the server asks for it. The server never rasterises PDFs;
 * if it cannot read one it answers 422 PDF_NEEDS_IMAGES and this class re-sends the pages as JPEGs (using the same
 * [PdfRasterizer] the direct mode uses). HEIC photos are converted to JPEG up front, since the server accepts only
 * PDF/JPEG/PNG/WebP.
 */
@Singleton
class BackendAiDocumentImportRepositoryImpl @Inject constructor(
    private val api: BackendApi,
    private val moshi: Moshi,
    private val rasterizer: PdfRasterizer
) : AiDocumentImportRepository {

    private class UploadPart(val bytes: ByteArray, val mimeType: String)

    override suspend fun extract(
        fileBytes: ByteArray,
        mimeType: String,
        documentType: AiExtractionDocumentType,
        sessionId: String
    ): Result<AiExtractionDocument> {
        BackendConfig.configurationProblem()?.let {
            return Result.failure(BackendFailure.Structured("BACKEND_NOT_CONFIGURED", it))
        }
        if (fileBytes.size > AiConfig.MAX_INLINE_BYTES) {
            return Result.failure(AiFileTooLargeException(fileBytes.size, AiConfig.MAX_INLINE_BYTES).toProviderFailure())
        }

        val firstAttempt: List<UploadPart> = try {
            if (mimeType in SERVER_READABLE_TYPES) {
                listOf(UploadPart(fileBytes, mimeType))
            } else {
                // HEIC and friends: the platform can decode them, the server cannot.
                rasterizer.prepare(fileBytes, mimeType, MAX_PAGES).map { UploadPart(it.bytes, it.mimeType) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.failure(e.toProviderFailure())
        }

        val first = upload(firstAttempt, documentType, sessionId)
        val failure = first.exceptionOrNull()
        if (mimeType == PDF && failure is BackendFailure.Structured && failure.code == CODE_PDF_NEEDS_IMAGES) {
            val pages = try {
                rasterizer.prepare(fileBytes, mimeType, MAX_PAGES).map { UploadPart(it.bytes, it.mimeType) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return first
            }
            if (pages.isNotEmpty()) return upload(pages, documentType, sessionId)
        }
        return first
    }

    private suspend fun upload(
        parts: List<UploadPart>,
        documentType: AiExtractionDocumentType,
        sessionId: String
    ): Result<AiExtractionDocument> = safeApiCall(moshi) {
        val fileParts = parts.mapIndexed { index, part ->
            MultipartBody.Part.createFormData("file", "page-${index + 1}", part.bytes.toRequestBody(part.mimeType.toMediaTypeOrNull()))
        }
        val documentTypePart = documentType.name.toRequestBody("text/plain".toMediaTypeOrNull())
        val sessionIdPart = sessionId.toRequestBody("text/plain".toMediaTypeOrNull())
        api.extractDocument(fileParts, documentTypePart, sessionIdPart).result
    }

    private companion object {
        const val PDF = "application/pdf"
        const val CODE_PDF_NEEDS_IMAGES = "PDF_NEEDS_IMAGES"
        const val MAX_PAGES = 5
        val SERVER_READABLE_TYPES = setOf(PDF, "image/jpeg", "image/png", "image/webp")
    }
}
