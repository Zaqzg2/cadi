package com.inventorysmartai.app.data.ai.provider

import com.inventorysmartai.app.data.ai.AiConfig
import com.inventorysmartai.app.data.ai.AiDocumentInput
import com.inventorysmartai.app.data.ai.AiFileTooLargeException
import com.inventorysmartai.app.data.ai.AiInvalidOutputException
import com.inventorysmartai.app.data.ai.ExtractionSchemas
import com.inventorysmartai.app.domain.importing.ai.AiExtractionDocument
import com.inventorysmartai.app.domain.importing.ai.AiExtractionDocumentType
import com.squareup.moshi.Moshi
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Document -> [AiExtractionDocument] using only the user's own provider keys.
 *
 * Arabic reading quality is the weak point of free vision models, so the pipeline has two stages:
 *  1. READ   — Mistral OCR turns the PDF/photo into Markdown text (when a Mistral key exists and the
 *              "OCR first" switch is on).
 *  2. STRUCTURE — any enabled provider (priority order, automatic fallback on rate-limit/outage) turns that
 *              text into the app's JSON shape.
 * If there is no OCR text (no Mistral key, OCR failed), every provider is tried with the page IMAGE(S)
 * directly (vision). Whatever comes back is only ever a *draft*: it still goes through the existing
 * normaliser/validator/matcher and the human review screen before anything is saved.
 */
@Singleton
class DirectDocumentExtractor @Inject constructor(
    private val client: OpenAiCompatClient,
    private val settings: AiProviderSettings,
    private val rasterizer: PdfRasterizer,
    private val moshi: Moshi
) {

    suspend fun extract(
        fileBytes: ByteArray,
        declaredMimeType: String,
        documentType: AiExtractionDocumentType
    ): AiExtractionDocument {
        val providers = settings.activeProviders()
        if (providers.isEmpty()) throw AiNoProviderConfiguredException()
        if (fileBytes.size > AiConfig.MAX_INLINE_BYTES) throw AiFileTooLargeException(fileBytes.size, AiConfig.MAX_INLINE_BYTES)

        val mimeType = AiDocumentInput.resolveMimeType(fileBytes, declaredMimeType)
        val instruction = buildInstruction(documentType)
        var lastError: Throwable? = null

        // ---- Stage 1: read (Mistral OCR) ----
        val mistral = providers.firstOrNull { it.id == AiProviderId.MISTRAL }
        var ocrText: String? = null
        if (mistral != null && settings.current().preferMistralOcr) {
            try {
                ocrText = readWithMistralOcr(mistral, fileBytes, mimeType)?.takeIf { it.isNotBlank() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }

        // ---- Stage 2a: structure the OCR text ----
        if (ocrText != null) {
            val user = "نص المستند بعد القراءة الضوئية (OCR):\n\n" + ocrText.take(MAX_OCR_CHARS)
            for (provider in providers) {
                try {
                    return structure(provider, provider.textModel, listOf(system(instruction), mapOf("role" to "user", "content" to user)), documentType)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                }
            }
        }

        // ---- Stage 2b: vision on the page images ----
        val images = try {
            rasterizer.prepare(fileBytes, mimeType, MAX_VISION_PAGES)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw lastError ?: e
        }
        val imageParts = images.map {
            mapOf(
                "type" to "image_url",
                "image_url" to mapOf("url" to "data:${it.mimeType};base64," + Base64.getEncoder().encodeToString(it.bytes))
            )
        }
        val userContent = listOf(mapOf("type" to "text", "text" to "اقرأ المستند المرفق (الصفحات بالترتيب) وأخرج JSON فقط.")) + imageParts
        for (provider in providers) {
            try {
                return structure(provider, provider.visionModel, listOf(system(instruction), mapOf("role" to "user", "content" to userContent)), documentType)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: AiInvalidOutputException("No provider produced a result")
    }

    private suspend fun readWithMistralOcr(mistral: ActiveProvider, bytes: ByteArray, mimeType: String): String? {
        // Mistral OCR reads PDF, JPEG, PNG and WebP; a HEIC photo is converted first.
        if (mimeType == "application/pdf" || mimeType in OCR_IMAGE_TYPES) return client.ocr(mistral, mimeType, bytes)
        val converted = rasterizer.prepare(bytes, mimeType, 1).firstOrNull() ?: return null
        return client.ocr(mistral, converted.mimeType, converted.bytes)
    }

    private suspend fun structure(
        provider: ActiveProvider,
        model: String,
        messages: List<Map<String, Any?>>,
        documentType: AiExtractionDocumentType
    ): AiExtractionDocument {
        val reply = client.chat(
            provider = provider,
            model = model,
            messages = messages,
            jsonMode = true,
            temperature = 0.0,
            maxTokens = MAX_OUTPUT_TOKENS
        )
        return parseDocument(reply.text.orEmpty(), documentType)
    }

    internal fun parseDocument(raw: String, documentType: AiExtractionDocumentType): AiExtractionDocument {
        val json = AiJson.extractObjectText(AiDocumentInput.stripCodeFence(raw))
            ?: throw AiInvalidOutputException("Model answer contained no JSON object")
        val parsed = try {
            moshi.adapter(AiExtractionDocument::class.java).fromJson(json)
        } catch (e: Exception) {
            throw AiInvalidOutputException("Model JSON did not match the extraction schema: ${e.message}")
        } ?: throw AiInvalidOutputException("Model returned an empty JSON document")
        return if (parsed.documentType.isBlank()) parsed.copy(documentType = documentType.name) else parsed
    }

    private fun system(instruction: String): Map<String, Any?> = mapOf("role" to "system", "content" to instruction)

    private fun buildInstruction(documentType: AiExtractionDocumentType): String {
        val schema = JsonSchemaConverter.toSchemaText(ExtractionSchemas.forDocumentType(documentType))
        return buildString {
            append(ExtractionSchemas.instructionFor(documentType))
            append("\n\nThe document is usually in Arabic; keep Arabic text and digits exactly as written.")
            append("\nReturn ONLY one JSON object — no Markdown fence, no commentary — that conforms to this JSON Schema:\n")
            append(schema)
        }
    }

    private companion object {
        const val MAX_OCR_CHARS = 40_000
        const val MAX_VISION_PAGES = 3 // Groq accepts at most 3 images per request
        const val MAX_OUTPUT_TOKENS = 8_000
        val OCR_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    }
}
