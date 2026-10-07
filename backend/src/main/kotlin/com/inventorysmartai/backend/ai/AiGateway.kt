package com.inventorysmartai.backend.ai

import com.inventorysmartai.backend.config.AiProviderConfig
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

/**
 * Tries the configured providers in priority order and moves on to the next one when a provider is rate-limited, down,
 * slow, or answers badly. A provider that just failed "cools down" (a rate-limited key is not hammered again for a
 * minute, a rejected key for ten) so the next requests skip straight to a provider that is likely to work.
 *
 * This is the server-side twin of the Android app's DirectChatGateway — the difference is that the keys live here,
 * once, instead of in every phone.
 */
class AiGateway(
    val providers: List<AiProviderConfig>,
    private val transport: AiTransport,
    private val providerTimeoutMs: Long,
    private val totalTimeoutMs: Long,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val log = LoggerFactory.getLogger(AiGateway::class.java)

    private class Cooldown(val untilMs: Long, val failure: ProviderFailure)

    private val cooldowns = ConcurrentHashMap<String, Cooldown>()

    data class Attempted<T>(val value: T, val provider: AiProviderConfig, val model: String)

    data class ChatReply(
        val text: String?,
        val toolCalls: List<JsonObject>,
        val finishReason: String?,
        val providerId: String,
        val model: String
    )

    data class ProviderStatus(val id: String, val label: String, val ready: Boolean, val cooldownSeconds: Long, val lastFailure: String?)

    /**
     * Runs [block] against each provider until one returns. [block] must THROW to signal "try the next provider" —
     * including when it received an answer it cannot use (so an invalid-JSON reply also falls through).
     */
    suspend fun <T> attempt(vision: Boolean, block: suspend (AiProviderConfig, String) -> T): Attempted<T> {
        val failures = mutableListOf<ProviderFailure>()
        val deadline = clock() + totalTimeoutMs
        for (provider in providers) {
            val cooling = cooldowns[provider.id]
            if (cooling != null && cooling.untilMs > clock()) {
                val remainingSeconds = (cooling.untilMs - clock() + 999) / 1000
                failures += cooling.failure.copy(retryAfterSeconds = remainingSeconds)
                continue
            }
            val remaining = deadline - clock()
            if (remaining < MIN_ATTEMPT_MS) break
            val model = if (vision) provider.visionModel else provider.textModel
            try {
                val value = withTimeout(minOf(providerTimeoutMs, remaining)) { block(provider, model) }
                cooldowns.remove(provider.id)
                return Attempted(value, provider, model)
            } catch (e: TimeoutCancellationException) {
                fail(provider, ProviderFailure(provider.id, FailureKind.TIMEOUT, "no answer within the time limit"), failures)
            } catch (e: CancellationException) {
                throw e // the caller went away — never swallow cancellation
            } catch (e: Exception) {
                fail(provider, e.toFailure(provider.id), failures)
            }
        }
        throw AiUnavailableException(failures)
    }

    suspend fun chat(
        messages: List<JsonObject>,
        tools: List<JsonObject>,
        temperature: Double,
        maxTokens: Int?
    ): ChatReply {
        val attempted = attempt(vision = false) { provider, model ->
            val body = ChatParsing.buildBody(
                model = model,
                messages = MessageSanitizer.forProvider(provider.id, messages),
                tools = tools,
                jsonMode = false,
                temperature = temperature,
                maxTokens = maxTokens
            )
            ChatParsing.parse(provider.id, transport.chat(provider, body))
        }
        val parsed = attempted.value
        return ChatReply(parsed.text, parsed.toolCalls, parsed.finishReason, attempted.provider.id, attempted.model)
    }

    /** A tiny real request: proves a key AND a model id work. Used by GET /v1/status?verifyAi=true. */
    suspend fun ping(): ChatReply = chat(
        messages = listOf(JsonObject(mapOf("role" to JsonPrimitive("user"), "content" to JsonPrimitive("ping")))),
        tools = emptyList(),
        temperature = 0.0,
        maxTokens = 8
    )

    fun status(): List<ProviderStatus> {
        val now = clock()
        return providers.map { provider ->
            val cooling = cooldowns[provider.id]?.takeIf { it.untilMs > now }
            ProviderStatus(
                id = provider.id,
                label = provider.label,
                ready = cooling == null,
                cooldownSeconds = if (cooling == null) 0 else (cooling.untilMs - now + 999) / 1000,
                lastFailure = cooling?.failure?.kind?.name
            )
        }
    }

    private fun fail(provider: AiProviderConfig, failure: ProviderFailure, into: MutableList<ProviderFailure>) {
        into += failure
        val cooldownMs = when (failure.kind) {
            FailureKind.AUTH -> 10 * 60_000L
            FailureKind.RATE_LIMITED -> (failure.retryAfterSeconds ?: 60L).coerceIn(5L, 300L) * 1_000L
            FailureKind.UNAVAILABLE, FailureKind.TIMEOUT, FailureKind.NETWORK -> 20_000L
            FailureKind.REJECTED, FailureKind.EMPTY, FailureKind.INVALID_OUTPUT -> 0L
        }
        if (cooldownMs > 0) cooldowns[provider.id] = Cooldown(clock() + cooldownMs, failure)
        log.warn("AI provider {} failed: {} ({})", provider.id, failure.kind, failure.detail)
    }

    private companion object {
        const val MIN_ATTEMPT_MS = 1_000L
    }
}
