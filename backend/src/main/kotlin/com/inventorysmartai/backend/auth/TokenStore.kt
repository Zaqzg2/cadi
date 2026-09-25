package com.inventorysmartai.backend.auth

import com.inventorysmartai.backend.config.AppConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class StoredGoogleTokens(
    val sessionId: String,
    val refreshToken: String,
    val accessToken: String,
    /** Epoch millis. */
    val accessTokenExpiresAt: Long,
    val grantedScopes: List<String>,
    val linkedAt: Long
)

/**
 * Persists one Google refresh token (+ cached access token) per app session/device. [sessionId]
 * is an opaque id the Android app generates once at install time and sends with every request —
 * this backend deliberately does not implement its own user-account system; the Google account
 * link IS the identity for Phase 4's purposes. Swap the implementation for a real database before
 * any multi-instance/production deployment (see [FileTokenStore]'s doc comment for exactly why).
 */
interface TokenStore {
    suspend fun get(sessionId: String): StoredGoogleTokens?
    suspend fun put(tokens: StoredGoogleTokens)
    suspend fun remove(sessionId: String)
}

/**
 * Development/single-instance convenience: one JSON file on local disk, guarded by an in-process
 * mutex. This is NOT production-grade persistence:
 *  - Not encrypted at rest — refresh tokens are long-lived credentials; a real deployment MUST
 *    encrypt this table (or use a managed secret store) rather than plain JSON on a filesystem.
 *  - Not safe across multiple backend instances (a load-balanced/horizontally-scaled deployment
 *    needs a real shared database — Postgres/DynamoDB/etc — behind this same [TokenStore]
 *    interface; nothing else in this backend would need to change).
 *  - No token-rotation/revocation bookkeeping beyond "overwrite on re-link".
 * Kept intentionally simple and clearly flagged rather than half-building a production datastore
 * this environment cannot verify against a real database engine anyway.
 */
class FileTokenStore(private val path: String = AppConfig.tokenStoreFilePath) : TokenStore {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private fun file(): File = File(path).apply { parentFile?.mkdirs() }

    private fun readAll(): MutableMap<String, StoredGoogleTokens> {
        val f = file()
        if (!f.exists()) return mutableMapOf()
        return runCatching {
            json.decodeFromString<Map<String, StoredGoogleTokens>>(f.readText()).toMutableMap()
        }.getOrDefault(mutableMapOf())
    }

    private fun writeAll(all: Map<String, StoredGoogleTokens>) {
        file().writeText(json.encodeToString(all))
    }

    override suspend fun get(sessionId: String): StoredGoogleTokens? = mutex.withLock { readAll()[sessionId] }

    override suspend fun put(tokens: StoredGoogleTokens) = mutex.withLock {
        val all = readAll()
        all[tokens.sessionId] = tokens
        writeAll(all)
    }

    override suspend fun remove(sessionId: String) = mutex.withLock {
        val all = readAll()
        all.remove(sessionId)
        writeAll(all)
    }
}
