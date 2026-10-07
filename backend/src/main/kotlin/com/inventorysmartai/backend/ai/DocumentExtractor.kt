package com.inventorysmartai.backend.ai

import com.inventorysmartai.backend.config.AiProviderConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.LoggerFactory
import java.util.Base64
import kotlin.coroutines.cancellation.CancellationException

class UploadedFile(val mimeType: String, val bytes: ByteArray)

class ExtractionOutcome(val result: JsonObject, val providerId: String, val model: String, val usedOcr: Boolean)

/**
 * Document -> the app's extraction JSON, entirely on the server.
 *
 * Arabic reading quality is the weak point of free vision models, so there are two stages:
 *  1. READ      — Mistral OCR turns a PDF/photo into Markdown (when a Mistral key is configured).
 *  2. STRUCTURE — the providers in priority order turn that text into the JSON shape in [ExtractionSchemas],
 *                 falling through to the next provider on a rate-limit, an outage or invalid JSON.
 * Without OCR text, IMAGES go to the vision models directly. The server never rasterises a PDF: when a PDF cannot
 * be read it answers [PdfNeedsImagesException] and the app re-sends the pages as images.
 *
 * What comes back is only a DRAFT — the app still runs its own normaliser/validator and the human review screen
 * before anything is saved.
 */
class DocumentExtractor(
    private val gateway: AiGateway,
    private val transport: AiTransport
) {
    private val log = LoggerFactory.getLogger(DocumentExtractor::class.java)

    suspend fun extract(type: ExtractionDocumentType, files: List<UploadedFile>, preferOcr: Boolean = true): ExtractionOutcome {
        if (files.isEmpty()) throw RequestRejectedException(400, "MISSING_FILE", "لم يُرسل أي ملف")
        if (files.size > MAX_FILES) throw RequestRejectedException(400, "TOO_MANY_FILES", "الحد الأقصى $MAX_FILES صفحات في الطلب الواحد")
        files.forEach {
            if (it.mimeType !in ACCEPTED_TYPES) {
                throw RequestRejectedException(415, "UNSUPPORTED_FILE_TYPE", "نوع الملف غير مدعوم (${it.mimeType}). المسموح: PDF أو JPEG أو PNG أو WebP")
            }
        }
        val isPdf = files.any { it.mimeType == PDF }
        if (isPdf && files.size > 1) {
            throw RequestRejectedException(400, "BAD_REQUEST", "أرسل ملف PDF واحدًا فقط، أو عدة صور للصفحات")
        }

        val instruction = buildInstruction(type)
        var lastError: Exception? = null

        // ---- Stage 1: read (OCR) ----
        val ocrProvider = gateway.providers.firstOrNull { it.supportsOcr }
        var ocrText: String? = null
        if (preferOcr && ocrProvider != null) {
            try {
                ocrText = readWithOcr(ocrProvider, files)?.takeIf { it.isNotBlank() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                log.warn("OCR failed: {}", e.message)
            }
        }

        // ---- Stage 2a: structure the OCR text ----
        if (ocrText != null) {
            val messages = listOf(
                message("system", instruction),
                message("user", "نص المستند بعد القراءة الضوئية (OCR):\n\n" + ocrText.take(MAX_OCR_CHARS))
            )
            try {
                return structure(type, messages, vision = false, usedOcr = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }

        // ---- Stage 2b: vision on the page images (images only) ----
        if (isPdf) {
            // OCR produced nothing usable. If it DID read the text but every structuring attempt failed, that error is
            // the honest answer (a rate limit, say); otherwise the app should retry with page images.
            val error = lastError
            if (ocrText != null && error != null) throw error
            throw PdfNeedsImagesException()
        }
        val images = files.take(MAX_VISION_IMAGES)
        val parts = ArrayList<JsonElement>()
        parts += JsonObject(mapOf("type" to JsonPrimitive("text"), "text" to JsonPrimitive("اقرأ المستند المرفق (الصفحات بالترتيب) وأخرج JSON فقط.")))
        images.forEach { image ->
            val dataUri = "data:${image.mimeType};base64," + Base64.getEncoder().encodeToString(image.bytes)
            parts += JsonObject(
                mapOf(
                    "type" to JsonPrimitive("image_url"),
                    "image_url" to JsonObject(mapOf("url" to JsonPrimitive(dataUri)))
                )
            )
        }
        val visionMessages = listOf(
            message("system", instruction),
            JsonObject(mapOf("role" to JsonPrimitive("user"), "content" to JsonArray(parts)))
        )
        return structure(type, visionMessages, vision = true, usedOcr = false)
    }

    private suspend fun readWithOcr(provider: AiProviderConfig, files: List<UploadedFile>): String? {
        val texts = files.map { transport.ocr(provider, it.mimeType, it.bytes) }.filter { it.isNotBlank() }
        return if (texts.isEmpty()) null else texts.joinToString("\n\n")
    }

    private suspend fun structure(
        type: ExtractionDocumentType,
        messages: List<JsonObject>,
        vision: Boolean,
        usedOcr: Boolean
    ): ExtractionOutcome {
        val attempted = gateway.attempt(vision) { provider, model ->
            val body = ChatParsing.buildBody(
                model = model,
                messages = MessageSanitizer.forProvider(provider.id, messages),
                tools = emptyList(),
                jsonMode = true,
                temperature = 0.0,
                maxTokens = MAX_OUTPUT_TOKENS
            )
            val parsed = ChatParsing.parse(provider.id, transport.chat(provider, body))
            parseDocument(parsed.text.orEmpty(), type)
        }
        return ExtractionOutcome(attempted.value, attempted.provider.id, attempted.model, usedOcr)
    }

    private fun message(role: String, content: String): JsonObject =
        JsonObject(mapOf("role" to JsonPrimitive(role), "content" to JsonPrimitive(content)))

    companion object {
        const val PDF = "application/pdf"
        val ACCEPTED_TYPES = setOf(PDF, "image/jpeg", "image/png", "image/webp")
        const val MAX_FILES = 5
        const val MAX_VISION_IMAGES = 3 // Groq accepts at most 3 images per request
        const val MAX_OCR_CHARS = 40_000
        const val MAX_OUTPUT_TOKENS = 8_000

        private val lenientJson = Json { ignoreUnknownKeys = true }

        /** The system prompt: the type-specific instruction plus the JSON Schema the answer must match. */
        fun buildInstruction(type: ExtractionDocumentType): String = buildString {
            append(ExtractionSchemas.instructionFor(type))
            append("\n\nThe document is usually in Arabic; keep Arabic text and digits exactly as written.")
            append("\nReturn ONLY one JSON object — no Markdown fence, no commentary — that conforms to this JSON Schema:\n")
            append(ExtractionSchemas.forDocumentType(type).toString())
        }

        /** Model text -> the document object. Throws [ProviderInvalidOutputException] (so the gateway tries the next provider). */
        fun parseDocument(raw: String, type: ExtractionDocumentType): JsonObject {
            val text = JsonText.extractObjectText(JsonText.stripCodeFence(raw))
                ?: throw ProviderInvalidOutputException("Model answer contained no JSON object")
            val obj = try {
                lenientJson.parseToJsonElement(text) as? JsonObject
            } catch (e: Exception) {
                null
            } ?: throw ProviderInvalidOutputException("Model answer was not valid JSON")
            if (obj["rows"] !is JsonArray) throw ProviderInvalidOutputException("Model JSON has no \"rows\" array")
            // The server decides the type, never the model: the app's mapper keys off it.
            return JsonObject(obj + ("documentType" to JsonPrimitive(type.name)))
        }
    }
}
