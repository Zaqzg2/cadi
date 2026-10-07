package com.inventorysmartai.backend.auth

import com.inventorysmartai.backend.config.GoogleOAuthConfig
import com.inventorysmartai.backend.security.SealedGoogleLink
import com.inventorysmartai.backend.security.TokenSealer
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

private const val GOOGLE_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
private const val GOOGLE_REVOKE_ENDPOINT = "https://oauth2.googleapis.com/revoke"

@Serializable
private data class GoogleTokenResponse(
    val access_token: String,
    val expires_in: Long,
    val refresh_token: String? = null,
    val scope: String? = null,
    val token_type: String? = null
)

sealed class GoogleAuthException(message: String) : Exception(message) {
    class NotConfigured : GoogleAuthException("Google Workspace is not configured on this server")
    class ExchangeFailed(message: String) : GoogleAuthException(message)
    class RefreshFailed(message: String) : GoogleAuthException(message)
    class NotLinked : GoogleAuthException("No Google account is linked for this session")
}

/**
 * The ONLY class that ever sees the Google OAuth client secret. The Android app never receives a secret or a readable
 * refresh token: it hands over a one-time `serverAuthCode`, this service exchanges it, then SEALS the resulting refresh
 * token (see [TokenSealer]) and returns the sealed "link token" for the phone to keep. Nothing is stored on the server,
 * so a restart, a redeploy or a free-tier sleep never logs anybody out.
 *
 * Short-lived access tokens are minted from the refresh token on demand and cached in memory only.
 */
class GoogleAuthService(
    private val http: HttpClient,
    private val config: GoogleOAuthConfig?,
    private val sealer: TokenSealer?,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val json = Json { ignoreUnknownKeys = true }

    private class CachedAccess(val token: String, val expiresAtMs: Long)

    private val cache = ConcurrentHashMap<String, CachedAccess>()

    val isConfigured: Boolean get() = config != null && sealer != null

    data class LinkResult(val linkToken: String, val grantedScopes: List<String>)

    /** [serverAuthCode] is single-use: call this at most once per code the app obtains. */
    suspend fun linkAccount(sessionId: String, serverAuthCode: String): LinkResult {
        val cfg = config ?: throw GoogleAuthException.NotConfigured()
        val seal = sealer ?: throw GoogleAuthException.NotConfigured()
        require(sessionId.isNotBlank()) { "sessionId is required" }

        val response = http.submitForm(
            url = GOOGLE_TOKEN_ENDPOINT,
            formParameters = Parameters.build {
                append("code", serverAuthCode)
                append("client_id", cfg.clientId)
                append("client_secret", cfg.clientSecret)
                append("redirect_uri", cfg.redirectUri)
                append("grant_type", "authorization_code")
            }
        )
        val bodyText = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw GoogleAuthException.ExchangeFailed("فشل تبادل رمز Google (${response.status.value}): ${bodyText.take(200)}")
        }
        val parsed = try {
            json.decodeFromString(GoogleTokenResponse.serializer(), bodyText)
        } catch (e: Exception) {
            throw GoogleAuthException.ExchangeFailed("ردّ غير متوقع من Google")
        }
        val refreshToken = parsed.refresh_token
            ?: throw GoogleAuthException.ExchangeFailed(
                "لم تُرجع Google رمز تحديث. يحدث هذا إذا استُخدم الرمز مرتين أو سبق منح الصلاحية دون إلغائها — " +
                    "أزل وصول التطبيق من https://myaccount.google.com/permissions ثم اربط الحساب من جديد."
            )

        val scopes = parsed.scope?.split(" ")?.filter { it.isNotBlank() }.orEmpty()
        val token = seal.seal(sessionId, SealedGoogleLink(refreshToken, scopes, clock()))
        // Keep the access token we just received: the first Workspace call needs no extra round trip.
        cache[cacheKey(sessionId, token)] = CachedAccess(parsed.access_token, clock() + parsed.expires_in * 1000 - SKEW_MS)
        return LinkResult(token, scopes)
    }

    /** What the user granted, or null when nothing (valid) is linked. Pure local check — no network. */
    fun scopesOf(sessionId: String, linkToken: String?): List<String>? {
        val seal = sealer ?: return null
        if (linkToken.isNullOrBlank()) return null
        return seal.unseal(sessionId, linkToken)?.scopes
    }

    /** A currently valid access token. Every Drive/Sheets/Docs/Gmail/Calendar call goes through here. */
    suspend fun accessToken(sessionId: String, linkToken: String?): String {
        val cfg = config ?: throw GoogleAuthException.NotConfigured()
        val seal = sealer ?: throw GoogleAuthException.NotConfigured()
        if (linkToken.isNullOrBlank()) throw GoogleAuthException.NotLinked()
        val link = seal.unseal(sessionId, linkToken) ?: throw GoogleAuthException.NotLinked()

        val key = cacheKey(sessionId, linkToken)
        val cached = cache[key]
        if (cached != null && clock() < cached.expiresAtMs) return cached.token

        val response = http.submitForm(
            url = GOOGLE_TOKEN_ENDPOINT,
            formParameters = Parameters.build {
                append("refresh_token", link.refreshToken)
                append("client_id", cfg.clientId)
                append("client_secret", cfg.clientSecret)
                append("grant_type", "refresh_token")
            }
        )
        val bodyText = response.bodyAsText()
        if (response.status == HttpStatusCode.BadRequest || response.status == HttpStatusCode.Unauthorized) {
            // The user revoked access in their Google account (or the grant expired): the app must connect again.
            cache.remove(key)
            throw GoogleAuthException.RefreshFailed("انتهى ربط حساب Google أو أُلغي. أعد ربط الحساب من الإعدادات.")
        }
        if (!response.status.isSuccess()) {
            throw GoogleAuthException.RefreshFailed("تعذّر تجديد جلسة Google (${response.status.value})")
        }
        val parsed = try {
            json.decodeFromString(GoogleTokenResponse.serializer(), bodyText)
        } catch (e: Exception) {
            throw GoogleAuthException.RefreshFailed("ردّ غير متوقع من Google")
        }
        cache[key] = CachedAccess(parsed.access_token, clock() + parsed.expires_in * 1000 - SKEW_MS)
        trimCache()
        return parsed.access_token
    }

    /** Revokes the grant at Google (best effort) and forgets any cached access token. The phone then deletes its link token. */
    suspend fun unlink(sessionId: String, linkToken: String?) {
        val seal = sealer ?: return
        if (linkToken.isNullOrBlank()) return
        val link = seal.unseal(sessionId, linkToken) ?: return
        cache.remove(cacheKey(sessionId, linkToken))
        try {
            http.submitForm(url = GOOGLE_REVOKE_ENDPOINT, formParameters = Parameters.build { append("token", link.refreshToken) })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Best effort: the phone forgets the token regardless, and the user can also revoke at myaccount.google.com.
        }
    }

    private fun trimCache() {
        if (cache.size <= MAX_CACHE) return
        val now = clock()
        cache.entries.removeIf { it.value.expiresAtMs <= now }
        if (cache.size > MAX_CACHE) cache.clear()
    }

    private fun cacheKey(sessionId: String, linkToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$sessionId|$linkToken".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        /** Refresh a little early rather than racing a call that starts just before real expiry. */
        const val SKEW_MS = 60_000L
        const val MAX_CACHE = 500
    }
}
