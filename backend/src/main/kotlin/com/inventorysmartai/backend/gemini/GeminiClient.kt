package com.inventorysmartai.backend.gemini

import com.inventorysmartai.backend.config.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.IOException

/**
 * Thin wrapper around Gemini's **Interactions API** — verified (Sept 2026) as the current,
 * GA-since-June-2026, Google-recommended interface for new Gemini integrations, superseding
 * `generateContent` for this kind of multimodal + tool-calling + structured-output work (see the
 * Phase 4 section of README.md for the sources this was checked against; the Phase 4 brief itself
 * calls for exactly this API). Endpoint, headers, and every field name below were confirmed
 * against Google's own docs/quickstart samples, not reconstructed from the older generateContent
 * shape — the two APIs are NOT wire-compatible.
 *
 * Deliberately built on raw JSON (kotlinx.serialization's [JsonElement] tree) rather than a fixed
 * set of strictly-typed request/response data classes: the `input` array is polymorphic per
 * content-part type (text/image/document/function_result) and the `steps` array is polymorphic
 * per step type (user_input/model_output/function_call/...). Modelling that faithfully with sealed
 * classes needs a custom contextual/polymorphic serializer per field that cannot be exercised
 * against a real compiler in the environment this was written in; manual JSON-tree construction
 * and defensive field lookups (`?.let`, `jsonObject["x"]?.jsonPrimitive?.contentOrNull`) fail soft
 * on an unexpected/missing field instead of throwing a deserialization exception on first use.
 */
class GeminiClient(private val http: HttpClient) {

    private val interactionsUrl = "${AppConfig.geminiBaseUrl}/v1beta/interactions"

    /**
     * One Interactions API call. [input] is either a plain user message ([inputText]) or a
     * pre-built content array (see [GeminiContent] for the part builders) for multimodal/
     * function-result turns. [store]=null lets the API default to true (server-side state, the
     * mode this backend uses everywhere — see [previousInteractionId]); pass false only for a
     * one-shot extraction call that never needs a follow-up turn, to avoid leaving Gemini-side
     * state around for no reason.
     *
     * Request/connect timeouts are NOT configured per call here — they come from the shared
     * [HttpClient]'s own `HttpTimeout` plugin config (see http/HttpClients.kt), one generous
     * default (document extraction can legitimately take longer than a chat turn) rather than a
     * per-request override built on a Ktor DSL shape this environment could not compile-check.
     */
    suspend fun createInteraction(
        input: JsonElement,
        tools: List<GeminiTool> = emptyList(),
        previousInteractionId: String? = null,
        store: Boolean? = null,
        responseFormat: JsonElement? = null,
        model: String = AppConfig.geminiModel
    ): GeminiInteractionResult {
        val body = buildJsonObject {
            put("model", model)
            put("input", input)
            if (tools.isNotEmpty()) {
                putJsonArray("tools") {
                    tools.forEach { tool ->
                        add(
                            buildJsonObject {
                                put("type", tool.type)
                                put("name", tool.name)
                                put("description", tool.description)
                                put("parameters", tool.parameters)
                            }
                        )
                    }
                }
            }
            previousInteractionId?.let { put("previous_interaction_id", it) }
            store?.let { put("store", it) }
            responseFormat?.let { put("response_format", it) }
        }

        val response = try {
            http.post(interactionsUrl) {
                contentType(ContentType.Application.Json)
                header("x-goog-api-key", AppConfig.geminiApiKey)
                header("Api-Revision", AppConfig.geminiApiRevision)
                setBody(body)
            }
        } catch (e: HttpRequestTimeoutException) {
            throw GeminiException.Timeout(e)
        } catch (e: IOException) {
            throw GeminiException.NetworkError(e)
        }

        return parseInteractionResponse(response)
    }

    /** Convenience for the common "plain text turn, no tools" case (e.g. a final confirmation
     *  message with nothing left to call). */
    suspend fun createInteraction(inputText: String, previousInteractionId: String? = null): GeminiInteractionResult =
        createInteraction(input = GeminiContent.textInput(inputText), previousInteractionId = previousInteractionId)

    /** Continues a conversation after the app (or this backend, for Workspace tools) executed one
     *  or more tool calls Gemini asked for. [results] must cover every `function_call` step from
     *  the previous turn — Gemini's own docs note a turn with unanswered tool calls will not
     *  proceed to a final answer. */
    suspend fun sendFunctionResults(
        results: List<GeminiFunctionResultInput>,
        tools: List<GeminiTool>,
        previousInteractionId: String,
        model: String = AppConfig.geminiModel
    ): GeminiInteractionResult {
        val input = buildJsonArray {
            results.forEach { r ->
                add(
                    buildJsonObject {
                        put("type", "function_result")
                        put("call_id", r.callId)
                        put("name", r.name)
                        putJsonArray("result") {
                            add(buildJsonObject { put("type", "text"); put("text", r.resultText) })
                        }
                    }
                )
            }
        }
        return createInteraction(input = input, tools = tools, previousInteractionId = previousInteractionId)
    }

    /**
     * One-shot structured-data extraction: no tools, `store=false` (a document-extraction call
     * never needs a follow-up turn), [jsonSchema] enforced via `response_format`. Returns the raw
     * JSON text Gemini produced — callers parse it against their own Kotlin model (see
     * gemini/ExtractionSchemas.kt) and run their OWN deterministic validation on top; this method
     * only guarantees "valid JSON, schema-shaped", never business correctness (Phase 4 spec:
     * "AI suggestions do not override deterministic validation").
     */
    suspend fun createStructured(
        input: JsonElement,
        jsonSchema: JsonObject,
        schemaName: String,
        model: String = AppConfig.geminiModel
    ): JsonObject {
        val responseFormat = buildJsonArray {
            add(
                buildJsonObject {
                    put("type", "text")
                    put("mime_type", "application/json")
                    put("schema", jsonSchema)
                }
            )
        }
        val result = createInteraction(
            input = input,
            store = false,
            responseFormat = responseFormat,
            model = model
        )
        val text = result.outputText
            ?: throw GeminiException.InvalidOutput("Gemini returned no output_text for schema \"$schemaName\"")
        val parsed = try {
            Json.parseToJsonElement(text)
        } catch (e: Exception) {
            throw GeminiException.InvalidOutput("Gemini's output for \"$schemaName\" was not valid JSON: ${e.message}")
        }
        return parsed as? JsonObject
            ?: throw GeminiException.SchemaMismatch("Gemini's output for \"$schemaName\" was valid JSON but not a JSON object")
    }

    private suspend fun parseInteractionResponse(response: HttpResponse): GeminiInteractionResult {
        val bodyText = response.bodyAsText()

        if (response.status == HttpStatusCode.TooManyRequests) {
            val retryAfter = response.headers["Retry-After"]?.toIntOrNull()
            throw GeminiException.RateLimited(retryAfter)
        }
        if (!response.status.isSuccess()) {
            throw GeminiException.ApiError(response.status.value, "Gemini API error ${response.status.value}: $bodyText")
        }

        val json = try {
            Json.parseToJsonElement(bodyText).jsonObject
        } catch (e: Exception) {
            throw GeminiException.InvalidOutput("Gemini's response body was not valid JSON: ${e.message}")
        }

        val interactionId = json["id"]?.jsonPrimitive?.contentOrNull ?: ""
        val outputTextDirect = json["output_text"]?.jsonPrimitive?.contentOrNull

        val steps: JsonArray = json["steps"]?.jsonArray ?: JsonArray(emptyList())
        val functionCalls = mutableListOf<GeminiFunctionCall>()
        val modelOutputText = StringBuilder()

        steps.forEach { stepElement ->
            val step = stepElement as? JsonObject ?: return@forEach
            when (step["type"]?.jsonPrimitive?.contentOrNull) {
                "function_call" -> {
                    val id = step["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                    val name = step["name"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                    val arguments = step["arguments"] as? JsonObject ?: JsonObject(emptyMap())
                    val signature = step["signature"]?.jsonPrimitive?.contentOrNull
                    functionCalls += GeminiFunctionCall(id, name, arguments, signature)
                }
                "model_output" -> {
                    val content = step["content"] as? JsonArray ?: return@forEach
                    content.forEach { block ->
                        val obj = block as? JsonObject ?: return@forEach
                        if (obj["type"]?.jsonPrimitive?.contentOrNull == "text") {
                            obj["text"]?.jsonPrimitive?.contentOrNull?.let { modelOutputText.append(it) }
                        }
                    }
                }
                else -> Unit // user_input / built-in tool steps / anything future — not needed here
            }
        }

        val outputText = outputTextDirect ?: modelOutputText.toString().ifBlank { null }
        return GeminiInteractionResult(
            interactionId = interactionId,
            outputText = outputText,
            functionCalls = functionCalls,
            raw = json
        )
    }
}

/** Builders for the Interactions API's polymorphic `input` content parts — verified shapes only
 *  (see GeminiClient's class doc). Kept separate from [GeminiClient] so gemini/DocumentExtraction*
 *  code building multimodal prompts doesn't need to know anything about HTTP. */
object GeminiContent {
    fun textInput(text: String): JsonElement = buildJsonArray {
        add(buildJsonObject { put("type", "text"); put("text", text) })
    }

    fun text(text: String): JsonObject = buildJsonObject { put("type", "text"); put("text", text) }

    /** Inline base64 image — fine up to a few MB; Gemini's own docs cap inline document/image
     *  payloads at 50MB before the Files API becomes necessary, which this backend does not need
     *  for the receipt/invoice/purchase-request photos this app deals with. */
    fun imageBase64(base64Data: String, mimeType: String): JsonObject = buildJsonObject {
        put("type", "image"); put("data", base64Data); put("mime_type", mimeType)
    }

    fun documentBase64(base64Data: String, mimeType: String): JsonObject = buildJsonObject {
        put("type", "document"); put("data", base64Data); put("mime_type", mimeType)
    }

    fun array(vararg parts: JsonObject): JsonElement = buildJsonArray { parts.forEach { add(it) } }
    fun array(parts: List<JsonObject>): JsonElement = buildJsonArray { parts.forEach { add(it) } }
}
