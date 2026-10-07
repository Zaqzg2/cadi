package com.inventorysmartai.backend.ai

import com.inventorysmartai.backend.config.AiProviderConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.util.Base64

/** The two provider calls the server makes. An interface so tests can fake the network. */
interface AiTransport {
    /** POST {baseUrl}chat/completions. Returns the parsed JSON document; throws [ProviderHttpException] / [ProviderEmptyException]. */
    suspend fun chat(provider: AiProviderConfig, body: JsonObject): JsonObject

    /** Mistral OCR: the pages' Markdown joined in order ("" when nothing was read). */
    suspend fun ocr(provider: AiProviderConfig, mimeType: String, bytes: ByteArray): String
}

/**
 * Groq, Mistral and OpenRouter (and any custom endpoint) all speak the OpenAI `chat/completions` dialect, so one client
 * serves them all. The API key travels only in the Authorization header: it is never put in a URL and never logged.
 */
class KtorAiTransport(private val http: HttpClient) : AiTransport {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun chat(provider: AiProviderConfig, body: JsonObject): JsonObject = post(provider, "chat/completions", body)

    override suspend fun ocr(provider: AiProviderConfig, mimeType: String, bytes: ByteArray): String {
        val model = provider.ocrModel ?: throw IllegalArgumentException("${provider.id} has no OCR endpoint")
        val dataUri = "data:$mimeType;base64," + Base64.getEncoder().encodeToString(bytes)
        val document = buildJsonObject {
            if (mimeType == "application/pdf") {
                put("type", "document_url")
                put("document_url", dataUri)
            } else {
                put("type", "image_url")
                put("image_url", dataUri)
            }
        }
        val root = post(provider, "ocr", buildJsonObject {
            put("model", model)
            put("document", document)
        })
        val pages = root["pages"] as? JsonArray ?: return ""
        return pages.mapIndexedNotNull { index, page ->
            val markdown = ((page as? JsonObject)?.get("markdown") as? JsonPrimitive)?.contentOrNull
            markdown?.takeIf { it.isNotBlank() }?.let { "[صفحة ${index + 1}]\n$it" }
        }.joinToString("\n\n")
    }

    private suspend fun post(provider: AiProviderConfig, path: String, body: JsonObject): JsonObject {
        val response = http.post(provider.baseUrl + path) {
            header(HttpHeaders.Authorization, "Bearer ${provider.apiKey}")
            header(HttpHeaders.Accept, "application/json")
            if (provider.id == "openrouter") header("X-Title", "Inventory Smart AI")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val retryAfter = response.headers[HttpHeaders.RetryAfter]?.trim()?.toLongOrNull()
            throw ProviderHttpException(provider.id, response.status.value, errorSummary(text), retryAfter)
        }
        val root = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (e: Exception) {
            null
        } ?: throw ProviderEmptyException(provider.id)

        // OpenRouter can answer HTTP 200 with an {"error": {...}} document when the upstream model failed.
        val error = root["error"] as? JsonObject
        if (error != null && root["choices"] == null && root["pages"] == null) {
            val code = (error["code"] as? JsonPrimitive)?.intOrNull ?: 502
            val message = ((error["message"] as? JsonPrimitive)?.contentOrNull).orEmpty()
            throw ProviderHttpException(provider.id, code, message.take(300))
        }
        return root
    }

    private fun errorSummary(body: String): String {
        val message = try {
            val error = (json.parseToJsonElement(body) as? JsonObject)?.get("error")
            ((error as? JsonObject)?.get("message") as? JsonPrimitive)?.contentOrNull
                ?: (error as? JsonPrimitive)?.contentOrNull
        } catch (e: Exception) {
            null
        }
        return (message ?: body).trim().take(300)
    }
}
