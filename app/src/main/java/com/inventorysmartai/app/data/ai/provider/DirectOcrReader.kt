package com.inventorysmartai.app.data.ai.provider

import com.inventorysmartai.app.data.ai.AiConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * The READ step of every direct-mode document job (import analysis and assistant attachments): a photo or PDF becomes text with
 * an OCR engine, using only the person's own keys. Engines are tried in this order and the first one that returns text wins:
 *  1. Mistral OCR — reads a PDF as it is (needs a Mistral key).
 *  2. OCR.space   — free; reads up to 3 shrunk page images (needs an OCR.space key).
 * Adding another engine later means adding one more block to [read]; nothing above this class changes.
 *
 * Never throws (except for cancellation): a failed engine just leaves its error in [Outcome.error], so the caller can fall back
 * to reading the page images with a vision model and, if that fails too, report the most useful error.
 */
@Singleton
class DirectOcrReader @Inject constructor(
    private val client: OpenAiCompatClient,
    private val ocrSpace: OcrSpaceClient,
    private val rasterizer: PdfRasterizer
) {
    class Outcome(val text: String?, val error: Throwable?)

    suspend fun read(mistral: ActiveProvider?, ocrSpaceKey: ActiveOcrSpace?, bytes: ByteArray, mimeType: String): Outcome {
        var lastError: Throwable? = null

        if (mistral != null) {
            try {
                readWithMistral(mistral, bytes, mimeType)?.takeIf { it.isNotBlank() }?.let { return Outcome(it, null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }

        if (ocrSpaceKey != null) {
            try {
                readWithOcrSpace(ocrSpaceKey, bytes, mimeType)?.takeIf { it.isNotBlank() }?.let { return Outcome(it, null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }
        return Outcome(null, lastError)
    }

    private suspend fun readWithMistral(mistral: ActiveProvider, bytes: ByteArray, mimeType: String): String? {
        // Mistral OCR reads PDF, JPEG, PNG and WebP; a HEIC photo is converted first.
        if (mimeType == PDF || mimeType in MISTRAL_IMAGE_TYPES) return client.ocr(mistral, mimeType, bytes)
        val converted = rasterizer.prepare(bytes, mimeType, 1).firstOrNull() ?: return null
        return client.ocr(mistral, converted.mimeType, converted.bytes)
    }

    /** One request per page, all at once; if any page fails the whole read counts as failed (a missing page would silently drop lines). */
    private suspend fun readWithOcrSpace(ocr: ActiveOcrSpace, bytes: ByteArray, mimeType: String): String? {
        val pages = rasterizer.prepareForOcr(bytes, mimeType, AiConfig.OCR_SPACE_MAX_PAGES, AiConfig.OCR_SPACE_MAX_BYTES)
        if (pages.isEmpty()) return null
        val texts = coroutineScope { pages.map { page -> async { ocrSpace.read(ocr, page) } }.awaitAll() }
        return texts.mapIndexedNotNull { index, text ->
            text.takeIf { it.isNotBlank() }?.let { "[صفحة ${index + 1}]\n$it" }
        }.joinToString("\n\n")
    }

    private companion object {
        const val PDF = "application/pdf"
        val MISTRAL_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    }
}
