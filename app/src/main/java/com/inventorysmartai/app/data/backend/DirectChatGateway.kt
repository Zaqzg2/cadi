package com.inventorysmartai.app.data.backend

import com.inventorysmartai.app.data.ai.AiConfig
import com.inventorysmartai.app.data.ai.AiTimeoutException
import com.inventorysmartai.app.data.ai.provider.AiChatResult
import com.inventorysmartai.app.data.ai.provider.AiNoProviderConfiguredException
import com.inventorysmartai.app.data.ai.provider.AiProviderSettings
import com.inventorysmartai.app.data.ai.provider.OpenAiCompatClient
import com.inventorysmartai.app.data.ai.provider.OpenAiMessageSanitizer
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * The OPTIONAL no-backend route: the assistant's model call goes straight from the phone to Groq / Mistral / OpenRouter with
 * the keys the person saved in Settings, trying them in their priority order until one answers.
 */
@Singleton
class DirectChatGateway @Inject constructor(
    private val client: OpenAiCompatClient,
    private val settings: AiProviderSettings
) : AiChatGateway {

    override suspend fun chat(
        messages: List<Map<String, Any?>>,
        tools: List<Map<String, Any?>>,
        temperature: Double,
        maxTokens: Int
    ): AiChatResult {
        val providers = settings.activeProviders()
        if (providers.isEmpty()) throw AiNoProviderConfiguredException()

        var lastError: Throwable? = null
        val result = withTimeoutOrNull(AiConfig.REQUEST_TIMEOUT_MS * 2) {
            for (provider in providers) {
                try {
                    return@withTimeoutOrNull client.chat(
                        provider = provider,
                        model = provider.textModel,
                        messages = OpenAiMessageSanitizer.forProvider(provider.id, messages),
                        tools = tools,
                        temperature = temperature,
                        maxTokens = maxTokens
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                }
            }
            null
        }
        return result ?: throw (lastError ?: AiTimeoutException())
    }
}
