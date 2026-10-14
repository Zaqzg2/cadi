package com.inventorysmartai.app.data.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolArgsTest {

    @Test
    fun `strings are trimmed and blank ones are missing`() {
        assertEquals("x", ToolArgs.string(mapOf("k" to "  x "), "k"))
        assertNull(ToolArgs.string(mapOf("k" to "   "), "k"))
        assertNull(ToolArgs.string(mapOf("k" to 5.0), "k"))
        assertNull(ToolArgs.string(emptyMap(), "k"))
    }

    @Test
    fun `numbers are read from numbers and from numeric text, with a comma decimal`() {
        assertEquals(10.0, ToolArgs.double(mapOf("k" to 10.0), "k")!!, 0.0)
        assertEquals(10.0, ToolArgs.double(mapOf("k" to "10"), "k")!!, 0.0)
        assertEquals(2.5, ToolArgs.double(mapOf("k" to "2,5"), "k")!!, 0.0)
        assertNull(ToolArgs.double(mapOf("k" to "ten"), "k"))
        assertNull(ToolArgs.double(mapOf("k" to "NaN"), "k"))
        assertNull(ToolArgs.double(mapOf("k" to Double.POSITIVE_INFINITY), "k"))
        assertNull(ToolArgs.double(mapOf("k" to null), "k"))
    }

    @Test
    fun `ids and counts come back as whole numbers`() {
        assertEquals(7L, ToolArgs.long(mapOf("id" to 7.0), "id"))
        assertEquals(20, ToolArgs.int(mapOf("limit" to 20.0), "limit"))
        assertNull(ToolArgs.long(emptyMap(), "id"))
    }

    @Test
    fun `booleans accept true and false as text too`() {
        assertEquals(true, ToolArgs.bool(mapOf("k" to true), "k"))
        assertEquals(true, ToolArgs.bool(mapOf("k" to "TRUE"), "k"))
        assertEquals(false, ToolArgs.bool(mapOf("k" to "0"), "k"))
        assertNull(ToolArgs.bool(mapOf("k" to "maybe"), "k"))
        assertNull(ToolArgs.bool(emptyMap(), "k"))
    }

    @Test
    fun `lists and objects are returned only when they are of that shape`() {
        assertEquals(listOf<Any?>("a", 1.0), ToolArgs.list(mapOf("k" to listOf("a", 1.0)), "k"))
        assertNull(ToolArgs.list(mapOf("k" to "a"), "k"))
        assertNotNull(ToolArgs.obj(mapOf("a" to 1)))
        assertNull(ToolArgs.obj("text"))
    }

    @Test
    fun `the standard errors have the vocabulary the model expects`() {
        assertEquals("missing_argument", ToolArgs.missing("items")["error"])
        assertTrue((ToolArgs.missing("items")["message"] as String).contains("items"))
        assertEquals("invalid_argument", ToolArgs.invalid("bad")["error"])
        assertFalse(ToolArgs.invalid("bad").containsKey("missing"))
    }
}
