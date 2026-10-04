package com.inventorysmartai.app.data.ai.provider

import com.inventorysmartai.app.data.ai.AiConfig
import com.inventorysmartai.app.data.ai.provider.AiJson.asList
import com.inventorysmartai.app.data.ai.provider.AiJson.asObject
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class AiToolCall(val id: String, val name: String, val argumentsJson: String)

data class AiChatResult(val text: String?, val toolCalls: List<AiToolCall>)

/**
 * One HTTP client for Groq, Mistral and OpenRouter — all three speak the OpenAI `chat/completions`
 * dialect. Mistral additionally has `POST /ocr`. The API key travels only in the Authorization header
 * (never in a URL, never logged: the app's logging interceptor runs at BASIC level, which prints no headers).
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
            put("temperature", temperature)
            if (maxTokens != null) put("max_tokens", maxTokens)
            if (tools.isNotEmpty()) {
                put("tools", tools)
                put("tool_choice", "auto")
            }
            if (jsonMode) put("response_format", mapOf("type" to "json_object"))
        }
        val root = post(provider, "chat/completions", body)
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

    // ---- plumbing ----

    private suspend fun post(provider: ActiveProvider, path: String, body: Map<String, Any?>): Map<String, Any?> {
        val request = Request.Builder()
            .url(provider.id.baseUrl + path)
            .header("Authorization", "Bearer ${provider.apiKey}")
            .header("Accept", "application/json")
            .apply { if (provider.id == AiProviderId.OPENROUTER) header("X-Title", "Inventory Smart AI") }
            .post(AiJson.toJson(body).toRequestBody(JSON))
            .build()

        return client.newCall(request).await().use { response ->
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
            val err = AiJson.parseObject(body)?.get("error")
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
            AiToolCall(id = (call["id"] as? String)?.takeIf { it.isNotBlank() } ?: "call_${System.nanoTime()}_$index", name = name, argumentsJson = arguments)
        }
        if (text == null && calls.isEmpty()) throw AiProviderEmptyException(provider)
        return AiChatResult(text, calls)
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) continuation.resume(response) else response.close()
            }
        })
        continuation.invokeOnCancellation { runCatching { cancel() } }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
