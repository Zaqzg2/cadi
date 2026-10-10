package com.inventorysmartai.app.data.assistant.files

import com.inventorysmartai.app.data.ai.AiConfig
import com.inventorysmartai.app.data.ai.AiFileTooLargeException
import com.inventorysmartai.app.data.ai.provider.PdfRasterizer
import com.inventorysmartai.app.data.ai.provider.toProviderFailure
import com.inventorysmartai.app.data.backend.BackendConfig
import com.inventorysmartai.app.data.remote.BackendApi
import com.inventorysmartai.app.data.remote.BackendFailure
import com.inventorysmartai.app.data.remote.safeApiCall
import com.squareup.moshi.Moshi
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Photo / PDF -> text, done by the app's own backend (POST /v1/documents/read): Mistral OCR when the server has a key,
 * a vision model otherwise. This is what lets the assistant "look at" an attached picture of a price list or a target sheet.
 *
 * Like the document import, it re-sends a PDF as page images when the server says it cannot read the PDF itself
 * (422 PDF_NEEDS_IMAGES), and converts HEIC photos to JPEG first, since the server accepts only PDF/JPEG/PNG/WebP.
 * Without the backend (the person chose their own provider keys) there is nothing to read with, and it says so.
 */
@Singleton
class AttachmentTextReader @Inject constructor(
    private val api: BackendApi,
    private val moshi: Moshi,
    private val rasterizer: PdfRasterizer
) {
    private class UploadPart(val bytes: ByteArray, val mimeType: String)

    suspend fun read(bytes: ByteArray, mimeType: String, sessionId: String): Result<String> {
        BackendConfig.configurationProblem()?.let {
            return Result.failure(BackendFailure.Structured("BACKEND_NOT_CONFIGURED", it))
        }
        if (bytes.size > AiConfig.MAX_INLINE_BYTES) {
            return Result.failure(AiFileTooLargeException(bytes.size, AiConfig.MAX_INLINE_BYTES).toProviderFailure())
        }

        val firstAttempt: List<UploadPart> = try {
            if (mimeType in SERVER_READABLE_TYPES) {
                listOf(UploadPart(bytes, mimeType))
            } else {
                rasterizer.prepare(bytes, mimeType, MAX_PAGES).map { UploadPart(it.bytes, it.mimeType) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.failure(e.toProviderFailure())
        }

        val first = upload(firstAttempt, sessionId)
        val failure = first.exceptionOrNull()
        if (mimeType == PDF && failure is BackendFailure.Structured && failure.code == CODE_PDF_NEEDS_IMAGES) {
            val pages = try {
                rasterizer.prepare(bytes, mimeType, MAX_PAGES).map { UploadPart(it.bytes, it.mimeType) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return first
            }
            if (pages.isNotEmpty()) return upload(pages, sessionId)
        }
        return first
    }

    private suspend fun upload(parts: List<UploadPart>, sessionId: String): Result<String> = safeApiCall(moshi) {
        val fileParts = parts.mapIndexed { index, part ->
            MultipartBody.Part.createFormData("file", "page-${index + 1}", part.bytes.toRequestBody(part.mimeType.toMediaTypeOrNull()))
        }
        val sessionIdPart = sessionId.toRequestBody("text/plain".toMediaTypeOrNull())
        api.readDocument(fileParts, sessionIdPart).text
    }

    private companion object {
        const val PDF = "application/pdf"
        const val CODE_PDF_NEEDS_IMAGES = "PDF_NEEDS_IMAGES"
        const val MAX_PAGES = 5
        val SERVER_READABLE_TYPES = setOf(PDF, "image/jpeg", "image/png", "image/webp")
    }
}
