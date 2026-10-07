package com.inventorysmartai.backend.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** A provider's answer reduced to what callers need. [toolCalls] are in the standard OpenAI shape. */
data class ParsedChat(val text: String?, val toolCalls: List<JsonObject>, val finishReason: String?)

/** Building the OpenAI-style request and normalising the answer — pure, so it is unit-tested without any network. */
object ChatParsing {

    fun buildBody(
        model: String,
        messages: List<JsonObject>,
        tools: List<JsonObject>,
        jsonMode: Boolean,
        temperature: Double,
        maxTokens: Int?
    ): JsonObject = buildJsonObject {
        put("model", model)
        put("messages", JsonArray(messages))
        put("temperature", temperature)
        if (maxTokens != null) put("max_tokens", maxTokens)
        if (tools.isNotEmpty()) {
            put("tools", JsonArray(tools))
            put("tool_choice", "auto")
        }
        if (jsonMode) put("response_format", buildJsonObject { put("type", "json_object") })
    }

    fun parse(providerId: String, root: JsonObject): ParsedChat {
        val choice = (root["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
        val message = choice?.get("message") as? JsonObject ?: throw ProviderEmptyException(providerId)

        val text = when (val content = message["content"]) {
            is JsonPrimitive -> content.contentOrNull
            is JsonArray -> content.mapNotNull { ((it as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNull }.joinToString("")
            else -> null
        }?.takeIf { it.isNotBlank() }

        val rawCalls = (message["tool_calls"] as? JsonArray) ?: JsonArray(emptyList())
        val calls = rawCalls.mapIndexedNotNull { index, raw -> normalizeToolCall(raw, index) }

        if (text == null && calls.isEmpty()) throw ProviderEmptyException(providerId)
        return ParsedChat(text, calls, (choice?.get("finish_reason") as? JsonPrimitive)?.contentOrNull)
    }

    private fun normalizeToolCall(raw: JsonElement, index: Int): JsonObject? {
        val call = raw as? JsonObject ?: return null
        val function = call["function"] as? JsonObject ?: return null
        val name = (function["name"] as? JsonPrimitive)?.contentOrNull ?: return null
        val arguments = when (val a = function["arguments"]) {
            null -> "{}"
            is JsonPrimitive -> (a.contentOrNull ?: "{}").ifBlank { "{}" }
            else -> a.toString() // some providers return the object itself instead of a JSON string
        }
        val id = (call["id"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: "call_${System.nanoTime()}_$index"
        return buildJsonObject {
            put("id", id)
            put("type", "function")
            put("function", buildJsonObject {
                put("name", name)
                put("arguments", arguments)
            })
        }
    }
}
