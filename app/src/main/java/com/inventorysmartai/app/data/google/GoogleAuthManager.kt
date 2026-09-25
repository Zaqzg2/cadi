package com.inventorysmartai.app.data.google

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class GoogleAuthCancelledException : Exception("تم إلغاء عملية ربط حساب Google")

/**
 * Requests exactly the three scopes Phase 4 needs (Drive/Sheets/Docs via `drive.file`, plus
 * `gmail.send` and `calendar.events` — see this class's [REQUIRED_SCOPES]) and a one-time
 * server auth code for the backend, via `AuthorizationClient` — per developer.android.com/
 * identity/authorization: "For authorizing actions that need access to user data stored by
 * Google, we recommend using AuthorizationClient" (Credential Manager is for *authentication*,
 * a separate concern this app does not need: AuthorizationClient's own consent screen already
 * includes account selection). `GoogleSignInClient`/`GoogleSignInOptions` were the deprecated
 * part of the old API — `AuthorizationClient` remains current and is not affected.
 *
 * [register] MUST be called from the hosting Activity's `onCreate`, before `STARTED`
 * (`registerForActivityResult` requires this) — see `MainActivity.onCreate`.
 */
@Singleton
class GoogleAuthManager @Inject constructor() {

    private var launcher: ActivityResultLauncher<IntentSenderRequest>? = null
    private var pendingContinuation: CancellableContinuation<AuthorizationResult>? = null

    private val requiredScopes = listOf(
        Scope("https://www.googleapis.com/auth/drive.file"),
        Scope("https://www.googleapis.com/auth/gmail.send"),
        Scope("https://www.googleapis.com/auth/calendar.events")
    )

    fun register(activity: ComponentActivity) {
        launcher = activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { activityResult ->
            val continuation = pendingContinuation ?: return@registerForActivityResult
            pendingContinuation = null
            if (activityResult.resultCode != Activity.RESULT_OK || activityResult.data == null) {
                continuation.resumeWithException(GoogleAuthCancelledException())
                return@registerForActivityResult
            }
            try {
                val result = Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(activityResult.data)
                continuation.resume(result)
            } catch (e: ApiException) {
                continuation.resumeWithException(e)
            }
        }
    }

    /**
     * Suspends until the user has granted (or cancelled) access, returning the one-time server
     * auth code to hand to the backend's `POST /v1/auth/google/link` (see data/google/
     * GoogleAuthRepositoryImpl). [backendServerClientId] is the BACKEND's "Web application" OAuth
     * client id (backend/README.md explains why this is deliberately a different Cloud Console
     * client from the app's own Android client id) — never a secret, safe to ship in the app.
     */
    suspend fun requestServerAuthCode(activity: ComponentActivity, backendServerClientId: String): String {
        val activeLauncher = launcher
            ?: error("GoogleAuthManager.register(activity) must be called in onCreate before requestServerAuthCode")

        val request = AuthorizationRequest.builder()
            .setRequestedScopes(requiredScopes)
            .requestOfflineAccess(backendServerClientId)
            .build()

        val initialResult = Identity.getAuthorizationClient(activity).authorize(request).await()

        val finalResult = if (initialResult.hasResolution()) {
            val pendingIntent = initialResult.pendingIntent
                ?: error("hasResolution() was true but pendingIntent was null")
            suspendCancellableCoroutine { continuation ->
                pendingContinuation = continuation
                activeLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
            }
        } else {
            initialResult
        }

        return finalResult.serverAuthCode
            ?: error("Google did not return a server auth code — offline access may not have been requested correctly")
    }
}

/** Small hand-rolled Task→coroutine bridge — avoids pulling in the separate kotlinx-coroutines-
 *  play-services artifact for the one `.await()` call this file needs. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
}
