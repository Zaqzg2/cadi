package com.inventorysmartai.backend.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MessageSanitizerTest {

    private fun parse(text: String): JsonElement = Json.parseToJsonElement(text)

    private fun msg(role: String, content: String? = null, extra: Map<String, JsonElement> = emptyMap()): JsonObject {
        val map = LinkedHashMap<String, JsonElement>()
        map["role"] = JsonPrimitive(role)
        map["content"] = if (content == null) JsonNull else JsonPrimitive(content)
        map.putAll(extra)
        return JsonObject(map)
    }

    private fun rejection(block: () -> Unit): RequestRejectedException {
        try {
            block()
        } catch (e: RequestRejectedException) {
            return e
        }
        fail("expected a RequestRejectedException")
        throw IllegalStateException()
    }

    // ---- incoming ----

    @Test
    fun `keeps known fields and drops unknown ones`() {
        val raw = parse("""[{"role":"user","content":"hi","reasoning":"secret","extra":1}]""")
        val cleaned = MessageSanitizer.sanitizeIncoming(raw)
        assertEquals(1, cleaned.size)
        assertEquals(setOf("role", "content"), cleaned.first().keys)
    }

    @Test
    fun `tool calls and ids pass through`() {
        val raw = parse(
            """[
              {"role":"user","content":"q"},
              {"role":"assistant","content":null,"tool_calls":[{"id":"c1","type":"function","function":{"name":"x","arguments":"{}"}}]},
              {"role":"tool","tool_call_id":"c1","content":"result"}
            ]"""
        )
        val cleaned = MessageSanitizer.sanitizeIncoming(raw)
        assertEquals(3, cleaned.size)
        assertTrue(cleaned[1].containsKey("tool_calls"))
        assertEquals("c1", cleaned[2]["tool_call_id"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun `rejects an unknown role`() {
        val e = rejection { MessageSanitizer.sanitizeIncoming(parse("""[{"role":"developer","content":"x"}]""")) }
        assertEquals(400, e.status)
    }

    @Test
    fun `rejects non text content such as image parts`() {
        val raw = parse("""[{"role":"user","content":[{"type":"image_url","image_url":{"url":"data:image/png;base64,AAA"}}]}]""")
        assertEquals(400, rejection { MessageSanitizer.sanitizeIncoming(raw) }.status)
    }

    @Test
    fun `rejects missing or empty messages`() {
        assertEquals(400, rejection { MessageSanitizer.sanitizeIncoming(null) }.status)
        assertEquals(400, rejection { MessageSanitizer.sanitizeIncoming(parse("[]")) }.status)
    }

    @Test
    fun `rejects too many messages with 413`() {
        val many = JsonArray(List(MessageSanitizer.MAX_MESSAGES + 1) { msg("user", "x") })
        assertEquals(413, rejection { MessageSanitizer.sanitizeIncoming(many) }.status)
    }

    @Test
    fun `rejects a conversation that is too large`() {
        val big = "x".repeat(MessageSanitizer.MAX_TOTAL_CHARS + 1)
        assertEquals(413, rejection { MessageSanitizer.sanitizeIncoming(JsonArray(listOf(msg("user", big)))) }.status)
    }

    // ---- tools ----

    @Test
    fun `no tools is fine`() {
        assertTrue(MessageSanitizer.sanitizeTools(null).isEmpty())
        assertTrue(MessageSanitizer.sanitizeTools(JsonNull).isEmpty())
    }

    @Test
    fun `a tool needs a function name`() {
        assertEquals(400, rejection { MessageSanitizer.sanitizeTools(parse("""[{"type":"function","function":{"description":"d"}}]""")) }.status)
        assertEquals(1, MessageSanitizer.sanitizeTools(parse("""[{"type":"function","function":{"name":"getX"}}]""")).size)
    }

    // ---- per provider ----

    private fun toolHistory(): List<JsonObject> = listOf(
        msg("user", "q"),
        JsonObject(
            mapOf(
                "role" to JsonPrimitive("assistant"),
                "content" to JsonNull,
                "tool_calls" to parse("""[{"id":"call_abc","type":"function","function":{"name":"x","arguments":"{}"}},{"id":"call_def","type":"function","function":{"name":"y","arguments":"{}"}}]""")
            )
        ),
        msg("tool", "r1", mapOf("tool_call_id" to JsonPrimitive("call_abc"))),
        msg("tool", "r2", mapOf("tool_call_id" to JsonPrimitive("call_def")))
    )

    @Test
    fun `mistral gets stable nine character ids that still match`() {
        val mapped = MessageSanitizer.forProvider("mistral", toolHistory())
        val callIds = mapped[1]["tool_calls"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }
        val replyIds = listOf(mapped[2], mapped[3]).map { it["tool_call_id"]!!.jsonPrimitive.content }
        assertEquals(callIds, replyIds)
        assertEquals(2, callIds.toSet().size)
        callIds.forEach {
            assertEquals(9, it.length)
            assertTrue(it.all { c -> c.isLetterOrDigit() })
        }
    }

    @Test
    fun `other providers are untouched`() {
        val history = toolHistory()
        assertEquals(history, MessageSanitizer.forProvider("groq", history))
        assertEquals(history, MessageSanitizer.forProvider("openrouter", history))
    }

    // ---- trimming ----

    @Test
    fun `trim keeps the system prompt and starts at a user message`() {
        val history = listOf(msg("system", "sys")) +
            (1..10).flatMap { listOf(msg("user", "u$it"), msg("assistant", "a$it")) }
        val trimmed = MessageSanitizer.trimHistory(history, 5)
        assertEquals("system", trimmed.first()["role"]!!.jsonPrimitive.content)
        assertEquals("user", trimmed[1]["role"]!!.jsonPrimitive.content)
        assertTrue(trimmed.size <= 6)
    }

    @Test
    fun `trim never leaves an orphan tool reply first`() {
        val history = listOf(msg("system", "sys"), msg("user", "old")) + toolHistory() + listOf(msg("user", "new"))
        val trimmed = MessageSanitizer.trimHistory(history, 3)
        val first = trimmed.drop(1).first()["role"]!!.jsonPrimitive.content
        assertEquals("user", first)
        assertFalse(trimmed.any { it["role"]!!.jsonPrimitive.content == "tool" })
    }

    @Test
    fun `short histories are untouched`() {
        val history = listOf(msg("system", "sys"), msg("user", "hi"))
        assertEquals(history, MessageSanitizer.trimHistory(history, 10))
    }
}
