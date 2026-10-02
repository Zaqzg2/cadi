package com.inventorysmartai.app

import android.content.Context
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.inventorysmartai.app.data.ai.AppCheckDiagnostics
import java.io.File

/**
 * Debug builds use App Check's DEBUG provider (firebase-appcheck-debug is a debugImplementation
 * dependency, so this class does not exist in release builds). The provider invents a secret on first use
 * and keeps it in the app's SharedPreferences; that secret must be registered once in Firebase Console >
 * App Check > Apps > (this app) > Manage debug tokens. Keep the token private.
 *
 * The same object exists in src/debug and src/release with the same name; the build type picks one.
 */
internal object AppCheckInstaller {
    fun install() {
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
    }

    /**
     * Debug builds only: the debug secret, so the error screen can show it and offer to copy it (no computer
     * or Logcat needed). First choice is the provider's own SharedPreferences file, which exists as long as
     * the app's data does; the app's own log is the fallback (the provider prints the secret there only
     * once, and the log rolls over within minutes). Blocking disk/process work: call from a background thread.
     */
    fun recentDebugSecret(context: Context): String? = secretFromPreferenceFiles(context) ?: secretFromOwnLog()

    /**
     * Names of the app's SharedPreferences files that belong to App Check. When no secret is found this
     * tells "the debug provider never ran" (no file) apart from "it keeps the secret somewhere unexpected".
     */
    fun appCheckPreferenceFiles(context: Context): List<String> = runCatching {
        preferenceFiles(context).map { it.name }.filter { AppCheckDiagnostics.isAppCheckPreferenceFile(it) }
    }.getOrDefault(emptyList())

    private fun preferenceFiles(context: Context): List<File> =
        File(context.dataDir, "shared_prefs")
            .listFiles { file -> file.isFile && file.name.endsWith(".xml") }
            .orEmpty()
            .toList()

    private fun secretFromPreferenceFiles(context: Context): String? = runCatching {
        AppCheckDiagnostics.findDebugSecretInPreferenceFiles(
            preferenceFiles(context).associate { it.name to it.readText() }
        )
    }.getOrNull()

    private fun secretFromOwnLog(): String? = runCatching {
        // An app can always read its own log lines through logcat (never other apps').
        val process = ProcessBuilder("logcat", "-d", "-v", "raw").redirectErrorStream(true).start()
        try {
            AppCheckDiagnostics.findDebugSecret(process.inputStream.bufferedReader().use { it.readText() })
        } finally {
            process.destroy()
        }
    }.getOrNull()
}
