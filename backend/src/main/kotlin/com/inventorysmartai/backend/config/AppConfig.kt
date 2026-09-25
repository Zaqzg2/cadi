package com.inventorysmartai.backend.config

/**
 * Every secret this backend needs comes from an environment variable — never a literal in source,
 * never committed, never sent to the Android app. This is the one place that reads them, so
 * "never hard-code an API key/secret" (Phase 4 spec, SECURITY section) is enforceable by code
 * review of a single small file rather than "grep the whole codebase and hope".
 *
 * In production, set these as real secrets (a secret manager / the host platform's encrypted env
 * vars) — never in a checked-in `.env` file. See backend/README.md for the full list and how to
 * run this locally for development.
 */
object AppConfig {
    /** Gemini API key (Google AI Studio / Vertex). Required — the process refuses to start
     *  without it (see [require]), rather than silently running with AI features broken. */
    val geminiApiKey: String = requireEnv("GEMINI_API_KEY")

    /** Pin an explicit Gemini model rather than an alias like "gemini-flash-latest" — an alias
     *  can silently change which model answers a request. gemini-3.5-flash was confirmed GA and
     *  current as of this writing (Sept 2026); re-verify against
     *  https://ai.google.dev/gemini-api/docs/models before changing this default, since the
     *  Gemini 2.5 line is scheduled for shutdown on 16 October 2026. */
    val geminiModel: String = System.getenv("GEMINI_MODEL") ?: "gemini-3.5-flash"

    /** The Interactions API is Beta-to-GA and still shipped breaking changes as recently as May
     *  2026 (see README) — pinning an explicit revision means a future Google-side default change
     *  can't silently break this backend. Bump deliberately, not by omission. */
    val geminiApiRevision: String = System.getenv("GEMINI_API_REVISION") ?: "2026-05-20"

    val geminiBaseUrl: String = System.getenv("GEMINI_BASE_URL") ?: "https://generativelanguage.googleapis.com"

    /** The backend's own OAuth "Web application" client — DIFFERENT from any client ID baked
     *  into the Android app. Only this backend ever sees [googleOAuthClientSecret]; the app only
     *  ever obtains a one-time, single-use authorization code (see auth/GoogleAuthService.kt). */
    val googleOAuthClientId: String = requireEnv("GOOGLE_OAUTH_CLIENT_ID")
    val googleOAuthClientSecret: String = requireEnv("GOOGLE_OAUTH_CLIENT_SECRET")

    /** Empty string is valid here — Google's own offline-access docs note this is correct when
     *  there is no web version of the app receiving a redirect. */
    val googleOAuthRedirectUri: String = System.getenv("GOOGLE_OAUTH_REDIRECT_URI") ?: ""

    val port: Int = System.getenv("PORT")?.toIntOrNull() ?: 8080

    /** Where per-user Google refresh tokens are persisted between process restarts. "file" (the
     *  default) is a clearly-flagged development convenience — see auth/TokenStore.kt's doc
     *  comment for exactly what must change before any real deployment. */
    val tokenStoreBackend: String = System.getenv("TOKEN_STORE_BACKEND") ?: "file"
    val tokenStoreFilePath: String = System.getenv("TOKEN_STORE_FILE_PATH") ?: "./data/tokens.json"

    /** Comma-separated allow-list of origins for CORS — the Android app itself is not subject to
     *  CORS (it's not a browser), this only matters if a web admin console is ever added. Empty
     *  by default (no cross-origin browser access at all). */
    val corsAllowedHosts: List<String> = System.getenv("CORS_ALLOWED_HOSTS")
        ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    private fun requireEnv(name: String): String =
        System.getenv(name)?.takeIf { it.isNotBlank() }
            ?: error(
                "Missing required environment variable: $name. This backend refuses to start " +
                    "without it rather than silently disabling AI/Google features — see " +
                    "backend/README.md for the full list of required configuration."
            )
}
