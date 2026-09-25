package com.inventorysmartai.backend.gemini

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * One function/tool declaration exactly as the Interactions API's `tools` array expects it —
 * verified shape (Sept 2026): `{"type":"function","name":...,"description":...,"parameters":<JSON
 * Schema>}`. [parameters] is a plain JSON-Schema object (type: object, properties, required).
 */
@Serializable
data class GeminiTool(
    val type: String = "function",
    val name: String,
    val description: String,
    val parameters: JsonObject
)

/** One `function_call` step Gemini returned — [arguments] are whatever the tool's declared
 *  parameters schema described, parsed here as a raw JSON object since each tool has its own
 *  shape (see gemini/ToolCatalog.kt for the per-tool argument contracts). [signature], present on
 *  Gemini 3+ tool-call steps, must be echoed back unchanged in stateless mode — carried here even
 *  though this backend always runs in stateful mode (store=true, previous_interaction_id) so it
 *  is available if that ever changes. */
data class GeminiFunctionCall(
    val id: String,
    val name: String,
    val arguments: JsonObject,
    val signature: String? = null
)

/** One already-computed tool result, ready to send back via [GeminiClient.sendFunctionResults].
 *  [resultText] is a plain string (usually JSON-encoded) — the Interactions API accepts a
 *  `result` array of typed content blocks; this backend only ever needs the `{"type":"text"}`
 *  one, since none of the Phase 4 tool catalog returns images. */
data class GeminiFunctionResultInput(
    val callId: String,
    val name: String,
    val resultText: String
)

/** Parsed, backend-friendly view of one Interactions API response — [raw] is kept for anything
 *  a caller needs that this wrapper doesn't surface (debugging, audit logging). */
data class GeminiInteractionResult(
    val interactionId: String,
    val outputText: String?,
    val functionCalls: List<GeminiFunctionCall>,
    val raw: JsonObject
)

/** Mirrors the Phase 4 spec's "Error Handling" list for anything Gemini-side. Every one of these
 *  is caught at the route layer (see routes/AssistantRoutes.kt, routes/DocumentRoutes.kt) and
 *  turned into a structured JSON error the app can show a specific Arabic message for, rather
 *  than a raw 500. */
sealed class GeminiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Timeout(cause: Throwable? = null) : GeminiException("Gemini request timed out", cause)
    class NetworkError(cause: Throwable? = null) : GeminiException("Could not reach Gemini (network unavailable)", cause)
    class RateLimited(val retryAfterSeconds: Int? = null) :
        GeminiException("Gemini quota/rate limit exceeded" + (retryAfterSeconds?.let { " (retry after ${it}s)" } ?: ""))
    /** The model's response did not parse as valid JSON, or didn't conform to the requested
     *  schema closely enough for this backend to trust it — see [GeminiClient.createStructured]. */
    class InvalidOutput(message: String) : GeminiException(message)
    class SchemaMismatch(message: String) : GeminiException(message)
    class ApiError(val statusCode: Int, message: String) : GeminiException(message)
}
