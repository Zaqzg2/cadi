package com.inventorysmartai.app.data.backend

import com.inventorysmartai.app.data.ai.AiConfig
import com.inventorysmartai.app.data.ai.provider.AiChatResult
import com.inventorysmartai.app.data.ai.provider.AiJson
import com.inventorysmartai.app.data.ai.provider.AiJson.asList
import com.inventorysmartai.app.data.ai.provider.AiJson.asObject
import com.inventorysmartai.app.data.ai.provider.AiToolCall
import com.inventorysmartai.app.data.remote.BackendFailure
import com.inventorysmartai.app.data.remote.backendFailureFor
import com.inventorysmartai.app.data.remote.dto.ErrorDetailDto
import com.inventorysmartai.app.data.remote.dto.ErrorResponseDto
import com.inventorysmartai.app.domain.repository.DeviceSessionRepository
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

/**
 * POST /v1/ai/chat — the assistant's model call, made by the app's own server. The server tries Mistral, Groq and
 * OpenRouter in turn with ITS keys, so nothing secret is on the phone and no per-phone setup is needed.
 *
 * Failures come out as [BackendFailure] with the server's own Arabic message (rate limit, bad key on the server, daily cap,
 * server asleep ...), so the screens above show a specific reason instead of a generic one.
 */
@Singleton
class BackendChatGateway @Inject constructor(
    baseClient: OkHttpClient,
    private val sessionRepository: DeviceSessionRepository
) : AiChatGateway {

    private val client: OkHttpClient by lazy {
        baseClient.newBuilder().callTimeout(AiConfig.BACKEND_REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS).build()
    }

    override suspend fun chat(
        messages: List<Map<String, Any?>>,
        tools: List<Map<String, Any?>>,
        temperature: Double,
        maxTokens: Int
    ): AiChatResult {
        BackendConfig.configurationProblem()?.let { throw BackendFailure.Structured("BACKEND_NOT_CONFIGURED", it) }

        val body = buildMap<String, Any?> {
            put("messages", messages)
            if (tools.isNotEmpty()) put("tools", tools)
            put("temperature", temperature)
            put("maxTokens", maxTokens)
            put("sessionId", sessionRepository.getSessionId())
        }
        val request = Request.Builder()
            .url(BackendConfig.baseUrlNoSlash + "/v1/ai/chat")
            .header("Accept", "application/json")
            .post(AiJson.toJson(body).toRequestBody(JSON))
            .build()

        val response = try {
            client.newCall(request).await()
        } catch (e: IOException) {
            throw BackendFailure.NetworkUnavailable(e)
        }
        response.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw failureFor(it.code, text)
            return parse(text)
        }
    }

    private fun parse(text: String): AiChatResult {
        val root = AiJson.parseObject(text)
            ?: throw BackendFailure.Structured("AI_INVALID_OUTPUT", "ردّ الخادم غير مفهوم. حاول مرة أخرى.")
        val message = root["message"].asObject()
            ?: throw BackendFailure.Structured("AI_INVALID_OUTPUT", "ردّ الخادم غير مفهوم. حاول مرة أخرى.")

        val content = (message["content"] as? String)?.takeIf { it.isNotBlank() }
        val calls = message["tool_calls"].asList().orEmpty().mapIndexedNotNull { index, raw ->
            val call = raw.asObject() ?: return@mapIndexedNotNull null
            val function = call["function"].asObject() ?: return@mapIndexedNotNull null
            val name = function["name"] as? String ?: return@mapIndexedNotNull null
            val arguments = (function["arguments"] as? String)?.ifBlank { "{}" } ?: "{}"
            AiToolCall(
                id = (call["id"] as? String)?.takeIf { it.isNotBlank() } ?: "call_${System.nanoTime()}_$index",
                name = name,
                argumentsJson = arguments
            )
        }
        if (content == null && calls.isEmpty()) {
            throw BackendFailure.Structured("AI_INVALID_OUTPUT", "لم يُرجع الذكاء الاصطناعي ردًّا. أعد المحاولة.")
        }
        return AiChatResult(content, calls)
    }

    /** The server's `{"error":{"code","message"}}` when present (see data/remote/BackendErrors.kt for the fallback texts). */
    private fun failureFor(httpCode: Int, body: String): BackendFailure {
        val error = runCatching { AiJson.parseObject(body)?.get("error").asObject() }.getOrNull()
        val code = error?.get("code") as? String
        val message = error?.get("message") as? String
        val parsed = if (code != null && message != null) ErrorResponseDto(ErrorDetailDto(code, message)) else null
        return backendFailureFor(httpCode, parsed)
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
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
}
