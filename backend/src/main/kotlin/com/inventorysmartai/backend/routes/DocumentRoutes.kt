package com.inventorysmartai.backend.routes

import com.inventorysmartai.backend.audit.AuditLog
import com.inventorysmartai.backend.gemini.ExtractionDocumentType
import com.inventorysmartai.backend.gemini.ExtractionSchemas
import com.inventorysmartai.backend.gemini.GeminiClient
import com.inventorysmartai.backend.gemini.GeminiContent
import com.inventorysmartai.backend.routes.dto.DocumentExtractionResponseDto
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import java.util.Base64

/**
 * multipart/form-data body: a "file" part (the PDF/image bytes), a "documentType" text part (one
 * of [ExtractionDocumentType]'s names), and a "sessionId" text part (used only for the audit log —
 * document extraction needs no Google token, only Gemini's).
 */
fun Route.documentRoutes(geminiClient: GeminiClient, auditLog: AuditLog) {
    post("/v1/documents/extract") {
        var fileBytes: ByteArray? = null
        var mimeType: String = "application/octet-stream"
        var documentTypeRaw: String? = null
        var sessionId: String = "unknown"

        call.receiveMultipart().forEachPart { part ->
            when (part) {
                is PartData.FileItem -> {
                    mimeType = part.contentType?.toString() ?: mimeType
                    fileBytes = part.provider().readRemaining().readByteArray()
                }
                is PartData.FormItem -> when (part.name) {
                    "documentType" -> documentTypeRaw = part.value
                    "sessionId" -> sessionId = part.value
                    else -> Unit
                }
                else -> Unit
            }
            part.dispose()
        }

        val bytes = fileBytes ?: run {
            call.respond(io.ktor.http.HttpStatusCode.BadRequest, mapOf("error" to mapOf("code" to "MISSING_FILE", "message" to "No \"file\" part in the request")))
            return@post
        }
        val documentType = documentTypeRaw?.let { runCatching { ExtractionDocumentType.valueOf(it) }.getOrNull() } ?: run {
            call.respond(
                io.ktor.http.HttpStatusCode.BadRequest,
                mapOf("error" to mapOf("code" to "INVALID_DOCUMENT_TYPE", "message" to "\"documentType\" must be one of ${ExtractionDocumentType.entries.map { it.name }}"))
            )
            return@post
        }

        val base64 = Base64.getEncoder().encodeToString(bytes)
        val contentPart = if (mimeType.startsWith("image/")) {
            GeminiContent.imageBase64(base64, mimeType)
        } else {
            GeminiContent.documentBase64(base64, if (mimeType == "application/octet-stream") "application/pdf" else mimeType)
        }

        val input = GeminiContent.array(GeminiContent.text(ExtractionSchemas.instructionFor(documentType)), contentPart)
        val schema = ExtractionSchemas.forDocumentType(documentType)
        val extracted = geminiClient.createStructured(input = input, jsonSchema = schema, schemaName = documentType.name)

        auditLog.record("AI_IMPORT", sessionId, mapOf("documentType" to documentType.name, "sizeBytes" to bytes.size.toString()))

        call.respond(DocumentExtractionResponseDto(documentType = documentType.name, result = extracted))
    }
}
