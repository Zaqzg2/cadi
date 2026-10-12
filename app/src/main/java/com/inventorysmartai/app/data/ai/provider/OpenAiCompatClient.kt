package com.inventorysmartai.app.data.ai.provider

import com.inventorysmartai.app.data.ai.AiConfig
import com.inventorysmartai.app.data.ai.provider.AiJson.asList
import com.inventorysmartai.app.data.ai.provider.AiJson.asObject
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class AiToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String,
    /**
     * Provider-specific extras that must travel back with the call in the next request. Gemini 3 puts its "thought signature"
     * here (`extra_content.google.thought_signature`) and answers 400 when a later request replays the call without it.
     */
    val extraContent: Map<String, Any?>? = null
)

data class AiChatResult(val text: String?, val toolCalls: List<AiToolCall>)

/**
 * One HTTP client for Gemini, Groq, Mistral and OpenRouter — all four speak the OpenAI `chat/completions`
 * dialect (Gemini through its OpenAI-compatibility endpoint). Mistral additionally has `POST /ocr`. The API key travels only
 * in the Authorization header (never in a URL, never logged: the app's logging interceptor runs at BASIC level, which
 * prints no headers).
 */
@Singleton
class OpenAiCompatClient @Inject constructor(private val baseClient: OkHttpClient) {

    private val client: OkHttpClient by lazy {
        baseClient.newBuilder().callTimeout(AiConfig.REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS).build()
    }

    suspend fun chat(
        provider: ActiveProvider,
        model: String,
        messages: List<Map<String, Any?>>,
        tools: List<Map<String, Any?>> = emptyList(),
        jsonMode: Boolean = false,
        temperature: Double = 0.2,
        maxTokens: Int? = null
    ): AiChatResult {
        val body = buildMap<String, Any?> {
            put("model", model)
            put("messages", messages)
            if (sendsTemperature(provider.id, model)) put("temperature", temperature)
            if (maxTokens != null) put("max_tokens", effectiveMaxTokens(provider.id, maxTokens))
            if (tools.isNotEmpty()) {
                put("tools", tools)
                put("tool_choice", "auto")
            }
            if (jsonMode) put("response_format", mapOf("type" to "json_object"))
            // Gemini thinks before answering by default, and those tokens come out of max_tokens; a light level keeps replies
            // quick and leaves room for the answer. Every Gemini model accepts "low".
            if (isGeminiModel(provider.id, model)) put("reasoning_effort", "low")
        }
        val root = try {
            post(provider, "chat/completions", body)
        } catch (e: AiProviderHttpException) {
            // Gemini's compatibility layer is the youngest of the four; if it refuses JSON mode, the prompt (which already
            // demands "JSON only") and the tolerant parser carry the request.
            if (jsonMode && provider.id == AiProviderId.GEMINI && e.code == 400) {
                post(provider, "chat/completions", body - "response_format")
            } else {
                throw e
            }
        }
        return parseChat(provider.id, root)
    }

    /** Mistral OCR: returns the pages' Markdown joined in order. Throws for any other provider. */
    suspend fun ocr(provider: ActiveProvider, mimeType: String, bytes: ByteArray): String {
        val ocrModel = requireNotNull(provider.id.ocrModel) { "${provider.id} has no OCR endpoint" }
        val dataUri = "data:$mimeType;base64," + java.util.Base64.getEncoder().encodeToString(bytes)
        val document = if (mimeType == "application/pdf") {
            mapOf("type" to "document_url", "document_url" to dataUri)
        } else {
            mapOf("type" to "image_url", "image_url" to dataUri)
        }
        val root = post(provider, "ocr", mapOf("model" to ocrModel, "document" to document))
        val pages = root["pages"].asList().orEmpty()
        return pages.mapIndexedNotNull { index, page ->
            val markdown = page.asObject()?.get("markdown") as? String
            markdown?.takeIf { it.isNotBlank() }?.let { "[صفحة ${index + 1}]\n$it" }
        }.joinToString("\n\n")
    }

    /** A 1-token-ish request: validates the key AND the model id in one go. Returns the reply text. */
    suspend fun ping(provider: ActiveProvider): String =
        chat(
            provider = provider,
            model = provider.textModel,
            messages = listOf(mapOf("role" to "user", "content" to "ping")),
            maxTokens = 16
        ).text.orEmpty()

    // ---- model-specific request rules ----

    private fun isGeminiModel(provider: AiProviderId, model: String) = provider == AiProviderId.GEMINI && model.startsWith("gemini-")

    /** Google advises leaving the temperature at its default for Gemini 3 and newer; a low value can make them loop. */
    private fun sendsTemperature(provider: AiProviderId, model: String) = !(provider == AiProviderId.GEMINI && model.startsWith("gemini-3"))

    /** Gemini counts its thinking tokens inside max_tokens, so a tiny limit (the 16-token ping) would leave no room for an answer. */
    private fun effectiveMaxTokens(provider: AiProviderId, requested: Int): Int =
        if (provider == AiProviderId.GEMINI) maxOf(requested, GEMINI_MIN_OUTPUT_TOKENS) else requested

    // ---- plumbing ----

    /** One automatic retry when the provider says it is overloaded (503) — Gemini does that regularly on the free tier. */
    private suspend fun post(provider: ActiveProvider, path: String, body: Map<String, Any?>): Map<String, Any?> {
        try {
            return postOnce(provider, path, body)
        } catch (e: AiProviderHttpException) {
            if (e.code != 503) throw e
        }
        delay(OVERLOAD_RETRY_DELAY_MS)
        return postOnce(provider, path, body)
    }

    private suspend fun postOnce(provider: ActiveProvider, path: String, body: Map<String, Any?>): Map<String, Any?> {
        val request = Request.Builder()
            .url(provider.id.baseUrl + path)
            .header("Authorization", "Bearer ${provider.apiKey}")
            .header("Accept", "application/json")
            .apply { if (provider.id == AiProviderId.OPENROUTER) header("X-Title", "Inventory Smart AI") }
            .post(AiJson.toJson(body).toRequestBody(JSON))
            .build()

        return client.newCall(request).awaitResponse().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw AiProviderHttpException(provider.id, response.code, errorSummary(text))
            val root = AiJson.parseObject(text)
                ?: throw AiProviderEmptyException(provider.id)
            // OpenRouter can answer HTTP 200 with an {"error": {...}} document (upstream model failed).
            val error = root["error"].asObject()
            if (error != null && root["choices"] == null && root["pages"] == null) {
                val code = (error["code"] as? Number)?.toInt() ?: 502
                throw AiProviderHttpException(provider.id, code, (error["message"] as? String).orEmpty().take(300))
            }
            root
        }
    }

    private fun errorSummary(body: String): String {
        val message = runCatching {
            val parsed = AiJson.parse(body)
            // Gemini answers some errors as a JSON ARRAY: [{"error": {...}}].
            val root = (parsed as? List<*>)?.firstOrNull() ?: parsed
            val err = root.asObject()?.get("error")
            (err.asObject()?.get("message") as? String) ?: (err as? String)
        }.getOrNull()
        return (message ?: body).trim().take(300)
    }

    private fun parseChat(provider: AiProviderId, root: Map<String, Any?>): AiChatResult {
        val message = root["choices"].asList()?.firstOrNull().asObject()?.get("message").asObject()
            ?: throw AiProviderEmptyException(provider)

        val text = when (val content = message["content"]) {
            is String -> content
            is List<*> -> content.mapNotNull { it.asObject()?.get("text") as? String }.joinToString("")
            else -> null
        }?.takeIf { it.isNotBlank() }

        val calls = message["tool_calls"].asList().orEmpty().mapIndexedNotNull { index, raw ->
            val call = raw.asObject() ?: return@mapIndexedNotNull null
            val function = call["function"].asObject() ?: return@mapIndexedNotNull null
            val name = function["name"] as? String ?: return@mapIndexedNotNull null
            val arguments = when (val a = function["arguments"]) {
                is String -> a.ifBlank { "{}" }
                null -> "{}"
                else -> AiJson.toJson(a) // some providers return the object itself
            }
            AiToolCall(
                id = (call["id"] as? String)?.takeIf { it.isNotBlank() } ?: "call_${System.nanoTime()}_$index",
                name = name,
                argumentsJson = arguments,
                extraContent = call["extra_content"].asObject()
            )
        }
        if (text == null && calls.isEmpty()) throw AiProviderEmptyException(provider)
        return AiChatResult(text, calls)
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        const val GEMINI_MIN_OUTPUT_TOKENS = 1_024
        const val OVERLOAD_RETRY_DELAY_MS = 2_000L
    }
}
