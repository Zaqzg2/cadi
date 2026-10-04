package com.inventorysmartai.app.data.assistant

import com.inventorysmartai.app.data.ai.provider.AiProviderSettings
import com.inventorysmartai.app.domain.assistant.AssistantContext
import com.inventorysmartai.app.domain.assistant.AssistantStepResult
import com.inventorysmartai.app.domain.repository.AssistantRepository
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the assistant screen talks to. A conversation is pinned to ONE implementation for its whole life,
 * because its state lives in different places (on the phone vs. Gemini's server-side interaction):
 *  - at least one provider key saved when the conversation starts -> [DirectAssistantRepositoryImpl];
 *  - otherwise -> the backend-routed [AssistantRepositoryImpl], exactly as before.
 * Deleting the backend later means binding [DirectAssistantRepositoryImpl] directly and deleting this class.
 */
@Singleton
class RoutingAssistantRepository @Inject constructor(
    private val direct: DirectAssistantRepositoryImpl,
    private val backend: AssistantRepositoryImpl, // remove with the backend
    private val settings: AiProviderSettings
) : AssistantRepository {

    private val owner = ConcurrentHashMap<String, AssistantRepository>()

    override suspend fun sendMessage(conversationId: String, message: String, context: AssistantContext?): AssistantStepResult {
        val repository = owner[conversationId] ?: (if (settings.hasAnyActive()) direct else backend).also { owner[conversationId] = it }
        return repository.sendMessage(conversationId, message, context)
    }

    override suspend fun confirmPendingAction(conversationId: String, approved: Boolean): AssistantStepResult =
        (owner[conversationId] ?: backend).confirmPendingAction(conversationId, approved)
}
