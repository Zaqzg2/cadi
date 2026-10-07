package com.inventorysmartai.app.data.backend

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the one credential the backend gives this phone: the SEALED Google link token.
 *
 * After "ربط حساب Google" the server encrypts the Google refresh token with a secret only it knows and returns the
 * result; this class stores it. The phone cannot read or alter it, and the server keeps nothing — which is what lets the
 * backend run on free hosting that wipes its disk whenever it sleeps. The token is attached to every backend request
 * by [BackendHeadersInterceptor] (header X-Google-Link) and is meaningless to anyone but that server.
 */
@Singleton
class BackendCredentials @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val GOOGLE_LINK = stringPreferencesKey("backend_google_link_token")
    }

    @Volatile
    private var cached: String? = null

    @Volatile
    private var loaded = false

    /** For the OkHttp interceptor, which runs on a network thread (never the main thread), so blocking once here is fine. */
    fun googleLinkTokenBlocking(): String? {
        if (!loaded) {
            synchronized(this) {
                if (!loaded) {
                    cached = runBlocking { dataStore.data.first()[Keys.GOOGLE_LINK] }
                    loaded = true
                }
            }
        }
        return cached
    }

    suspend fun saveGoogleLinkToken(token: String) {
        dataStore.edit { it[Keys.GOOGLE_LINK] = token }
        cached = token
        loaded = true
    }

    suspend fun clearGoogleLinkToken() {
        dataStore.edit { it.remove(Keys.GOOGLE_LINK) }
        cached = null
        loaded = true
    }
}
