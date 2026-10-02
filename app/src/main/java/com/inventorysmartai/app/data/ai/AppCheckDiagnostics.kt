package com.inventorysmartai.app.data.ai

/** What a debug build could find out about the App Check debug secret, for the error screen. */
data class AppCheckDebugInfo(
    val secret: String?,
    /** Names of this app's SharedPreferences files that belong to App Check — shown when no secret was found. */
    val appCheckPreferenceFiles: List<String>
)

/** Written by the Application class when installing the App Check provider failed; read by the error screen. */
object AppCheckStatus {
    @Volatile
    var installError: String? = null
}

/**
 * Pure helpers (no Android, no Firebase) for recognising an App Check rejection and for finding the
 * App Check debug secret.
 */
object AppCheckDiagnostics {

    private val UUID_REGEX =
        Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

    /** `<string name="...">value</string>` — how Android writes a String into a SharedPreferences XML file. */
    private val STRING_ENTRY = Regex("""<string\s+name="[^"]*"\s*>([^<]*)</string>""")

    /**
     * True when any message in the cause chain mentions App Check. Google's 403 texts for Firebase AI Logic
     * all do ("To access this model, you must enforce Firebase App Check", "Firebase AI Logic has been
     * deactivated in this project... you must enforce Firebase App Check", "...enable App Check to allow
     * API Calls"), as does a failed token fetch. Matching the text, not the exception type, keeps this
     * correct whichever FirebaseAIException subclass the SDK wraps it in.
     */
    fun isAppCheckRejection(error: Throwable): Boolean =
        generateSequence<Throwable>(error) { it.cause }.take(8).any { t ->
            val message = t.message.orEmpty()
            message.contains("App Check", ignoreCase = true) || message.contains("AppCheck", ignoreCase = true)
        }

    /** Whether a SharedPreferences file name belongs to App Check (its files are named ...appcheck...). */
    fun isAppCheckPreferenceFile(fileName: String): Boolean = fileName.contains("appcheck", ignoreCase = true)

    /**
     * The debug secret the debug provider keeps in SharedPreferences ("If no debug secret is found in
     * SharedPreferences, a new debug secret will be generated" — Firebase's reference for
     * DebugAppCheckProviderFactory). [files] maps the name of each .xml file in the app's shared_prefs folder to its text. Looks only at
     * App Check *debug* files and only at values that are exactly a UUID, so it does not depend on the
     * SDK's internal key names, and cannot confuse the cached App Check token (a JWT) or another library's
     * ids for the secret.
     */
    fun findDebugSecretInPreferenceFiles(files: Map<String, String>): String? =
        files.entries
            .filter { (name, _) -> isAppCheckPreferenceFile(name) && name.contains("debug", ignoreCase = true) }
            .flatMap { (_, xml) -> STRING_ENTRY.findAll(xml).map { it.groupValues[1].trim() }.toList() }
            .filter { value -> UUID_REGEX.matches(value) }
            .lastOrNull()

    /**
     * The newest debug secret the debug provider printed ("Enter this debug secret into the allow list in
     * the Firebase Console for your project: <uuid>", wording from Google's App Check docs) in [logText],
     * or null. Works for any `logcat` output format because it looks for the sentence, not the tag. A
     * fallback only: the provider prints it once, and the log buffer rolls over within minutes.
     */
    fun findDebugSecret(logText: String): String? =
        logText.lineSequence()
            .filter { it.contains("debug secret", ignoreCase = true) }
            .mapNotNull { UUID_REGEX.find(it)?.value }
            .lastOrNull()

    /** The first UUID-shaped token in [text] (to offer a "copy" button for the debug token in an error message). */
    fun findFirstUuid(text: String): String? = UUID_REGEX.find(text)?.value
}
