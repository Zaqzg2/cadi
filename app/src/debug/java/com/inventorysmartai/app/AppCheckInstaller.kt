package com.inventorysmartai.app

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.inventorysmartai.app.data.ai.AppCheckDiagnostics

/**
 * Debug builds use App Check's DEBUG provider (firebase-appcheck-debug is a debugImplementation
 * dependency, so this class does not exist in release builds). On first launch it logs a line like
 * "Enter this debug secret into the allow list in the Firebase Console..." under the tag
 * DebugAppCheckProvider: register that token once in Firebase Console > App Check > Apps > Manage
 * debug tokens. Keep the token private.
 *
 * The same object exists in src/debug and src/release with the same name; the build type picks one.
 */
internal object AppCheckInstaller {
    fun install() {
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
    }

    /**
     * Debug builds only: the debug secret the provider printed to THIS app's own log, if it is still
     * in the log buffer — so it can be shown on screen and copied without a computer. An app can always
     * read its own log lines through `logcat`. The provider prints the secret only when it generates a
     * new one (first launch after an install or after clearing the app's data), so after the buffer has
     * rolled over this returns null. Blocking: call from a background thread.
     */
    fun recentDebugSecret(): String? = runCatching {
        val process = ProcessBuilder("logcat", "-d", "-v", "raw").redirectErrorStream(true).start()
        try {
            AppCheckDiagnostics.findDebugSecret(process.inputStream.bufferedReader().use { it.readText() })
        } finally {
            process.destroy()
        }
    }.getOrNull()
}
