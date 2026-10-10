package com.inventorysmartai.backend.routes

import com.inventorysmartai.backend.ai.DocumentExtractor
import com.inventorysmartai.backend.ai.DocumentReader
import com.inventorysmartai.backend.ai.ExtractionDocumentType
import com.inventorysmartai.backend.ai.RequestRejectedException
import com.inventorysmartai.backend.ai.UploadedFile
import com.inventorysmartai.backend.audit.AuditLog
import com.inventorysmartai.backend.routes.dto.DocumentExtractionResponseDto
import com.inventorysmartai.backend.routes.dto.DocumentReadResponseDto
import com.inventorysmartai.backend.security.Bucket
import com.inventorysmartai.backend.security.RequestGuard
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray

/** Extra bytes a multipart envelope adds around the file(s). */
private const val MULTIPART_OVERHEAD = 64L * 1024

/** What one multipart upload carried: the files plus the small form fields every document route understands. */
private class UploadRequest(
    val files: List<UploadedFile>,
    val documentType: String?,
    val sessionId: String,
    val preferOcr: Boolean,
    val totalBytes: Long
)

/** Reads the multipart body, enforcing the size cap while it streams. Shared by /extract and /read. */
private suspend fun ApplicationCall.receiveUpload(maxUploadBytes: Int): UploadRequest {
    val files = mutableListOf<UploadedFile>()
    var documentTypeRaw: String? = null
    var sessionId = "unknown"
    var preferOcr = true
    var totalBytes = 0L

    receiveMultipart().forEachPart { part ->
        when (part) {
            is PartData.FileItem -> {
                val bytes = part.provider().readRemaining().readByteArray()
                totalBytes += bytes.size
                if (totalBytes > maxUploadBytes) {
                    throw RequestRejectedException(413, "PAYLOAD_TOO_LARGE", "حجم الملف أكبر من الحد المسموح (${maxUploadBytes / (1024 * 1024)} ميغابايت)")
                }
                files += UploadedFile(sniffMimeType(bytes, part.contentType?.toString()), bytes)
            }
            is PartData.FormItem -> when (part.name) {
                "documentType" -> documentTypeRaw = part.value
                "sessionId" -> sessionId = part.value.take(64)
                "preferOcr" -> preferOcr = !part.value.equals("false", ignoreCase = true)
                else -> Unit
            }
            else -> Unit
        }
        part.dispose()
    }
    return UploadRequest(files, documentTypeRaw, sessionId, preferOcr, totalBytes)
}

/**
 * POST /v1/documents/extract — multipart/form-data:
 *  - one or more "file" parts: a single PDF, OR up to 5 page images (JPEG/PNG/WebP, in page order);
 *  - "documentType": one of [ExtractionDocumentType];
 *  - "sessionId" (optional, audit only) and "preferOcr" (optional, "false" skips OCR).
 * 422 PDF_NEEDS_IMAGES means "I could not read that PDF — send its pages as images".
 *
 * POST /v1/documents/read — the same upload without "documentType": answers `{text, pages, provider, usedOcr}` with the
 * document's text (tables as Markdown tables) and no structuring, for the assistant's "attach a file" feature.
 */
fun Route.documentRoutes(extractor: DocumentExtractor, reader: DocumentReader, guard: RequestGuard, audit: AuditLog, maxUploadBytes: Int) {
    post("/v1/documents/extract") {
        if (!guard.admit(call, Bucket.EXTRACT)) return@post
        if (!guard.bodyWithin(call, maxUploadBytes + MULTIPART_OVERHEAD)) return@post

        val upload = call.receiveUpload(maxUploadBytes)
        val files = upload.files
        if (files.isEmpty()) {
            call.respondError(HttpStatusCode.BadRequest, "MISSING_FILE", "لم يُرسل أي ملف في الطلب")
            return@post
        }
        val documentType = upload.documentType?.let { raw -> ExtractionDocumentType.entries.firstOrNull { it.name == raw } }
        if (documentType == null) {
            call.respondError(
                HttpStatusCode.BadRequest,
                "INVALID_DOCUMENT_TYPE",
                "documentType يجب أن يكون واحدًا من: ${ExtractionDocumentType.entries.joinToString { it.name }}"
            )
            return@post
        }

        val outcome = extractor.extract(documentType, files, upload.preferOcr)

        audit.record(
            "AI_IMPORT",
            upload.sessionId,
            mapOf(
                "documentType" to documentType.name,
                "files" to files.size.toString(),
                "sizeBytes" to upload.totalBytes.toString(),
                "provider" to outcome.providerId,
                "ocr" to outcome.usedOcr.toString()
            )
        )

        call.respond(DocumentExtractionResponseDto(documentType.name, outcome.result, outcome.providerId, outcome.model, outcome.usedOcr))
    }

    post("/v1/documents/read") {
        if (!guard.admit(call, Bucket.EXTRACT)) return@post
        if (!guard.bodyWithin(call, maxUploadBytes + MULTIPART_OVERHEAD)) return@post

        val upload = call.receiveUpload(maxUploadBytes)
        if (upload.files.isEmpty()) {
            call.respondError(HttpStatusCode.BadRequest, "MISSING_FILE", "لم يُرسل أي ملف في الطلب")
            return@post
        }

        val outcome = reader.read(upload.files, upload.preferOcr)

        audit.record(
            "AI_READ",
            upload.sessionId,
            mapOf(
                "files" to upload.files.size.toString(),
                "sizeBytes" to upload.totalBytes.toString(),
                "provider" to outcome.providerId,
                "ocr" to outcome.usedOcr.toString()
            )
        )

        call.respond(DocumentReadResponseDto(outcome.text, outcome.pages, outcome.providerId, outcome.usedOcr))
    }
}

/** What the bytes really are (the declared content type is only a fallback): PDF, JPEG, PNG or WebP. */
internal fun sniffMimeType(bytes: ByteArray, declared: String?): String {
    fun starts(vararg signature: Int): Boolean =
        bytes.size >= signature.size && signature.indices.all { (bytes[it].toInt() and 0xFF) == signature[it] }

    return when {
        starts(0x25, 0x50, 0x44, 0x46) -> "application/pdf"
        starts(0xFF, 0xD8, 0xFF) -> "image/jpeg"
        starts(0x89, 0x50, 0x4E, 0x47) -> "image/png"
        bytes.size >= 12 && starts(0x52, 0x49, 0x46, 0x46) && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
        else -> declared?.substringBefore(';')?.trim()?.lowercase() ?: "application/octet-stream"
    }
}
