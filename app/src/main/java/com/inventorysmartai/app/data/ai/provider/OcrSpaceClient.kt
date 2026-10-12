package com.inventorysmartai.app.data.ai.provider

import com.inventorysmartai.app.data.ai.AiConfig
import com.inventorysmartai.app.data.ai.provider.AiJson.asList
import com.inventorysmartai.app.data.ai.provider.AiJson.asObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * OCR.space (https://ocr.space/ocrapi): a plain OCR service — a photo or PDF page in, its text out. It is not a chat model, so it
 * never takes part in the assistant; it only does the READ step of a document analysis, and a text provider (Gemini, ...) then
 * turns that text into the app's JSON.
 *
 * The free plan accepts files up to 1 MB and reads at most 3 PDF pages, so callers send shrunk page images
 * (see PdfRasterizer.prepareForOcr). The key travels only in the `apikey` header, never in a URL and never logged.
 */
@Singleton
class OcrSpaceClient @Inject constructor(private val baseClient: OkHttpClient) {

    private val client: OkHttpClient by lazy {
        baseClient.newBuilder().callTimeout(AiConfig.OCR_REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS).build()
    }

    /**
     * Reads one page image (JPEG or PNG, at most [AiConfig.OCR_SPACE_MAX_BYTES]) and returns its text — blank for a page with
     * no text. Engine 3 goes first: it is the one that covers Arabic (plus tables, returned as Markdown). When it fails — it is
     * the slow one and has a smaller monthly quota — the older engine 1 with the Arabic language pack gets a try.
     */
    suspend fun read(ocr: ActiveOcrSpace, image: PreparedImage): String {
        try {
            return readWith(ocr, image, ENGINE_3)
        } catch (e: CancellationException) {
            throw e
        } catch (e: OcrSpaceException) {
            if (e.kind == OcrSpaceException.Kind.INVALID_KEY || e.kind == OcrSpaceException.Kind.TOO_LARGE) throw e
        } catch (e: IOException) {
            // a timeout or dropped connection on the slow engine: the quick one may still answer
        }
        return readWith(ocr, image, ENGINE_1)
    }

    /** The settings screen's "test" button: a tiny image with the words INVOICE 2026 proves the key and the service work. */
    suspend fun ping(ocr: ActiveOcrSpace) {
        read(ocr, PreparedImage(java.util.Base64.getDecoder().decode(TEST_PNG_BASE64), "image/png"))
    }

    private suspend fun readWith(ocr: ActiveOcrSpace, image: PreparedImage, engine: Int): String {
        val form = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                if (image.mimeType == "image/png") "page.png" else "page.jpg",
                image.bytes.toRequestBody(image.mimeType.toMediaType())
            )
            .addFormDataPart("OCREngine", engine.toString())
        if (engine == ENGINE_1) {
            form.addFormDataPart("language", "ara")
                .addFormDataPart("isTable", "true")
                .addFormDataPart("scale", "true")
                .addFormDataPart("detectOrientation", "true")
        }
        val request = Request.Builder()
            .url(ENDPOINT)
            .header("apikey", ocr.apiKey)
            .header("Accept", "application/json")
            .post(form.build())
            .build()

        return client.newCall(request).awaitResponse().use { response ->
            OcrSpaceResponse.parse(response.code, response.body?.string().orEmpty())
        }
    }

    private companion object {
        const val ENDPOINT = "https://api.ocr.space/parse/image"
        const val ENGINE_1 = 1
        const val ENGINE_3 = 3

        // 420x110 black-and-white PNG (682 bytes) reading "INVOICE 2026".
        const val TEST_PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAaQAAABuAQAAAABAM1r+AAACcUlEQVR42u2XP27UQBSHPzuOdgoku6CgWpsuosoBgtbcIKKizDFS" +
        "EDIikbjGlnRwg0wS7kBKZ9OkQGKCImTC7DyK8a6zu97wp8XT2LOj783v5/fe2BsJfz9uYv5l9FRP9VRP9VRP9VRPwSuAn83EawS4" +
        "gC8rmIikdiTpO/F5jfhczEAMGyImlWogDhEOZWHYsFcF4ItrCqC8C/HKmspxi54y7lRow2T83sJ0cxREbDpePMfuGrdlOxWmkp6L" +
        "G234gc/r3B6ei4jPT2VQyaXLaxl0KgTAjR5Fr4E9ZYLgXJ5kehLbH+hOhfthklFCrZPGF6BI5vpXqB0YwpuCIdwQVwCOzBeJMREx" +
        "O+vzVTvo/FqseNxJDWeaMpjMJAUzCmBr3V4p0ew2ypoVB1UBydtuXymArQGNaZK+CZQxu3ByWD5UhxqlSdrP25DQzIcwK5TSKD1p" +
        "zdDWGR5e2z/qlGmUtWvF9KjupBJzT1hIA2BCySwpWNzLLHAKOA58Ga+pjbiahwZgQyyICv2gw35rfKlG2Gz4DIBLErqffGSJK41G" +
        "3YvrCvBw9ptzQ0Gt27h1iYln7jqpfSKyeaMWjaGk9EXjrpMq4evy0iUY8NmKqzkVzBhuwM+Wzmbu3AO+dBESVuCDwmOoHHUJfOqm" +
        "hvCduGLSNqYoYiitXu8rBUosBjLtSgC/DbbmCoYPVFRJdCvHoMbX8/bEagOy8hxbysHBSwXJ1VhPo4Tb04hvTzmIsuxZ1k2FYHsf" +
        "tyH2R/PVkxhAXRSdZ6+4VCqR8HbQqZimfqtUIPXI0tm7+MMHEZG7du7D5fMyFfX/l3uqp3qqp3qqp/5L6heSplCwRrAHdQAAAABJ" +
        "RU5ErkJggg=="
    }
}

/** Turns one OCR.space answer into text, or into an [OcrSpaceException]. Pure, so it is unit-tested on the plain JVM. */
internal object OcrSpaceResponse {

    fun parse(httpCode: Int, body: String): String {
        val root = runCatching { AiJson.parseObject(body) }.getOrNull()
        val message = errorMessage(root)
        if (httpCode !in 200..299) throw failure(httpCode, message.ifBlank { body.trim().take(200) })
        if (root == null) throw OcrSpaceException(OcrSpaceException.Kind.FAILED, "unreadable answer")

        val results = root["ParsedResults"].asList().orEmpty().mapNotNull { it.asObject() }
        val text = results.mapNotNull { it["ParsedText"] as? String }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")
        if (text.isNotEmpty()) return text // a partly read PDF may also say "errored": the text that came back wins

        if (root["IsErroredOnProcessing"] == true || results.isEmpty()) throw failure(httpCode, message.ifBlank { "no result" })
        return "" // the page simply has no text
    }

    private fun flatten(value: Any?): List<String> = when (value) {
        is String -> listOf(value)
        is List<*> -> value.flatMap { flatten(it) }
        else -> emptyList()
    }

    /** OCR.space reports errors as a string or as a list of strings, at the top level and per page. */
    private fun errorMessage(root: Map<String, Any?>?): String {
        if (root == null) return ""
        val perPage = root["ParsedResults"].asList().orEmpty().flatMap { page ->
            flatten(page.asObject()?.get("ErrorMessage")) + flatten(page.asObject()?.get("ErrorDetails"))
        }
        return (flatten(root["ErrorMessage"]) + flatten(root["ErrorDetails"]) + perPage)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(" | ")
            .take(300)
    }

    private fun failure(httpCode: Int, message: String): OcrSpaceException {
        val lower = message.lowercase()
        val kind = when {
            httpCode == 413 ||
                (lower.contains("size") && (lower.contains("limit") || lower.contains("exceed") || lower.contains("large") || lower.contains("maximum"))) ->
                OcrSpaceException.Kind.TOO_LARGE
            httpCode == 429 || lower.contains("exceed") || lower.contains("too many") || lower.contains("quota") ||
                lower.contains("rate limit") || lower.contains("maximum number") ->
                OcrSpaceException.Kind.QUOTA
            httpCode == 401 || httpCode == 403 || lower.contains("api key") || lower.contains("apikey") || lower.contains("unauthor") ->
                OcrSpaceException.Kind.INVALID_KEY
            else -> OcrSpaceException.Kind.FAILED
        }
        return OcrSpaceException(kind, message.ifBlank { "HTTP $httpCode" })
    }
}
