package com.inventorysmartai.app.presentation.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.domain.assistant.AssistantContext
import com.inventorysmartai.app.domain.assistant.AssistantStepResult
import com.inventorysmartai.app.domain.assistant.AttachmentInfo
import com.inventorysmartai.app.domain.assistant.ChatMessage
import com.inventorysmartai.app.domain.assistant.ChatRole
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
    private val assistantRepository: AssistantRepository
) : ViewModel() {

    private val conversationId = UUID.randomUUID().toString()

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
        val text = typed.ifEmpty { DEFAULT_FILE_PROMPT }

        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = ChatRole.USER,
            text = text,
            timestamp = System.currentTimeMillis(),
            attachments = files
        )
        update { it.copy(messages = it.messages + userMessage, inputText = "", pendingAttachments = emptyList(), isSending = true, errorMessage = null) }

        viewModelScope.launch {
            val result = assistantRepository.sendMessage(conversationId, text, context, files.map { it.id })
            handleResult(result)
        }
    }

    fun onConfirmPendingAction(approved: Boolean) {
        if (_state.value.pendingConfirmationTextAr == null) return
        update { it.copy(pendingConfirmationTextAr = null, isSending = true) }
        viewModelScope.launch {
            val result = assistantRepository.confirmPendingAction(conversationId, approved)
            handleResult(result)
        }
    }

    fun consumeError() = update { it.copy(errorMessage = null) }

    private fun handleResult(result: AssistantStepResult) {
        when (result) {
            is AssistantStepResult.Final -> {
                val assistantMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = ChatRole.ASSISTANT,
                    text = result.text.ifBlank { "لم يصل ردّ من المساعد، يرجى المحاولة مرة أخرى." },
                    timestamp = System.currentTimeMillis(),
                    usedLocalData = result.usedLocalData,
                    files = result.files
                )
                update { it.copy(messages = it.messages + assistantMessage, isSending = false, pendingConfirmationTextAr = null) }
            }
            is AssistantStepResult.ConfirmationRequired -> {
                update { it.copy(isSending = false, pendingConfirmationTextAr = result.actionDescriptionAr) }
            }
            is AssistantStepResult.Error -> {
                update { it.copy(isSending = false, errorMessage = result.messageAr, pendingConfirmationTextAr = null) }
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
