package com.inventorysmartai.backend.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ChatParsingTest {

    private fun root(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    @Test
    fun `parses a plain text answer`() {
        val parsed = ChatParsing.parse("groq", root("""{"choices":[{"message":{"role":"assistant","content":"مرحبا"},"finish_reason":"stop"}]}"""))
        assertEquals("مرحبا", parsed.text)
        assertTrue(parsed.toolCalls.isEmpty())
        assertEquals("stop", parsed.finishReason)
    }

    @Test
    fun `content given as parts is joined`() {
        val parsed = ChatParsing.parse("x", root("""{"choices":[{"message":{"content":[{"type":"text","text":"a"},{"type":"text","text":"b"}]}}]}"""))
        assertEquals("ab", parsed.text)
    }

    @Test
    fun `tool calls are normalised`() {
        val parsed = ChatParsing.parse(
            "groq",
            root(
                """{"choices":[{"message":{"content":null,"tool_calls":[
                    {"id":"call_1","type":"function","function":{"name":"getProduct","arguments":"{\"id\":1}"}},
                    {"function":{"name":"listAll","arguments":{"limit":5}}},
                    {"id":"call_3","function":{"name":"noArgs"}}
                ]}}]}"""
            )
        )
        assertNull(parsed.text)
        assertEquals(3, parsed.toolCalls.size)

        val first = parsed.toolCalls[0]
        assertEquals("call_1", first["id"]!!.jsonPrimitive.content)
        assertEquals("function", first["type"]!!.jsonPrimitive.content)
        assertEquals("getProduct", first["function"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("{\"id\":1}", first["function"]!!.jsonObject["arguments"]!!.jsonPrimitive.content)

        val second = parsed.toolCalls[1]
        assertTrue("a missing id is generated", second["id"]!!.jsonPrimitive.content.startsWith("call_"))
        assertEquals("{\"limit\":5}", second["function"]!!.jsonObject["arguments"]!!.jsonPrimitive.content)

        assertEquals("{}", parsed.toolCalls[2]["function"]!!.jsonObject["arguments"]!!.jsonPrimitive.content)
    }

    @Test
    fun `an answer with neither text nor tool calls is empty`() {
        try {
            ChatParsing.parse("groq", root("""{"choices":[{"message":{"content":"   "}}]}"""))
            fail("expected ProviderEmptyException")
        } catch (e: ProviderEmptyException) {
            assertEquals("groq", e.providerId)
        }
        try {
            ChatParsing.parse("groq", root("""{"choices":[]}"""))
            fail("expected ProviderEmptyException")
        } catch (e: ProviderEmptyException) {
            // expected
        }
    }

    @Test
    fun `request body carries tools and json mode only when asked`() {
        val plain = ChatParsing.buildBody("m", listOf(JsonObject(mapOf("role" to JsonPrimitive("user"), "content" to JsonPrimitive("x")))), emptyList(), false, 0.2, null)
        assertEquals("m", plain["model"]!!.jsonPrimitive.content)
        assertFalse(plain.containsKey("tools"))
        assertFalse(plain.containsKey("tool_choice"))
        assertFalse(plain.containsKey("response_format"))
        assertFalse(plain.containsKey("max_tokens"))

        val tool = Json.parseToJsonElement("""{"type":"function","function":{"name":"t"}}""").jsonObject
        val rich = ChatParsing.buildBody("m", emptyList(), listOf(tool), true, 0.0, 800)
        assertEquals("auto", rich["tool_choice"]!!.jsonPrimitive.content)
        assertEquals("json_object", rich["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("800", rich["max_tokens"]!!.jsonPrimitive.contentOrNull)
    }
}
