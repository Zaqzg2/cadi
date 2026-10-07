package com.inventorysmartai.app.data.backend

import com.inventorysmartai.app.data.ai.provider.AiChatResult
import com.inventorysmartai.app.data.ai.provider.AiProviderSettings
import com.inventorysmartai.app.data.remote.BackendFailure
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Picks the route for every assistant model call — the BACKEND is the default:
 *  1. The person turned on "use my own keys" AND saved at least one key  -> direct.
 *  2. Otherwise -> the backend.
 *  3. If the backend is not set up in this build, or it is unreachable / out of quota, and the person DID save keys of their
 *     own -> those keys answer instead (they chose to store them; without any, the backend's own error is shown).
 */
@Singleton
class RoutingChatGateway @Inject constructor(
    private val backend: BackendChatGateway,
    private val direct: DirectChatGateway,
    private val settings: AiProviderSettings
) : AiChatGateway {

    override suspend fun chat(
        messages: List<Map<String, Any?>>,
        tools: List<Map<String, Any?>>,
        temperature: Double,
        maxTokens: Int
    ): AiChatResult {
        val hasOwnKeys = settings.hasAnyActive()
        if (hasOwnKeys && settings.current().preferDirect) return direct.chat(messages, tools, temperature, maxTokens)
        if (hasOwnKeys && BackendConfig.configurationProblem() != null) return direct.chat(messages, tools, temperature, maxTokens)
        return try {
            backend.chat(messages, tools, temperature, maxTokens)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (hasOwnKeys && e.isBackendOutage()) direct.chat(messages, tools, temperature, maxTokens) else throw e
        }
    }
}

/**
 * True when the failure is about the BACKEND being unavailable (not the request being wrong), i.e. when answering with the
 * person's own keys instead is a sensible thing to try. Shared by the assistant and the document-import router.
 */
internal fun Throwable.isBackendOutage(): Boolean = when (this) {
    is BackendFailure.NetworkUnavailable -> true
    is BackendFailure.Structured -> code in OUTAGE_CODES || code.startsWith("BACKEND_HTTP_5")
    else -> false
}

private val OUTAGE_CODES = setOf(
    "BACKEND_NOT_CONFIGURED",
    "BACKEND_TIMEOUT",
    "UNAUTHORIZED",
    "RATE_LIMITED",
    "DAILY_CAP_REACHED",
    "AI_UNAVAILABLE",
    "AI_QUOTA_EXCEEDED",
    "AI_INVALID_KEY"
)
