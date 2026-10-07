package com.inventorysmartai.backend.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JsonTextTest {

    @Test
    fun `strips a json fence`() {
        assertEquals("{\"a\":1}", JsonText.stripCodeFence("```json\n{\"a\":1}\n```"))
        assertEquals("{\"a\":1}", JsonText.stripCodeFence("```\n{\"a\":1}\n```"))
    }

    @Test
    fun `unfenced text is only trimmed`() {
        assertEquals("{\"a\":1}", JsonText.stripCodeFence("  {\"a\":1}  "))
    }

    @Test
    fun `finds the object inside surrounding prose`() {
        assertEquals("{\"a\":{\"b\":2}}", JsonText.extractObjectText("Here you go: {\"a\":{\"b\":2}} hope it helps"))
    }

    @Test
    fun `braces inside strings do not confuse it`() {
        val raw = "{\"text\":\"a } b { c\",\"n\":1} trailing"
        assertEquals("{\"text\":\"a } b { c\",\"n\":1}", JsonText.extractObjectText(raw))
    }

    @Test
    fun `escaped quotes inside strings are honoured`() {
        val raw = "{\"q\":\"say \\\"hi}\\\"\"}"
        assertEquals(raw, JsonText.extractObjectText(raw))
    }

    @Test
    fun `unbalanced or missing objects give null`() {
        assertNull(JsonText.extractObjectText("{\"a\":1"))
        assertNull(JsonText.extractObjectText("no json here"))
    }
}
