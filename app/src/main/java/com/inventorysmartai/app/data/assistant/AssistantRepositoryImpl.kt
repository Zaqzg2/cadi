package com.inventorysmartai.app.data.assistant

import com.inventorysmartai.app.data.ai.provider.AiChatResult
import com.inventorysmartai.app.data.ai.provider.AiJson
import com.inventorysmartai.app.data.ai.provider.AiToolCall
import com.inventorysmartai.app.data.ai.provider.OpenAiMessageSanitizer
import com.inventorysmartai.app.data.ai.provider.toProviderFailure
import com.inventorysmartai.app.data.assistant.files.AttachmentStore
import com.inventorysmartai.app.data.assistant.offline.OfflineAssistant
import com.inventorysmartai.app.data.backend.AiChatGateway
import com.inventorysmartai.app.data.remote.BackendFailure
import com.inventorysmartai.app.domain.assistant.AssistantCard
import com.inventorysmartai.app.domain.assistant.AssistantCardBuilder
import com.inventorysmartai.app.domain.assistant.AssistantContext
import com.inventorysmartai.app.domain.assistant.AssistantStepResult
import com.inventorysmartai.app.domain.assistant.AttachmentInfo
import com.inventorysmartai.app.domain.assistant.CardAction
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
 *
 * Local first: before any model call, [offline] gets the message. A command it recognises («افتح صفحة الأصناف», «ما الأصناف التي لم تحقق
 * هدف الشهر؟»...) is answered on the phone with cards — no network, no tokens. Only a message it does not recognise goes to the model,
 * and if there is no connection (or no key) the person gets the list of commands that DO work instead of a bare error. The cards for
 * the model path come from the same builder: the results of the smart tools are turned into cards and handed back with the answer.
 */
@Singleton
class AssistantRepositoryImpl @Inject constructor(
    private val gateway: AiChatGateway,
    private val localToolExecutor: LocalToolExecutor,
    private val attachments: AttachmentStore,
    private val offline: OfflineAssistant
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

    /** The cards built from the smart tools' results during the current model turn, handed back with its final answer. */
    private val turnCards = ConcurrentHashMap<String, MutableList<AssistantCard>>()
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

    override suspend fun performCardAction(conversationId: String, action: CardAction): AssistantStepResult =
        lock.withLock {
            // an open model confirmation is declined first, exactly as a new typed message would do
            histories[conversationId]?.let { abandonPending(conversationId, it) }
            offline.requestAction(conversationId, action)
        }

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
        offline.clearPending(conversationId) // a new message also cancels a local action that was waiting for its dialog
        turnCards.remove(conversationId)
        // A failed earlier turn can leave the history ending on a tool result; Mistral rejects a user message right after one.
        if (history.last()["role"] == "tool") {
            history += mapOf("role" to "assistant", "content" to "تعذّر إكمال الرد السابق.")
        }

        // Local first: a command the app understands is carried out here, with no network and no model.
        if (attachmentIds.isEmpty()) {
            val local = offline.tryHandle(conversationId, message)
            if (local != null) {
                recordLocalExchange(history, message, local)
                return local
            }
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
            turnCards.remove(conversationId)
            val failure = e.toProviderFailure()
            if (needsConnection(failure)) {
                // No internet (or no key): not a dead end — say what still works without one.
                offline.offlineHelp("هذا الطلب يحتاج النموذج الذكي، وهو يتطلب اتصالًا بالإنترنت (أو مفتاحًا في إعدادات الذكاء الاصطناعي). هذه الأوامر تعمل بدونه:")
            } else {
                AssistantStepResult.Error(failure.messageAr)
            }
        }
    }

    /** True when the model could not be reached at all — no connection, a timeout, or no key set — as opposed to a model that answered badly. */
    private fun needsConnection(failure: BackendFailure): Boolean =
        failure is BackendFailure.NetworkUnavailable ||
            (failure is BackendFailure.Structured && failure.code in OFFLINE_FALLBACK_CODES)

    /** Keeps what was said locally in the conversation too, so a later question to the model still has the context. */
    private fun recordLocalExchange(history: MutableList<Map<String, Any?>>, message: String, result: AssistantStepResult) {
        history += mapOf("role" to "user", "content" to message)
        val said = if (result is AssistantStepResult.Final) result.text else "بانتظار تأكيد المستخدم."
        history += mapOf("role" to "assistant", "content" to said)
    }

    /** A new message while a confirmation was still open: treat the open write as declined so the history stays valid. */
    private suspend fun abandonPending(conversationId: String, history: MutableList<Map<String, Any?>>) {
        val turn = pending.remove(conversationId) ?: return
        turn.awaiting?.let { turn.results[it.id] = DECLINED_RESULT_JSON }
        for (call in turn.calls) {
            if (call.id in turn.results) continue
            turn.results[call.id] = if (localToolExecutor.requiresConfirmation(call.name)) DECLINED_RESULT_JSON else runTool(conversationId, call)
        }
        appendToolResults(history, turn)
    }

    private fun rollback(history: MutableList<Map<String, Any?>>, mark: Int) {
        while (history.size > mark) history.removeAt(history.lastIndex)
    }

    // ---- confirm ----

    private suspend fun confirmInternal(conversationId: String, approved: Boolean): AssistantStepResult {
        if (offline.hasPending(conversationId)) return offline.confirm(conversationId, approved)
        val turn = pending[conversationId]
        val call = turn?.awaiting
        val history = histories[conversationId]
        if (turn == null || call == null || history == null) {
            return AssistantStepResult.Error("لا يوجد إجراء بانتظار التأكيد لهذه المحادثة")
        }
        attachments.resumeTurn(conversationId)
        return try {
            turn.results[call.id] = if (approved) runTool(conversationId, call).also { turn.usedLocalData = true } else DECLINED_RESULT_JSON
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
                return AssistantStepResult.Final(text, usedLocal, attachments.takeTurnOutputs(), takeCards(conversationId))
            }

            history += mapOf(
                "role" to "assistant",
                "content" to reply.text.orEmpty(),
                "tool_calls" to reply.toolCalls.map { call ->
                    buildMap<String, Any?> {
                        put("id", call.id)
                        put("type", "function")
                        put("function", mapOf("name" to call.name, "arguments" to call.argumentsJson))
                        // Gemini 3 wants its "thought signature" echoed back with the call (see OpenAiMessageSanitizer).
                        call.extraContent?.let { put("extra_content", it) }
                    }
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
            turn.results[call.id] = runTool(conversationId, call)
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

    private suspend fun runTool(conversationId: String, call: AiToolCall): String = try {
        val result = localToolExecutor.execute(call.name, call.argumentsJson)
        collectCards(conversationId, call.name, result) // from the whole result, before it is cut down for the model
        // Free tiers have small per-minute token budgets; an oversized table is cut rather than rejected (413/429).
        if (result.length > MAX_TOOL_RESULT_CHARS) result.take(MAX_TOOL_RESULT_CHARS) + "…[تم اقتطاع النتيجة]" else result
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        """{"error":"tool_failed","tool":"${call.name}"}"""
    }

    /** The smart tools' results become cards under the answer, so the model can write one sentence instead of a table. */
    private fun collectCards(conversationId: String, toolName: String, resultJson: String) {
        if (toolName !in AssistantToolCatalog.SMART_TOOL_NAMES) return
        val parsed = AiJson.parseObject(resultJson) ?: return
        val cards = AssistantCardBuilder.fromToolResult(toolName, parsed)
        if (cards.isNotEmpty()) turnCards.getOrPut(conversationId) { mutableListOf() }.addAll(cards)
    }

    private fun takeCards(conversationId: String): List<AssistantCard> =
        turnCards.remove(conversationId)?.take(MAX_CARDS_PER_ANSWER).orEmpty()

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
        const val MAX_CARDS_PER_ANSWER = 3

        /** Failures that mean "the model could not be reached" (not "it answered badly"): the person gets the offline commands instead. */
        val OFFLINE_FALLBACK_CODES = setOf("AI_TIMEOUT", "BACKEND_TIMEOUT", "AI_NOT_CONFIGURED")
        const val DECLINED_RESULT_JSON = """{"status":"declined_by_user"}"""
    }
}
