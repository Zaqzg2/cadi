package com.inventorysmartai.app.data.ai.provider

import com.inventorysmartai.app.data.ai.AiConfig
import com.inventorysmartai.app.data.ai.AiDocumentInput
import com.inventorysmartai.app.data.ai.AiFileTooLargeException
import com.inventorysmartai.app.data.ai.AiInvalidOutputException
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Photo / PDF -> plain text (tables as Markdown) with only the person's own keys. This is what lets the assistant "look at" a
 * picture or PDF attached to the chat when the app's backend is not in use:
 *  1. an OCR engine (Mistral OCR, then OCR.space — see [DirectOcrReader]) when a key for one exists and "OCR first" is on;
 *  2. otherwise (or when OCR fails) a vision model transcribes the page images — Gemini reads Arabic photos well.
 */
@Singleton
class DirectDocumentReader @Inject constructor(
    private val client: OpenAiCompatClient,
    private val ocrReader: DirectOcrReader,
    private val settings: AiProviderSettings,
    private val rasterizer: PdfRasterizer
) {

    suspend fun read(bytes: ByteArray, declaredMimeType: String): String {
        val providers = settings.activeProviders()
        val ocrSpaceKey = settings.activeOcrSpace()
        if (providers.isEmpty() && ocrSpaceKey == null) throw AiNoProviderConfiguredException()
        if (bytes.size > AiConfig.MAX_INLINE_BYTES) throw AiFileTooLargeException(bytes.size, AiConfig.MAX_INLINE_BYTES)

        val mimeType = AiDocumentInput.resolveMimeType(bytes, declaredMimeType)
        var lastError: Throwable? = null

        // ---- 1. OCR engines ----
        val mistral = providers.firstOrNull { it.id == AiProviderId.MISTRAL }
        if (settings.current().preferOcr && (mistral != null || ocrSpaceKey != null)) {
            val outcome = ocrReader.read(mistral, ocrSpaceKey, bytes, mimeType)
            outcome.text?.let { return it }
            lastError = outcome.error
        }

        // ---- 2. a vision model reads the pages ----
        if (providers.isEmpty()) throw lastError ?: AiNoProviderConfiguredException()
        val images = try {
            rasterizer.prepare(bytes, mimeType, MAX_VISION_PAGES)
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
        val userContent = listOf(mapOf("type" to "text", "text" to "انسخ نص المستند المرفق (الصفحات بالترتيب).")) + imageParts
        val messages = listOf(
            mapOf("role" to "system", "content" to SYSTEM_PROMPT),
            mapOf("role" to "user", "content" to userContent)
        )
        for (provider in providers) {
            try {
                val reply = client.chat(
                    provider = provider,
                    model = provider.visionModel,
                    messages = messages,
                    temperature = 0.0,
                    maxTokens = MAX_OUTPUT_TOKENS
                )
                reply.text?.takeIf { it.isNotBlank() }?.let { return it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: AiInvalidOutputException("No provider could read the file")
    }

    private companion object {
        const val MAX_VISION_PAGES = 3 // Groq accepts at most 3 images per request
        const val MAX_OUTPUT_TOKENS = 6_000
        const val SYSTEM_PROMPT =
            "You read business documents (invoices, price lists, stock sheets, target sheets), usually in Arabic. " +
                "Transcribe ALL the visible text faithfully, in reading order, keeping Arabic text and numbers exactly as written. " +
                "Render tables as Markdown tables. Output only the transcription, with no commentary."
    }
}
