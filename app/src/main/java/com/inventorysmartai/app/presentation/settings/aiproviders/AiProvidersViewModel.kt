package com.inventorysmartai.app.presentation.settings.aiproviders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.data.ai.provider.AiProviderId
import com.inventorysmartai.app.data.ai.provider.AiProviderSettings
import com.inventorysmartai.app.data.ai.provider.AiProviderSnapshot
import com.inventorysmartai.app.data.ai.provider.OpenAiCompatClient
import com.inventorysmartai.app.data.ai.provider.toProviderFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class AiProvidersUiState(
    val snapshot: AiProviderSnapshot = AiProviderSnapshot(providers = emptyList(), preferMistralOcr = true),
    val testing: Set<AiProviderId> = emptySet(),
    /** Last test / save result per provider, already in Arabic. */
    val messages: Map<AiProviderId, String> = emptyMap()
)

@HiltViewModel
class AiProvidersViewModel @Inject constructor(
    private val settings: AiProviderSettings,
    private val client: OpenAiCompatClient
) : ViewModel() {

    private data class Ephemeral(val testing: Set<AiProviderId> = emptySet(), val messages: Map<AiProviderId, String> = emptyMap())

    private val ephemeral = MutableStateFlow(Ephemeral())

    val state: StateFlow<AiProvidersUiState> = combine(settings.snapshot, ephemeral) { snapshot, e ->
        AiProvidersUiState(snapshot, e.testing, e.messages)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiProvidersUiState())

    fun saveKey(id: AiProviderId, key: String) = viewModelScope.launch {
        settings.setApiKey(id, key)
        say(id, "تم حفظ المفتاح مشفّرًا على هذا الجهاز.")
    }

    fun clearKey(id: AiProviderId) = viewModelScope.launch {
        settings.setApiKey(id, "")
        say(id, "تم حذف المفتاح.")
    }

    fun setEnabled(id: AiProviderId, enabled: Boolean) = viewModelScope.launch { settings.setEnabled(id, enabled) }

    fun saveModels(id: AiProviderId, textModel: String, visionModel: String) = viewModelScope.launch {
        settings.setModels(id, textModel, visionModel)
        say(id, "تم حفظ أسماء النماذج.")
    }

    fun move(id: AiProviderId, delta: Int) = viewModelScope.launch { settings.move(id, delta) }

    fun setPreferMistralOcr(value: Boolean) = viewModelScope.launch { settings.setPreferMistralOcr(value) }

    /** true = skip the backend and use the keys saved on this phone; false (default) = the backend. */
    fun setPreferDirect(value: Boolean) = viewModelScope.launch { settings.setPreferDirect(value) }

    /** One tiny request: proves the key works AND the text-model id exists. */
    fun test(id: AiProviderId) = viewModelScope.launch {
        val provider = settings.active(id)
        if (provider == null) {
            say(id, "أدخل المفتاح وفعّل المزوّد أولًا.")
            return@launch
        }
        ephemeral.update { it.copy(testing = it.testing + id) }
        val message = try {
            client.ping(provider)
            "يعمل بشكل سليم ✓"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.toProviderFailure().messageAr
        }
        ephemeral.update { it.copy(testing = it.testing - id, messages = it.messages + (id to message)) }
    }

    private fun say(id: AiProviderId, message: String) {
        ephemeral.update { it.copy(messages = it.messages + (id to message)) }
    }
}
