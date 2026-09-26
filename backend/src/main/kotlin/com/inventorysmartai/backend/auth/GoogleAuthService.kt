package com.inventorysmartai.backend.auth

import com.inventorysmartai.backend.config.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private const val GOOGLE_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"

@Serializable
private data class GoogleTokenResponse(
    val access_token: String,
    val expires_in: Long,
    val refresh_token: String? = null,
    val scope: String? = null,
    val token_type: String? = null
)

sealed class GoogleAuthException(message: String) : Exception(message) {
    class ExchangeFailed(message: String) : GoogleAuthException(message)
    class RefreshFailed(message: String) : GoogleAuthException(message)
    class NotLinked(sessionId: String) : GoogleAuthException("No Google account linked for session $sessionId")
}

/**
 * The ONLY class in this backend that ever sees [AppConfig.googleOAuthClientSecret]. The Android
 * app never receives a client secret or a refresh token — it hands this service a one-time
 * `serverAuthCode` obtained via `AuthorizationClient.authorize(... .requestOfflineAccess(...))`
 * (see the app's `data/google/GoogleAuthManager.kt`), and this service does the rest exactly as
 * Google's own "enable server-side access" documentation describes for a mobile client with a
 * separate backend: exchange the code once for a refresh token (persisted, see [TokenStore]),
 * then mint short-lived access tokens from it as needed. Kept as one small class so an auditor
 * can see the entire OAuth trust boundary in one file.
 */
class GoogleAuthService(
    private val http: HttpClient,
    private val tokenStore: TokenStore
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** [serverAuthCode] is single-use — this must be called at most once per code the app
     *  obtains. [sessionId] is the app-generated device/session identifier this backend uses as
     *  the token-store key (see [TokenStore]). */
    suspend fun linkAccount(sessionId: String, serverAuthCode: String): StoredGoogleTokens {
        val response = http.submitForm(
            url = GOOGLE_TOKEN_ENDPOINT,
            formParameters = Parameters.build {
                append("code", serverAuthCode)
                append("client_id", AppConfig.googleOAuthClientId)
                append("client_secret", AppConfig.googleOAuthClientSecret)
                append("redirect_uri", AppConfig.googleOAuthRedirectUri)
                append("grant_type", "authorization_code")
            }
        )
        val bodyText = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw GoogleAuthException.ExchangeFailed("Google token exchange failed (${response.status.value}): $bodyText")
        }
        val parsed = runCatching { json.decodeFromString<GoogleTokenResponse>(bodyText) }
            .getOrElse { throw GoogleAuthException.ExchangeFailed("Unexpected response from Google's token endpoint: ${it.message}") }

        val refreshToken = parsed.refresh_token
            ?: throw GoogleAuthException.ExchangeFailed(
                "Google did not return a refresh token. This happens when the same authorization " +
                    "code is exchanged twice, or the user previously granted access without " +
                    "revoking it — ask the user to remove the app's access at " +
                    "https://myaccount.google.com/permissions and connect again."
            )

        val stored = StoredGoogleTokens(
            sessionId = sessionId,
            refreshToken = refreshToken,
            accessToken = parsed.access_token,
            accessTokenExpiresAt = System.currentTimeMillis() + (parsed.expires_in * 1000) - REFRESH_SKEW_MILLIS,
            grantedScopes = parsed.scope?.split(" ").orEmpty(),
            linkedAt = System.currentTimeMillis()
        )
        tokenStore.put(stored)
        return stored
    }

    /** Returns a currently-valid access token for [sessionId], refreshing it first if the cached
     *  one is at or past expiry. Every Drive/Sheets/Docs/Gmail/Calendar call in this backend goes
     *  through this — none of them cache or reuse a token themselves. */
    suspend fun getValidAccessToken(sessionId: String): String {
        val stored = tokenStore.get(sessionId) ?: throw GoogleAuthException.NotLinked(sessionId)
        if (System.currentTimeMillis() < stored.accessTokenExpiresAt) return stored.accessToken
        return refresh(stored).accessToken
    }

    suspend fun isLinked(sessionId: String): Boolean = tokenStore.get(sessionId) != null

    suspend fun unlink(sessionId: String) = tokenStore.remove(sessionId)

    private suspend fun refresh(stored: StoredGoogleTokens): StoredGoogleTokens {
        val response = http.submitForm(
            url = GOOGLE_TOKEN_ENDPOINT,
            formParameters = Parameters.build {
                append("refresh_token", stored.refreshToken)
                append("client_id", AppConfig.googleOAuthClientId)
                append("client_secret", AppConfig.googleOAuthClientSecret)
                append("grant_type", "refresh_token")
            }
        )
        val bodyText = response.bodyAsText()
        if (response.status == HttpStatusCode.BadRequest || response.status == HttpStatusCode.Unauthorized) {
            // A refresh token can be invalidated by the user revoking access in their Google
            // account — treat this as "unlinked" rather than a generic error so the app can show
            // "connect your Google account again" instead of a confusing retry loop.
            tokenStore.remove(stored.sessionId)
            throw GoogleAuthException.RefreshFailed("Google refresh token is no longer valid — the user must reconnect their account: $bodyText")
        }
        if (!response.status.isSuccess()) {
            throw GoogleAuthException.RefreshFailed("Google token refresh failed (${response.status.value}): $bodyText")
        }
        val parsed = runCatching { json.decodeFromString<GoogleTokenResponse>(bodyText) }
            .getOrElse { throw GoogleAuthException.RefreshFailed("Unexpected response from Google's token endpoint: ${it.message}") }

        val updated = stored.copy(
            accessToken = parsed.access_token,
            accessTokenExpiresAt = System.currentTimeMillis() + (parsed.expires_in * 1000) - REFRESH_SKEW_MILLIS
        )
        tokenStore.put(updated)
        return updated
    }

    private companion object {
        /** Refresh a little early rather than racing a call that starts just before real expiry. */
        const val REFRESH_SKEW_MILLIS = 60_000L
    }
}
