package com.inventorysmartai.app.data.ai

/**
 * Pure helpers (no Android, no Firebase) for recognising an App Check rejection and for finding the
 * App Check debug secret in a log dump.
 */
object AppCheckDiagnostics {

    private val UUID_REGEX =
        Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

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

    /**
     * The newest debug secret the debug provider printed ("Enter this debug secret into the allow list in
     * the Firebase Console for your project: <uuid>", wording from Google's App Check docs) in [logText],
     * or null. Works for any `logcat` output format because it looks for the sentence, not the tag.
     */
    fun findDebugSecret(logText: String): String? =
        logText.lineSequence()
            .filter { it.contains("debug secret", ignoreCase = true) }
            .mapNotNull { UUID_REGEX.find(it)?.value }
            .lastOrNull()
}
