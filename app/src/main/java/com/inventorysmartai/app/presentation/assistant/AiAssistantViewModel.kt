package com.inventorysmartai.app.presentation.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.domain.assistant.AssistantContext
import com.inventorysmartai.app.domain.assistant.AssistantStepResult
import com.inventorysmartai.app.domain.assistant.AssistantUiBridge
import com.inventorysmartai.app.domain.assistant.AttachmentInfo
import com.inventorysmartai.app.domain.assistant.CardAction
import com.inventorysmartai.app.domain.assistant.ChatMessage
import com.inventorysmartai.app.domain.assistant.ChatRole
import com.inventorysmartai.app.domain.assistant.UiCommand
import com.inventorysmartai.app.domain.repository.AssistantRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class AiAssistantUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isSending: Boolean = false,
    /** Non-null exactly when the last turn came back as
     *  [AssistantStepResult.ConfirmationRequired] — the screen shows [ActionConfirmationDialog]
     *  (core/designsystem/component/ConfirmationDialog.kt) whenever this is set. */
    val pendingConfirmationTextAr: String? = null,
    val errorMessage: String? = null,
    /** Files picked for the message being written; they travel with the next send and then clear. */
    val pendingAttachments: List<AttachmentInfo> = emptyList(),
    /** True while a picked file is being read and checked. */
    val isAttaching: Boolean = false
)

/**
 * "المساعد الذكي" — Phase 4 spec's AI ASSISTANT section. Owns exactly one conversation for the
 * lifetime of this ViewModel (a fresh `conversationId` per screen visit is the simplest correct
 * choice: nothing in the spec asks for chat history to survive navigating away, and starting
 * clean avoids ever resuming a stale or confused model thread). All the actual tool-calling-loop
 * logic — which calls run silently, which stop for confirmation — lives in
 * [AssistantRepository]; this ViewModel only turns its results into chat bubbles and dialog state.
 */
@HiltViewModel
class AiAssistantViewModel @Inject constructor(
    private val assistantRepository: AssistantRepository,
    private val uiBridge: AssistantUiBridge
) : ViewModel() {

    private val conversationId = UUID.randomUUID().toString()

    /** True while the chat is on screen. When the assistant opens another screen for the person ("افتح صفحة الأصناف"), its
     *  answer is shown over THAT screen instead (see AppNavHost), and "فتح المحادثة" there comes back to this same conversation. */
    private var chatVisible = false

    /** [AssistantUiBridge.queuedCount] when the current request started: if it changed, the assistant opened a screen meanwhile. */
    private var queuedAtStart = 0

    fun onChatVisible(visible: Boolean) {
        chatVisible = visible
        if (visible) uiBridge.clearNotice()
    }

    private val _state = MutableStateFlow(AiAssistantUiState())
    val state: StateFlow<AiAssistantUiState> = _state.asStateFlow()

    /** Set by the screen when the person has something selected elsewhere in the app (a product,
     *  an invoice, ...) before opening the assistant — spec's "selected product/invoice/purchase
     *  request/report context". Optional; most conversations start with none of this set. */
    var context: AssistantContext? = null

    fun onInputChanged(text: String) = update { it.copy(inputText = text) }

    /** The files the person picked ([references] are content:// URIs). Each is read and checked; the ones that pass become chips. */
    fun onFilesPicked(references: List<String>) {
        if (references.isEmpty() || _state.value.isAttaching) return
        val room = MAX_FILES_PER_MESSAGE - _state.value.pendingAttachments.size
        if (room <= 0) {
            update { it.copy(errorMessage = "الحد الأقصى $MAX_FILES_PER_MESSAGE ملفات في الرسالة الواحدة") }
            return
        }
        update { it.copy(isAttaching = true, errorMessage = null) }
        viewModelScope.launch {
            val added = mutableListOf<AttachmentInfo>()
            var problem: String? = null
            for (reference in references.take(room)) {
                val result = assistantRepository.attachFile(conversationId, reference)
                result.onSuccess { added += it }
                result.onFailure { if (problem == null) problem = it.message ?: "تعذّرت قراءة الملف" }
            }
            if (references.size > room && problem == null) problem = "أُضيفت أول $room ملفات فقط (الحد الأقصى $MAX_FILES_PER_MESSAGE في الرسالة)"
            update { it.copy(pendingAttachments = it.pendingAttachments + added, isAttaching = false, errorMessage = problem ?: it.errorMessage) }
        }
    }

    /** The person removed a chip before sending. */
    fun onRemoveAttachment(id: String) {
        assistantRepository.detachFile(conversationId, id)
        update { it.copy(pendingAttachments = it.pendingAttachments.filterNot { file -> file.id == id }) }
    }

    fun sendMessage() {
        val current = _state.value
        val typed = current.inputText.trim()
        val files = current.pendingAttachments
        if ((typed.isEmpty() && files.isEmpty()) || current.isSending || current.isAttaching) return
        submit(typed.ifEmpty { DEFAULT_FILE_PROMPT }, files, clearInput = true)
    }

    /** A chip or a card button that sends a ready sentence («افتح صفحة الأصناف»). It never carries the files being written. */
    fun sendQuick(text: String) {
        val current = _state.value
        val clean = text.trim()
        if (clean.isEmpty() || current.isSending || current.isAttaching) return
        submit(clean, emptyList(), clearInput = false)
    }

    private fun submit(text: String, files: List<AttachmentInfo>, clearInput: Boolean) {
        queuedAtStart = uiBridge.queuedCount
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = ChatRole.USER,
            text = text,
            timestamp = System.currentTimeMillis(),
            attachments = files
        )
        update {
            it.copy(
                messages = it.messages + userMessage,
                inputText = if (clearInput) "" else it.inputText,
                pendingAttachments = if (clearInput) emptyList() else it.pendingAttachments,
                isSending = true,
                errorMessage = null
            )
        }
        viewModelScope.launch {
            val result = assistantRepository.sendMessage(conversationId, text, context, files.map { it.id })
            handleResult(result)
        }
    }

    /**
     * A button on an answer card. Opening a screen and sending a sentence are immediate; the two that change data (a draft purchase
     * request, a saved alias) go through the repository and come back as the same confirmation dialog as any other write.
     */
    fun onCardAction(action: CardAction) {
        when (action) {
            is CardAction.OpenRoute -> {
                uiBridge.post(UiCommand.Navigate(action.route))
            }
            is CardAction.SendText -> sendQuick(action.text)
            is CardAction.DraftPurchase, is CardAction.SaveAlias -> {
                if (_state.value.isSending) return
                queuedAtStart = uiBridge.queuedCount
                update { it.copy(isSending = true, errorMessage = null) }
                viewModelScope.launch {
                    handleResult(assistantRepository.performCardAction(conversationId, action))
                }
            }
        }
    }

    fun onConfirmPendingAction(approved: Boolean) {
        if (_state.value.pendingConfirmationTextAr == null) return
        queuedAtStart = uiBridge.queuedCount
        update { it.copy(pendingConfirmationTextAr = null, isSending = true) }
        viewModelScope.launch {
            val result = assistantRepository.confirmPendingAction(conversationId, approved)
            handleResult(result)
        }
    }

    fun consumeError() = update { it.copy(errorMessage = null) }

    private fun handleResult(result: AssistantStepResult) {
        // The answer goes over the other screen when the chat is not showing — or when the assistant just opened that screen
        // (the chat may still be on screen for the instant before the navigation completes).
        val showOverOtherScreen = !chatVisible || uiBridge.queuedCount != queuedAtStart
        when (result) {
            is AssistantStepResult.Final -> {
                val assistantMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = ChatRole.ASSISTANT,
                    text = result.text.ifBlank { "لم يصل ردّ من المساعد، يرجى المحاولة مرة أخرى." },
                    timestamp = System.currentTimeMillis(),
                    usedLocalData = result.usedLocalData,
                    files = result.files,
                    cards = result.cards
                )
                update { it.copy(messages = it.messages + assistantMessage, isSending = false, pendingConfirmationTextAr = null) }
                if (showOverOtherScreen) uiBridge.postNotice(assistantMessage.text)
            }
            is AssistantStepResult.ConfirmationRequired -> {
                update { it.copy(isSending = false, pendingConfirmationTextAr = result.actionDescriptionAr) }
                if (showOverOtherScreen) uiBridge.postNotice("المساعد ينتظر موافقتك: ${result.actionDescriptionAr}")
            }
            is AssistantStepResult.Error -> {
                update { it.copy(isSending = false, errorMessage = result.messageAr, pendingConfirmationTextAr = null) }
                if (showOverOtherScreen) uiBridge.postNotice(result.messageAr)
            }
        }
    }

    private inline fun update(block: (AiAssistantUiState) -> AiAssistantUiState) {
        _state.value = block(_state.value)
    }

    private companion object {
        const val MAX_FILES_PER_MESSAGE = 4
        const val DEFAULT_FILE_PROMPT = "اقرأ الملف المرفق ولخّص محتواه باختصار، ثم اقترح ما يمكنك فعله به."
    }
}
