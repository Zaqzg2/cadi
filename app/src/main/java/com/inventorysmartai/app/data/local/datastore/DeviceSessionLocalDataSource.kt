package com.inventorysmartai.app.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject

/**
 * Phase 4: a random, opaque id — never a device identifier, email, or anything else personally
 * identifying — generated once on first use and persisted thereafter. Sent with every backend
 * call as `sessionId`; the backend uses it purely as a key into its own token/conversation stores
 * (see backend/auth/TokenStore.kt) and never learns anything about the device beyond "some app
 * instance with this random id".
 */
class DeviceSessionLocalDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val SESSION_ID = stringPreferencesKey("ai_backend_session_id")
    }

    suspend fun getOrCreateSessionId(): String {
        val existing = dataStore.data.first()[Keys.SESSION_ID]
        if (existing != null) return existing
        val created = UUID.randomUUID().toString()
        dataStore.edit { it[Keys.SESSION_ID] = created }
        return created
    }
}
