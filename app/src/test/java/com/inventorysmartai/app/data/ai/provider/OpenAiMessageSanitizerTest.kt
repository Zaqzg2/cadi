package com.inventorysmartai.app.data.ai.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiMessageSanitizerTest {

    private fun assistantCalling(vararg ids: String): Map<String, Any?> = mapOf(
        "role" to "assistant",
        "content" to "",
        "tool_calls" to ids.map { mapOf("id" to it, "type" to "function", "function" to mapOf("name" to "getLowStock", "arguments" to "{}")) }
    )

    private fun toolReply(id: String): Map<String, Any?> = mapOf("role" to "tool", "tool_call_id" to id, "content" to "[]")

    @Test
    fun `non-Mistral providers get the history untouched`() {
        val history = listOf(assistantCalling("call_q0wg"), toolReply("call_q0wg"))
        assertSame(history, OpenAiMessageSanitizer.forProvider(AiProviderId.GROQ, history))
    }

    @Test
    fun `Mistral ids become 9 alphanumeric characters and stay paired with their reply`() {
        val history = listOf(assistantCalling("call_q0wg", "call_zzzz"), toolReply("call_q0wg"), toolReply("call_zzzz"))
        val out = OpenAiMessageSanitizer.forProvider(AiProviderId.MISTRAL, history)

        @Suppress("UNCHECKED_CAST")
        val ids = (out[0]["tool_calls"] as List<Map<String, Any?>>).map { it["id"] as String }
        assertTrue(ids.all { it.length == 9 && it.all(Char::isLetterOrDigit) })
        assertNotEquals(ids[0], ids[1])
        assertEquals(ids[0], out[1]["tool_call_id"])
        assertEquals(ids[1], out[2]["tool_call_id"])
    }

    @Test
    fun `trim keeps the system prompt and never starts on an orphan tool reply`() {
        val system = mapOf<String, Any?>("role" to "system", "content" to "s")
        val history = buildList {
            add(system)
            add(mapOf("role" to "user", "content" to "u1"))
            add(assistantCalling("a"))
            add(toolReply("a"))
            add(mapOf("role" to "assistant", "content" to "r1"))
            add(mapOf("role" to "user", "content" to "u2"))
            add(mapOf("role" to "assistant", "content" to "r2"))
        }
        // maxMessages = 3 would start the tail on "assistant r1"; it must advance to the next user message.
        val out = OpenAiMessageSanitizer.trimHistory(history, 3)
        assertEquals("system", out.first()["role"])
        assertEquals("user", out[1]["role"])
        assertEquals("u2", out[1]["content"])
    }

    @Test
    fun `short histories are returned as they are`() {
        val history = listOf(mapOf<String, Any?>("role" to "system", "content" to "s"), mapOf("role" to "user", "content" to "u"))
        assertSame(history, OpenAiMessageSanitizer.trimHistory(history, 30))
    }
}
