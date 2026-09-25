package com.inventorysmartai.backend.assistant

import com.inventorysmartai.backend.gemini.GeminiClient
import com.inventorysmartai.backend.gemini.GeminiContent
import com.inventorysmartai.backend.gemini.GeminiFunctionResultInput
import com.inventorysmartai.backend.gemini.GeminiInteractionResult
import com.inventorysmartai.backend.gemini.ToolCatalog
import com.inventorysmartai.backend.gemini.ToolExecutionSite
import com.inventorysmartai.backend.gemini.ToolRisk
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.ConcurrentHashMap

data class ConversationState(val sessionId: String, val previousInteractionId: String?)

/**
 * Maps the app's own `conversationId` to Gemini's server-side `previous_interaction_id` pointer.
 * Deliberately in-memory only: losing an in-progress chat's thread on a backend restart is an
 * acceptable trade-off (the app can just start a new conversation) that this environment can
 * actually verify, unlike a database it has no way to run — contrast with [com.inventorysmartai.
 * backend.auth.TokenStore], where losing data (a Google refresh token) is NOT acceptable and so
 * that one is file-persisted. Swap for a real cache (Redis, etc.) if the backend ever runs as more
 * than one instance.
 */
class InMemoryConversationStore {
    private val map = ConcurrentHashMap<String, ConversationState>()
    private val mutex = Mutex()

    suspend fun get(conversationId: String): ConversationState? = map[conversationId]

    suspend fun put(conversationId: String, state: ConversationState) = mutex.withLock {
        map[conversationId] = state
    }
}

data class PendingToolCall(
    val callId: String,
    val name: String,
    val arguments: JsonObject,
    val site: ToolExecutionSite,
    val risk: ToolRisk
)

sealed class AssistantTurnResult {
    data class Final(val text: String) : AssistantTurnResult()
    data class ToolCallsRequested(val calls: List<PendingToolCall>) : AssistantTurnResult()
}

/**
 * Drives one Gemini Interactions API conversation. Every public method here corresponds 1:1 to a
 * route in routes/AssistantRoutes.kt — see that file for the full request/response contract and
 * for why local vs. backend tool calls are split the way they are (Room only exists on-device).
 */
class AssistantOrchestrator(
    private val geminiClient: GeminiClient,
    private val conversations: InMemoryConversationStore
) {
    suspend fun sendUserMessage(
        conversationId: String,
        sessionId: String,
        message: String,
        contextText: String?
    ): AssistantTurnResult {
        val state = conversations.get(conversationId)
        val previousId = state?.previousInteractionId

        val parts = buildList {
            if (previousId == null) add(GeminiContent.text(SYSTEM_PROMPT))
            contextText?.takeIf { it.isNotBlank() }?.let { add(GeminiContent.text("سياق إضافي متاح للمحادثة:\n$it")) }
            add(GeminiContent.text(message))
        }

        val result = geminiClient.createInteraction(
            input = GeminiContent.array(parts),
            tools = ToolCatalog.asGeminiTools(),
            previousInteractionId = previousId,
            store = true
        )
        conversations.put(conversationId, ConversationState(sessionId, result.interactionId))
        return toTurnResult(result)
    }

    /** [results] are results the APP computed itself for one or more [ToolExecutionSite.LOCAL]
     *  tool calls from the previous turn (read-only tools execute silently; [ToolCatalog.
     *  writeToolNames] ones only after the user confirmed and the app performed the write against
     *  Room — e.g. createPurchaseRequest). */
    suspend fun continueWithResults(conversationId: String, results: List<GeminiFunctionResultInput>): AssistantTurnResult {
        val state = conversations.get(conversationId)
            ?: throw IllegalStateException("Unknown conversationId: $conversationId — call sendUserMessage first")
        val previousId = state.previousInteractionId
            ?: throw IllegalStateException("Conversation $conversationId has no interaction to continue")

        val result = geminiClient.sendFunctionResults(
            results = results,
            tools = ToolCatalog.asGeminiTools(),
            previousInteractionId = previousId
        )
        conversations.put(conversationId, state.copy(previousInteractionId = result.interactionId))
        return toTurnResult(result)
    }

    suspend fun sessionIdFor(conversationId: String): String? = conversations.get(conversationId)?.sessionId

    private fun toTurnResult(result: GeminiInteractionResult): AssistantTurnResult {
        if (result.functionCalls.isEmpty()) return AssistantTurnResult.Final(result.outputText.orEmpty())
        val calls = result.functionCalls.mapNotNull { call ->
            val definition = ToolCatalog.find(call.name) ?: return@mapNotNull null // unknown tool name — drop rather than crash
            PendingToolCall(call.id, call.name, call.arguments, definition.site, definition.risk)
        }
        // Every requested call resolved to a known tool and there's at least one: hand them back
        // to the app. If somehow all resolved to nothing (a tool name Gemini invented despite the
        // declared catalog), fall back to whatever text accompanied the call rather than silently
        // dropping the turn.
        return if (calls.isNotEmpty()) AssistantTurnResult.ToolCallsRequested(calls)
        else AssistantTurnResult.Final(result.outputText.orEmpty())
    }

    private companion object {
        val SYSTEM_PROMPT = """
            أنت "المساعد الذكي" داخل تطبيق Inventory Smart AI لإدارة المخزون. أجب دائمًا بالعربية
            الفصحى المبسّطة.
            استخدم الأدوات المتاحة (tools) للحصول على أي بيانات فعلية عن المنتجات أو المخزون أو
            المبيعات أو طلبات الشراء أو الأهداف أو الفروع — لا تخترع أرقامًا أو أسماء أصناف أو
            نتائج من عندك أبدًا. إن لم تتوفر أداة مناسبة لسؤال المستخدم، وضّح ذلك بصراحة بدلاً من
            الافتراض.
            إذا طلب المستخدم إجراءً يكتب بيانات جديدة أو يرسل شيئًا (طلب شراء، حفظ تقرير، إنشاء
            مستند Google Doc، بريد إلكتروني، أو موعد في التقويم)، استدعِ الأداة المناسبة مباشرة —
            سيتولى التطبيق عرض تأكيد صريح على المستخدم قبل أي تنفيذ فعلي، فلا داعي لأن تطلب أنت
            التأكيد نصيًا قبل استدعاء الأداة.
            عند تقديم تحليل أو توصية (تحليل مخزون منخفض، توصية شراء، تحليل تقدم هدف)، اذكر بوضوح
            أنها توصية أو تحليل من الذكاء الاصطناعي وليست حقيقة نهائية مؤكدة.
        """.trimIndent()
    }
}
