package com.inventorysmartai.backend.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.LoggerFactory
import java.util.Base64
import kotlin.coroutines.cancellation.CancellationException

class ReadOutcome(val text: String, val providerId: String, val usedOcr: Boolean, val pages: Int)

/**
 * Document -> plain text, with NO structuring step: what the assistant's "attach a file" feature uses for photos and
 * PDFs, so the model (and the person) can work with whatever the document says — a price list, a target sheet, a form —
 * rather than only the fixed shapes [DocumentExtractor] knows.
 *
 *  1. OCR (Mistral) when a key is configured: the most accurate for Arabic, and tables come back as Markdown tables.
 *  2. Otherwise IMAGES go to a vision model with a "transcribe faithfully" instruction (at most [DocumentExtractor.MAX_VISION_IMAGES]).
 * The server never rasterises a PDF: a PDF that OCR cannot read answers [PdfNeedsImagesException] and the app re-sends
 * its pages as images, exactly as for /v1/documents/extract.
 */
class DocumentReader(
    private val gateway: AiGateway,
    private val transport: AiTransport
) {
    private val log = LoggerFactory.getLogger(DocumentReader::class.java)

    suspend fun read(files: List<UploadedFile>, preferOcr: Boolean = true): ReadOutcome {
        if (files.isEmpty()) throw RequestRejectedException(400, "MISSING_FILE", "لم يُرسل أي ملف")
        if (files.size > DocumentExtractor.MAX_FILES) {
            throw RequestRejectedException(400, "TOO_MANY_FILES", "الحد الأقصى ${DocumentExtractor.MAX_FILES} صفحات في الطلب الواحد")
        }
        files.forEach {
            if (it.mimeType !in DocumentExtractor.ACCEPTED_TYPES) {
                throw RequestRejectedException(415, "UNSUPPORTED_FILE_TYPE", "نوع الملف غير مدعوم (${it.mimeType}). المسموح: PDF أو JPEG أو PNG أو WebP")
            }
        }
        val isPdf = files.any { it.mimeType == DocumentExtractor.PDF }
        if (isPdf && files.size > 1) {
            throw RequestRejectedException(400, "BAD_REQUEST", "أرسل ملف PDF واحدًا فقط، أو عدة صور للصفحات")
        }

        // ---- 1. OCR ----
        val ocrProvider = gateway.providers.firstOrNull { it.supportsOcr }
        if (preferOcr && ocrProvider != null) {
            try {
                val texts = files.map { transport.ocr(ocrProvider, it.mimeType, it.bytes) }.filter { it.isNotBlank() }
                if (texts.isNotEmpty()) {
                    return ReadOutcome(texts.joinToString("\n\n").take(MAX_TEXT_CHARS), ocrProvider.id, usedOcr = true, pages = files.size)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("OCR failed: {}", e.message)
            }
        }

        // ---- 2. vision (images only) ----
        if (isPdf) throw PdfNeedsImagesException()
        val images = files.take(DocumentExtractor.MAX_VISION_IMAGES)
        val parts = ArrayList<JsonElement>()
        parts += JsonObject(mapOf("type" to JsonPrimitive("text"), "text" to JsonPrimitive("انسخ كل ما في المستند المرفق (الصفحات بالترتيب) كما هو.")))
        images.forEach { image ->
            val dataUri = "data:${image.mimeType};base64," + Base64.getEncoder().encodeToString(image.bytes)
            parts += JsonObject(
                mapOf(
                    "type" to JsonPrimitive("image_url"),
                    "image_url" to JsonObject(mapOf("url" to JsonPrimitive(dataUri)))
                )
            )
        }
        val messages = listOf(
            JsonObject(mapOf("role" to JsonPrimitive("system"), "content" to JsonPrimitive(READ_INSTRUCTION))),
            JsonObject(mapOf("role" to JsonPrimitive("user"), "content" to JsonArray(parts)))
        )
        val attempted = gateway.attempt(vision = true) { provider, model ->
            val body = ChatParsing.buildBody(
                model = model,
                messages = MessageSanitizer.forProvider(provider.id, messages),
                tools = emptyList(),
                jsonMode = false,
                temperature = 0.0,
                maxTokens = MAX_OUTPUT_TOKENS
            )
            val parsed = ChatParsing.parse(provider.id, transport.chat(provider, body))
            parsed.text?.takeIf { it.isNotBlank() } ?: throw ProviderInvalidOutputException("Model returned no text")
        }
        return ReadOutcome(attempted.value.take(MAX_TEXT_CHARS), attempted.provider.id, usedOcr = false, pages = images.size)
    }

    companion object {
        const val MAX_TEXT_CHARS = 60_000
        const val MAX_OUTPUT_TOKENS = 6_000

        const val READ_INSTRUCTION: String =
            "You are a precise document reader. Transcribe ALL the text in the attached document or photo exactly as written, " +
                "in reading order, page by page. Keep Arabic text and digits exactly as they appear — do not translate, summarise, " +
                "correct or guess. Render every table as a Markdown table (one row per line, keep every column, leave empty cells empty). " +
                "Write [غير واضح] where something cannot be read. Output only the transcription."
    }
}
