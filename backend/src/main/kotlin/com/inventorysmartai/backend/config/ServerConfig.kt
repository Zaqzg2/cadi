package com.inventorysmartai.backend.config

/**
 * One OpenAI-compatible AI provider this server may call. The key never leaves the server; [toString] hides it so
 * an accidental log line cannot leak it.
 */
data class AiProviderConfig(
    val id: String,
    val label: String,
    /** Always ends with '/'. */
    val baseUrl: String,
    val apiKey: String,
    val textModel: String,
    val visionModel: String,
    /** Mistral only: POST {baseUrl}ocr reads PDFs and photos (best for Arabic documents). Null = no OCR endpoint. */
    val ocrModel: String?
) {
    val supportsOcr: Boolean get() = ocrModel != null

    override fun toString(): String = "AiProviderConfig($id)"
}

/** The backend's own "Web application" OAuth client (NOT the Android client). */
data class GoogleOAuthConfig(val clientId: String, val clientSecret: String, val redirectUri: String) {
    override fun toString(): String = "GoogleOAuthConfig(clientId=$clientId)"
}

/** Everything wrong with the environment, reported together so one deploy attempt fixes all of it. */
class ConfigException(val problems: List<String>) : Exception(problems.joinToString("; "))

/**
 * Every setting, read once from environment variables by [fromEnv]. Nothing here is hard-coded and nothing secret
 * is ever printed ([toString] is overridden). Which variables exist is documented in backend/README.md and
 * backend/.env.example.
 *
 * Deliberately NOT required: Google OAuth. Without it the AI features work and the Google Workspace endpoints answer
 * `GOOGLE_NOT_CONFIGURED` — a free-tier deployment can start with just an app key and one AI key.
 */
data class ServerConfig(
    val port: Int,
    /** null only when APP_AUTH_DISABLED=true (local development). */
    val appApiKey: String?,
    /** In priority order; never empty. */
    val providers: List<AiProviderConfig>,
    val google: GoogleOAuthConfig?,
    /** Secret the Google refresh tokens are sealed with (see security/TokenSealer.kt). Present whenever [google] is. */
    val tokenEncryptionSecret: String?,
    val corsAllowedHosts: List<String>,
    val trustProxyHeaders: Boolean,
    val chatPerMinute: Int,
    val extractPerMinute: Int,
    val workspacePerMinute: Int,
    /** 0 = unlimited. Counts chat + extraction requests together, per UTC day. */
    val dailyAiRequestCap: Int,
    val maxUploadBytes: Int,
    val providerTimeoutMs: Long,
    val totalAiTimeoutMs: Long
) {
    override fun toString(): String =
        "ServerConfig(port=$port, auth=${appApiKey != null}, providers=${providers.map { it.id }}, google=${google != null})"

    /** One human-readable line per fact, for the startup log. No secrets. */
    fun describe(): List<String> = listOf(
        "Port: $port",
        "App key check: ${if (appApiKey != null) "ON" else "OFF (APP_AUTH_DISABLED=true — development only!)"}",
        "AI providers (priority order): " + providers.joinToString(" > ") { "${it.label} [text=${it.textModel}, vision=${it.visionModel}]" },
        "Google Workspace: ${if (google != null) "enabled" else "disabled (set GOOGLE_OAUTH_CLIENT_ID/SECRET + TOKEN_ENCRYPTION_KEY to enable)"}",
        "Rate limits per minute — chat $chatPerMinute, extraction $extractPerMinute, workspace $workspacePerMinute; daily AI cap " +
            (if (dailyAiRequestCap == 0) "unlimited" else dailyAiRequestCap.toString())
    )

    companion object {
        private class Spec(
            val id: String,
            val label: String,
            val envPrefix: String,
            val baseUrl: String,
            val defaultText: String,
            val defaultVision: String,
            val defaultOcrModel: String?
        )

        // Defaults mirror the Android app's own provider list (data/ai/provider/AiProviderId.kt). Model ids move fast
        // on every provider, so each one can be overridden from the environment without a code change.
        private val SPECS = listOf(
            Spec("mistral", "Mistral", "MISTRAL", "https://api.mistral.ai/v1/", "mistral-small-latest", "mistral-small-latest", "mistral-ocr-latest"),
            Spec("groq", "Groq", "GROQ", "https://api.groq.com/openai/v1/", "openai/gpt-oss-120b", "qwen/qwen3.8-27b", null),
            Spec("openrouter", "OpenRouter", "OPENROUTER", "https://openrouter.ai/api/v1/", "openrouter/free", "openrouter/free", null)
        )

        private val DEFAULT_ORDER = listOf("mistral", "groq", "openrouter", "custom")

        fun fromEnv(env: Map<String, String> = System.getenv()): ServerConfig {
            val problems = mutableListOf<String>()

            fun text(name: String): String? = env[name]?.trim()?.takeIf { it.isNotEmpty() }

            fun int(name: String, default: Int, min: Int): Int {
                val raw = text(name) ?: return default
                val parsed = raw.toIntOrNull()
                if (parsed == null || parsed < min) {
                    problems += "$name must be an integer >= $min (got \"$raw\")"
                    return default
                }
                return parsed
            }

            // ---- app key ----
            val authDisabled = text("APP_AUTH_DISABLED").equals("true", ignoreCase = true)
            val appKey = text("APP_API_KEY")
            if (appKey == null && !authDisabled) {
                problems += "APP_API_KEY is required (a long random string shared with the Android app as BACKEND_APP_KEY). " +
                    "For local development only you may set APP_AUTH_DISABLED=true instead."
            }
            if (appKey != null && appKey.length < 16) problems += "APP_API_KEY must be at least 16 characters"

            // ---- AI providers ----
            val configured = SPECS.mapNotNull { spec ->
                val key = text("${spec.envPrefix}_API_KEY") ?: return@mapNotNull null
                AiProviderConfig(
                    id = spec.id,
                    label = spec.label,
                    baseUrl = spec.baseUrl,
                    apiKey = key,
                    textModel = text("${spec.envPrefix}_TEXT_MODEL") ?: spec.defaultText,
                    visionModel = text("${spec.envPrefix}_VISION_MODEL") ?: spec.defaultVision,
                    ocrModel = spec.defaultOcrModel?.let { text("${spec.envPrefix}_OCR_MODEL") ?: it }
                )
            }.toMutableList()

            val customKey = text("CUSTOM_AI_API_KEY")
            val customUrl = text("CUSTOM_AI_BASE_URL")
            val customText = text("CUSTOM_AI_TEXT_MODEL")
            if (customKey != null || customUrl != null || customText != null) {
                if (customKey == null || customUrl == null || customText == null) {
                    problems += "A custom provider needs CUSTOM_AI_BASE_URL, CUSTOM_AI_API_KEY and CUSTOM_AI_TEXT_MODEL together"
                } else {
                    configured += AiProviderConfig(
                        id = "custom",
                        label = text("CUSTOM_AI_LABEL") ?: "Custom",
                        baseUrl = if (customUrl.endsWith("/")) customUrl else "$customUrl/",
                        apiKey = customKey,
                        textModel = customText,
                        visionModel = text("CUSTOM_AI_VISION_MODEL") ?: customText,
                        ocrModel = null
                    )
                }
            }

            val wantedOrder = text("AI_PROVIDER_ORDER")
                ?.split(',')?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() }
                ?: DEFAULT_ORDER
            val byId = configured.associateBy { it.id }
            val ordered = wantedOrder.distinct().mapNotNull { byId[it] } + configured.filter { it.id !in wantedOrder }
            if (ordered.isEmpty()) {
                problems += "At least one AI provider key is required: MISTRAL_API_KEY, GROQ_API_KEY, OPENROUTER_API_KEY, " +
                    "or CUSTOM_AI_BASE_URL + CUSTOM_AI_API_KEY + CUSTOM_AI_TEXT_MODEL"
            }

            // ---- Google (optional) ----
            val googleId = text("GOOGLE_OAUTH_CLIENT_ID")
            val googleSecret = text("GOOGLE_OAUTH_CLIENT_SECRET")
            val tokenSecret = text("TOKEN_ENCRYPTION_KEY")
            var google: GoogleOAuthConfig? = null
            if (googleId != null || googleSecret != null) {
                if (googleId == null || googleSecret == null) {
                    problems += "GOOGLE_OAUTH_CLIENT_ID and GOOGLE_OAUTH_CLIENT_SECRET must be set together"
                } else if (tokenSecret == null) {
                    problems += "TOKEN_ENCRYPTION_KEY is required when Google OAuth is configured " +
                        "(a long random secret — it seals the Google refresh tokens the phone stores)"
                } else {
                    google = GoogleOAuthConfig(googleId, googleSecret, text("GOOGLE_OAUTH_REDIRECT_URI").orEmpty())
                }
            }
            if (tokenSecret != null && tokenSecret.length < 32) problems += "TOKEN_ENCRYPTION_KEY must be at least 32 characters"

            // ---- everything else ----
            val port = int("PORT", 8080, 1)
            val chatPerMinute = int("RATE_LIMIT_CHAT_PER_MINUTE", 30, 1)
            val extractPerMinute = int("RATE_LIMIT_EXTRACT_PER_MINUTE", 6, 1)
            val workspacePerMinute = int("RATE_LIMIT_WORKSPACE_PER_MINUTE", 20, 1)
            val dailyCap = int("DAILY_AI_REQUEST_CAP", 1500, 0)
            val maxUpload = int("MAX_UPLOAD_BYTES", 10 * 1024 * 1024, 1024)
            val providerTimeoutSeconds = int("PROVIDER_TIMEOUT_SECONDS", 40, 5)
            val totalTimeoutSeconds = int("AI_TOTAL_TIMEOUT_SECONDS", 100, 10)

            if (problems.isNotEmpty()) throw ConfigException(problems)

            return ServerConfig(
                port = port,
                appApiKey = appKey,
                providers = ordered,
                google = google,
                tokenEncryptionSecret = tokenSecret,
                corsAllowedHosts = text("CORS_ALLOWED_HOSTS")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
                trustProxyHeaders = !text("TRUST_PROXY_HEADERS").equals("false", ignoreCase = true),
                chatPerMinute = chatPerMinute,
                extractPerMinute = extractPerMinute,
                workspacePerMinute = workspacePerMinute,
                dailyAiRequestCap = dailyCap,
                maxUploadBytes = maxUpload,
                providerTimeoutMs = providerTimeoutSeconds * 1000L,
                totalAiTimeoutMs = totalTimeoutSeconds * 1000L
            )
        }
    }
}
