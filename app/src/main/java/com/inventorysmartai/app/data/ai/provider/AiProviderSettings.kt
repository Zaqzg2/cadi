package com.inventorysmartai.app.data.ai.provider

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** One provider as the settings screen shows it. The key itself is never exposed here — only [hasKey]. */
data class ProviderConfig(
    val id: AiProviderId,
    val enabled: Boolean,
    val hasKey: Boolean,
    val textModel: String,
    val visionModel: String
)

data class AiProviderSnapshot(
    /** In priority order — the first usable one is tried first, the rest are automatic fallbacks. */
    val providers: List<ProviderConfig>,
    /** Read documents with Mistral OCR first (best for Arabic) when a Mistral key exists. */
    val preferMistralOcr: Boolean
)

/** A provider that is switched on AND has a key — everything a request needs. Never logged, never shown. */
class ActiveProvider(
    val id: AiProviderId,
    val apiKey: String,
    val textModel: String,
    val visionModel: String
) {
    override fun toString(): String = "ActiveProvider(${id.name})" // keep the key out of any accidental log
}

/**
 * Keys, on/off switches, model ids and priority for the direct providers. Lives in its OWN DataStore file
 * (`ai_provider_settings`) so it can be excluded from Android backups: the keys are encrypted with a
 * Keystore key that does not move to another phone anyway, so restoring them would only leave dead values.
 */
@Singleton
class AiProviderSettings @Inject constructor(
    @ApplicationContext context: Context,
    private val cipher: SecretCipher
) {
    private val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile(STORE_NAME) }
    )

    private fun keyKey(id: AiProviderId) = stringPreferencesKey("key_${id.name}")
    private fun enabledKey(id: AiProviderId) = booleanPreferencesKey("enabled_${id.name}")
    private fun textKey(id: AiProviderId) = stringPreferencesKey("text_model_${id.name}")
    private fun visionKey(id: AiProviderId) = stringPreferencesKey("vision_model_${id.name}")
    private val orderKey = stringPreferencesKey("order")
    private val preferOcrKey = booleanPreferencesKey("prefer_mistral_ocr")

    val snapshot: Flow<AiProviderSnapshot> = store.data.map { it.toSnapshot() }

    suspend fun current(): AiProviderSnapshot = snapshot.first()

    private fun Preferences.order(): List<AiProviderId> {
        val saved = this[orderKey].orEmpty().split(',').mapNotNull { AiProviderId.fromNameOrNull(it.trim()) }.distinct()
        return saved + AiProviderId.DEFAULT_ORDER.filter { it !in saved }
    }

    private fun Preferences.toSnapshot() = AiProviderSnapshot(
        providers = order().map { id ->
            ProviderConfig(
                id = id,
                enabled = this[enabledKey(id)] ?: true,
                hasKey = !this[keyKey(id)].isNullOrBlank() && cipher.decrypt(this[keyKey(id)].orEmpty()) != null,
                textModel = this[textKey(id)]?.takeIf { it.isNotBlank() } ?: id.defaultTextModel,
                visionModel = this[visionKey(id)]?.takeIf { it.isNotBlank() } ?: id.defaultVisionModel
            )
        },
        preferMistralOcr = this[preferOcrKey] ?: true
    )

    /** Enabled providers with a readable key, in priority order. */
    suspend fun activeProviders(): List<ActiveProvider> {
        val prefs = store.data.first()
        return prefs.order().mapNotNull { id ->
            if (!(prefs[enabledKey(id)] ?: true)) return@mapNotNull null
            val key = prefs[keyKey(id)]?.let(cipher::decrypt)?.trim().orEmpty()
            if (key.isEmpty()) return@mapNotNull null
            ActiveProvider(
                id = id,
                apiKey = key,
                textModel = prefs[textKey(id)]?.takeIf { it.isNotBlank() } ?: id.defaultTextModel,
                visionModel = prefs[visionKey(id)]?.takeIf { it.isNotBlank() } ?: id.defaultVisionModel
            )
        }
    }

    suspend fun hasAnyActive(): Boolean = activeProviders().isNotEmpty()

    suspend fun active(id: AiProviderId): ActiveProvider? = activeProviders().firstOrNull { it.id == id }

    suspend fun setApiKey(id: AiProviderId, key: String) {
        val trimmed = key.trim()
        store.edit { if (trimmed.isEmpty()) it.remove(keyKey(id)) else it[keyKey(id)] = cipher.encrypt(trimmed) }
    }

    suspend fun setEnabled(id: AiProviderId, enabled: Boolean) {
        store.edit { it[enabledKey(id)] = enabled }
    }

    /** Blank means "use the default". */
    suspend fun setModels(id: AiProviderId, textModel: String, visionModel: String) {
        store.edit {
            it[textKey(id)] = textModel.trim()
            it[visionKey(id)] = visionModel.trim()
        }
    }

    suspend fun move(id: AiProviderId, delta: Int) {
        store.edit { prefs ->
            val order = prefs.order().toMutableList()
            val from = order.indexOf(id)
            val to = (from + delta).coerceIn(0, order.lastIndex)
            if (from < 0 || from == to) return@edit
            order.add(to, order.removeAt(from))
            prefs[orderKey] = order.joinToString(",") { it.name }
        }
    }

    suspend fun setPreferMistralOcr(value: Boolean) {
        store.edit { it[preferOcrKey] = value }
    }

    private companion object {
        const val STORE_NAME = "ai_provider_settings"
    }
}
