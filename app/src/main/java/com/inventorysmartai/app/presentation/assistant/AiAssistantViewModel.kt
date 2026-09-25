package com.inventorysmartai.app.presentation.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.domain.assistant.AssistantContext
import com.inventorysmartai.app.domain.assistant.AssistantStepResult
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
    val errorMessage: String? = null
)

/**
 * "المساعد الذكي" — Phase 4 spec's AI ASSISTANT section. Owns exactly one conversation for the
 * lifetime of this ViewModel (a fresh `conversationId` per screen visit is the simplest correct
 * choice: nothing in the spec asks for chat history to survive navigating away, and starting
 * clean avoids ever resuming a stale/confused Gemini thread). All the actual tool-calling-loop
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

    fun sendMessage() {
        val text = _state.value.inputText.trim()
        if (text.isEmpty() || _state.value.isSending) return

        val userMessage = ChatMessage(id = UUID.randomUUID().toString(), role = ChatRole.USER, text = text, timestamp = System.currentTimeMillis())
        update { it.copy(messages = it.messages + userMessage, inputText = "", isSending = true, errorMessage = null) }

        viewModelScope.launch {
            val result = assistantRepository.sendMessage(conversationId, text, context)
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
                    usedLocalData = result.usedLocalData
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
}
