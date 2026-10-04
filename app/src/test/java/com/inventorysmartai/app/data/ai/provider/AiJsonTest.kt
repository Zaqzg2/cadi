package com.inventorysmartai.app.data.ai.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiJsonTest {

    @Test
    fun `round trips nested maps and lists including Arabic text`() {
        val value = mapOf("a" to listOf(1, 2), "b" to mapOf("c" to "فاتورة"))
        val parsed = AiJson.parseObject(AiJson.toJson(value))!!
        assertEquals("فاتورة", ((parsed["b"]) as Map<*, *>)["c"])
        assertEquals(listOf(1.0, 2.0), parsed["a"]) // Moshi reads every JSON number as Double
    }

    @Test
    fun `extracts the JSON object from a fenced or chatty answer`() {
        assertEquals("""{"x":1}""", AiJson.extractObjectText("```json\n{\"x\":1}\n```"))
        assertEquals("""{"x":1}""", AiJson.extractObjectText("Here you go: {\"x\":1} — done"))
    }

    @Test
    fun `drops a leading think block`() {
        assertEquals("""{"x":2}""", AiJson.extractObjectText("<think>maybe {not json}</think>\n{\"x\":2}"))
    }

    @Test
    fun `returns null when there is no object`() {
        assertNull(AiJson.extractObjectText("no json here"))
    }
}
