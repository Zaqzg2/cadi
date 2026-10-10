package com.inventorysmartai.app.data.assistant

import com.inventorysmartai.app.data.ai.provider.AiChatResult
import com.inventorysmartai.app.data.ai.provider.AiJson
import com.inventorysmartai.app.data.ai.provider.AiToolCall
import com.inventorysmartai.app.data.ai.provider.OpenAiMessageSanitizer
import com.inventorysmartai.app.data.ai.provider.toProviderFailure
import com.inventorysmartai.app.data.assistant.files.AttachmentStore
import com.inventorysmartai.app.data.backend.AiChatGateway
import com.inventorysmartai.app.domain.assistant.AssistantContext
import com.inventorysmartai.app.domain.assistant.AssistantStepResult
import com.inventorysmartai.app.domain.assistant.AttachmentInfo
import com.inventorysmartai.app.domain.assistant.LocalToolExecutor
import com.inventorysmartai.app.domain.assistant.ToolExecutionSite
import com.inventorysmartai.app.domain.repository.AssistantRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * The smart assistant. The tool-calling loop runs on the phone — the tools read and write the local Room database, which
 * only exists here — and only the model call itself goes out, through [gateway]: the app's own backend by default, or the
 * person's own provider keys when they opted in (see data/backend/RoutingChatGateway.kt).
 *
 * Contract with the ViewModel: read-only tools run silently, a write tool (createPurchaseRequest, or one of the four Google
 * Workspace tools) stops with [AssistantStepResult.ConfirmationRequired] until the person answers, and the ViewModel only
 * ever sees Final / ConfirmationRequired / Error. The conversation is a neutral OpenAI-format list kept in memory (it is lost
 * when the app process dies) and sent in full with every turn, so the server needs no state of its own and a conversation
 * can carry on with a different provider — or a different route — after a rate limit.
 *
 * Files: the person can attach spreadsheets, Word files, photos and PDFs. They live in [attachments]; the model is told they
 * exist (a short note after the question) and reads them through the file tools. Files the assistant creates while answering
 * (a filled form, a dashboard) are collected there and handed back with the final answer.
 */
@Singleton
class AssistantRepositoryImpl @Inject constructor(
    private val gateway: AiChatGateway,
    private val localToolExecutor: LocalToolExecutor,
    private val attachments: AttachmentStore
) : AssistantRepository {

    /** One assistant message's tool calls, resolved one by one; OpenAI-format requires a reply for every id. */
    private class PendingTurn(
        val calls: List<AiToolCall>,
        var usedLocalData: Boolean
    ) {
        val results: MutableMap<String, String> = LinkedHashMap()
        var awaiting: AiToolCall? = null
    }

    private sealed interface Step {
        data object Done : Step
        data class NeedsConfirmation(val result: AssistantStepResult.ConfirmationRequired) : Step
    }

    private val histories = ConcurrentHashMap<String, MutableList<Map<String, Any?>>>()
    private val pending = ConcurrentHashMap<String, PendingTurn>()
    private val lock = Mutex()

    override suspend fun sendMessage(
        conversationId: String,
        message: String,
        context: AssistantContext?,
        attachmentIds: List<String>
    ): AssistantStepResult = lock.withLock { sendInternal(conversationId, message, context, attachmentIds) }

    override suspend fun attachFile(conversationId: String, reference: String): Result<AttachmentInfo> =
        attachments.add(conversationId, reference)

    override fun detachFile(conversationId: String, attachmentId: String) {
        attachments.remove(conversationId, attachmentId)
    }

    override suspend fun confirmPendingAction(conversationId: String, approved: Boolean): AssistantStepResult =
        lock.withLock { confirmInternal(conversationId, approved) }

    // ---- send ----

    private suspend fun sendInternal(
        conversationId: String,
        message: String,
        context: AssistantContext?,
        attachmentIds: List<String>
    ): AssistantStepResult {
        attachments.beginTurn(conversationId)
        val history = histories.getOrPut(conversationId) {
            mutableListOf(mapOf("role" to "system", "content" to AssistantToolCatalog.systemPrompt))
        }
        abandonPending(conversationId, history)
        // A failed earlier turn can leave the history ending on a tool result; Mistral rejects a user message right after one.
        if (history.last()["role"] == "tool") {
            history += mapOf("role" to "assistant", "content" to "تعذّر إكمال الرد السابق.")
        }

        val mark = history.size
        val question = context?.toPromptText()?.let { "سياق إضافي متاح للمحادثة:\n$it\n\n$message" } ?: message
        val note = attachments.promptNote(conversationId, attachmentIds)
        val content = if (note.isEmpty()) question else "$question\n\n$note"
        history += mapOf("role" to "user", "content" to content)

        return try {
            runLoop(conversationId, history, usedLocalData = false)
        } catch (e: CancellationException) {
            rollback(history, mark)
            attachments.takeTurnOutputs()
            throw e
        } catch (e: Exception) {
            rollback(history, mark)
            attachments.takeTurnOutputs()
            AssistantStepResult.Error(e.toProviderFailure().messageAr)
        }
    }

    /** A new message while a confirmation was still open: treat the open write as declined so the history stays valid. */
    private suspend fun abandonPending(conversationId: String, history: MutableList<Map<String, Any?>>) {
        val turn = pending.remove(conversationId) ?: return
        turn.awaiting?.let { turn.results[it.id] = DECLINED_RESULT_JSON }
        for (call in turn.calls) {
            if (call.id in turn.results) continue
            turn.results[call.id] = if (localToolExecutor.requiresConfirmation(call.name)) DECLINED_RESULT_JSON else runTool(call)
        }
        appendToolResults(history, turn)
    }

    private fun rollback(history: MutableList<Map<String, Any?>>, mark: Int) {
        while (history.size > mark) history.removeAt(history.lastIndex)
    }

    // ---- confirm ----

    private suspend fun confirmInternal(conversationId: String, approved: Boolean): AssistantStepResult {
        val turn = pending[conversationId]
        val call = turn?.awaiting
        val history = histories[conversationId]
        if (turn == null || call == null || history == null) {
            return AssistantStepResult.Error("لا يوجد إجراء بانتظار التأكيد لهذه المحادثة")
        }
        attachments.resumeTurn(conversationId)
        return try {
            turn.results[call.id] = if (approved) runTool(call).also { turn.usedLocalData = true } else DECLINED_RESULT_JSON
            turn.awaiting = null
            when (val step = advance(conversationId, history, turn)) {
                is Step.NeedsConfirmation -> step.result
                Step.Done -> runLoop(conversationId, history, turn.usedLocalData)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AssistantStepResult.Error(e.toProviderFailure().messageAr)
        }
    }

    // ---- the tool-calling loop ----

    private suspend fun runLoop(
        conversationId: String,
        history: MutableList<Map<String, Any?>>,
        usedLocalData: Boolean
    ): AssistantStepResult {
        var usedLocal = usedLocalData
        repeat(MAX_ROUNDS) {
            val reply = chatOnce(conversationId, history)
            if (reply.toolCalls.isEmpty()) {
                val text = reply.text.orEmpty()
                history += mapOf("role" to "assistant", "content" to text)
                shrinkBulkyFileResults(history)
                return AssistantStepResult.Final(text, usedLocal, attachments.takeTurnOutputs())
            }

            history += mapOf(
                "role" to "assistant",
                "content" to reply.text.orEmpty(),
                "tool_calls" to reply.toolCalls.map {
                    mapOf("id" to it.id, "type" to "function", "function" to mapOf("name" to it.name, "arguments" to it.argumentsJson))
                }
            )
            val turn = PendingTurn(reply.toolCalls, usedLocal)
            val step = advance(conversationId, history, turn)
            if (step is Step.NeedsConfirmation) return step.result
            usedLocal = turn.usedLocalData
        }
        return AssistantStepResult.Error("تعذّر إكمال الطلب بعد عدة محاولات، يرجى إعادة صياغة السؤال")
    }

    /** Runs every read-only call; stops at the first write call and asks the person. */
    private suspend fun advance(conversationId: String, history: MutableList<Map<String, Any?>>, turn: PendingTurn): Step {
        for (call in turn.calls) {
            if (call.id in turn.results) continue
            if (localToolExecutor.requiresConfirmation(call.name)) {
                turn.awaiting = call
                pending[conversationId] = turn
                val description = localToolExecutor.describeForConfirmation(call.name, call.argumentsJson)
                return Step.NeedsConfirmation(
                    AssistantStepResult.ConfirmationRequired(call.id, call.name, siteOf(call.name), description)
                )
            }
            turn.results[call.id] = runTool(call)
            turn.usedLocalData = true
        }
        pending.remove(conversationId)
        appendToolResults(history, turn)
        return Step.Done
    }

    private fun appendToolResults(history: MutableList<Map<String, Any?>>, turn: PendingTurn) {
        for (call in turn.calls) {
            history += mapOf(
                "role" to "tool",
                "tool_call_id" to call.id,
                "name" to call.name,
                "content" to (turn.results[call.id] ?: DECLINED_RESULT_JSON)
            )
        }
    }

    private suspend fun runTool(call: AiToolCall): String = try {
        val result = localToolExecutor.execute(call.name, call.argumentsJson)
        // Free tiers have small per-minute token budgets; an oversized table is cut rather than rejected (413/429).
        if (result.length > MAX_TOOL_RESULT_CHARS) result.take(MAX_TOOL_RESULT_CHARS) + "…[تم اقتطاع النتيجة]" else result
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        """{"error":"tool_failed","tool":"${call.name}"}"""
    }

    private fun siteOf(toolName: String): ToolExecutionSite =
        if (toolName in AssistantToolCatalog.WORKSPACE_TOOL_NAMES) ToolExecutionSite.BACKEND else ToolExecutionSite.LOCAL

    /** Free tiers have small tokens-per-minute budgets, so only the system prompt plus the recent turns are sent. */
    private suspend fun chatOnce(conversationId: String, history: List<Map<String, Any?>>): AiChatResult = gateway.chat(
        messages = OpenAiMessageSanitizer.trimHistory(history, MAX_HISTORY_MESSAGES),
        tools = AssistantToolCatalog.toolsFor(attachments.hasAttachments(conversationId)),
        temperature = 0.2,
        maxTokens = MAX_REPLY_TOKENS
    )

    /**
     * The file tools return bulky tables (a page of a form, a query result). Once the answer is written they are not needed in
     * every later request — the model can call the tool again — so they are cut down to a short head to keep requests small
     * (free tiers allow few tokens per minute). Other tools' results are left alone.
     */
    private fun shrinkBulkyFileResults(history: MutableList<Map<String, Any?>>) {
        for (i in history.indices) {
            val message = history[i]
            if (message["role"] != "tool") continue
            val toolName = message["name"] as? String ?: continue
            val content = message["content"] as? String ?: continue
            if (toolName !in AssistantToolCatalog.FILE_TOOL_NAMES || content.length <= SHRINK_ABOVE_CHARS) continue
            val shrunk = AiJson.toJson(
                mapOf(
                    "note" to "نتيجة سابقة اختُصرت لتوفير الحجم؛ استدعِ الأداة مجددًا إن احتجت تفاصيلها",
                    "head" to content.take(SHRINK_KEEP_CHARS)
                )
            )
            history[i] = message + mapOf("content" to shrunk)
        }
    }

    private companion object {
        const val SHRINK_ABOVE_CHARS = 1_500
        const val SHRINK_KEEP_CHARS = 300
        const val MAX_ROUNDS = 8
        const val MAX_HISTORY_MESSAGES = 30
        const val MAX_TOOL_RESULT_CHARS = 8_000
        const val MAX_REPLY_TOKENS = 2_000
        const val DECLINED_RESULT_JSON = """{"status":"declined_by_user"}"""
    }
}
