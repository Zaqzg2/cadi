package com.inventorysmartai.backend.routes

import com.inventorysmartai.backend.ai.AiGateway
import com.inventorysmartai.backend.ai.MessageSanitizer
import com.inventorysmartai.backend.audit.AuditLog
import com.inventorysmartai.backend.security.Bucket
import com.inventorysmartai.backend.security.RequestGuard
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

private const val MAX_CHAT_BODY_BYTES = 600L * 1024

/**
 * POST /v1/ai/chat — one assistant turn.
 *
 * Request : { "messages": [OpenAI-format messages], "tools": [OpenAI function definitions]?, "temperature": 0.2?,
 *             "maxTokens": 1500?, "sessionId": "..."? }
 * Response: { "message": { "role": "assistant", "content": "..."|null, "tool_calls": [...]? },
 *             "provider": "groq", "model": "...", "finishReason": "stop" }
 *
 * The server keeps NO conversation state: the app sends the (trimmed) history every time and runs the tools itself, so a
 * restart or a free-tier sleep can never lose a conversation, and a conversation can even continue on another provider.
 */
fun Route.aiRoutes(gateway: AiGateway, guard: RequestGuard, audit: AuditLog) {
    post("/v1/ai/chat") {
        if (!guard.admit(call, Bucket.CHAT)) return@post
        if (!guard.bodyWithin(call, MAX_CHAT_BODY_BYTES)) return@post

        val body = call.receive<JsonObject>()
        val messages = MessageSanitizer.sanitizeIncoming(body["messages"])
        val tools = MessageSanitizer.sanitizeTools(body["tools"])
        val temperature = (body["temperature"] as? JsonPrimitive)?.doubleOrNull?.coerceIn(0.0, 1.5) ?: 0.2
        val maxTokens = ((body["maxTokens"] as? JsonPrimitive)?.intOrNull ?: DEFAULT_MAX_TOKENS).coerceIn(16, MessageSanitizer.MAX_TOKENS_CAP)
        val sessionId = (body["sessionId"] as? JsonPrimitive)?.content?.take(64) ?: "unknown"

        val reply = gateway.chat(messages, tools, temperature, maxTokens)

        audit.record(
            "AI_CHAT",
            sessionId,
            mapOf(
                "provider" to reply.providerId,
                "model" to reply.model,
                "toolCalls" to reply.toolCalls.size.toString()
            )
        )

        call.respond(
            buildJsonObject {
                put(
                    "message",
                    buildJsonObject {
                        put("role", "assistant")
                        put("content", reply.text)
                        if (reply.toolCalls.isNotEmpty()) put("tool_calls", JsonArray(reply.toolCalls))
                    }
                )
                put("provider", reply.providerId)
                put("model", reply.model)
                put("finishReason", reply.finishReason)
            }
        )
    }
}

private const val DEFAULT_MAX_TOKENS = 1500
