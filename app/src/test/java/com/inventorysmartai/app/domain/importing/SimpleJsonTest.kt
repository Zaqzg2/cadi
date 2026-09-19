package com.inventorysmartai.app.domain.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimpleJsonTest {

    @Test
    fun `encodes and decodes a simple map round-trip`() {
        val map = mapOf("a" to "1", "b" to "2")
        val decoded = SimpleJson.decodeMap(SimpleJson.encodeMap(map))
        assertEquals(map, decoded)
    }

    @Test
    fun `Arabic keys and values survive a round trip unescaped and readable`() {
        val map = mapOf("اسم الصنف" to "أرز بسمتي", "الباركود" to "123456")
        val json = SimpleJson.encodeMap(map)
        assertTrue("Arabic text should not be escaped away", json.contains("أرز بسمتي"))
        assertEquals(map, SimpleJson.decodeMap(json))
    }

    @Test
    fun `a quote and a backslash in a value are escaped and correctly restored`() {
        val map = mapOf("k" to "قيمة \"بين علامتي اقتباس\" و\\شرطة مائلة")
        val decoded = SimpleJson.decodeMap(SimpleJson.encodeMap(map))
        assertEquals(map["k"], decoded["k"])
    }

    @Test
    fun `null values encode as JSON null and decode by being absent from the map`() {
        val json = SimpleJson.encodeMap(mapOf("a" to "1", "b" to null))
        val decoded = SimpleJson.decodeMap(json)
        assertEquals("1", decoded["a"])
        assertEquals(false, decoded.containsKey("b"))
    }

    @Test
    fun `empty map encodes to an empty JSON object and decodes back to empty`() {
        assertEquals("{}", SimpleJson.encodeMap(emptyMap()))
        assertEquals(emptyMap<String, String>(), SimpleJson.decodeMap("{}"))
    }

    @Test
    fun `malformed JSON decodes to an empty map instead of throwing`() {
        assertEquals(emptyMap<String, String>(), SimpleJson.decodeMap("{not json"))
        assertEquals(emptyMap<String, String>(), SimpleJson.decodeMap(null))
    }

    @Test
    fun `encodes and decodes a list of strings round-trip`() {
        val list = listOf("تحذير أول", "تحذير ثانٍ")
        assertEquals(list, SimpleJson.decodeList(SimpleJson.encodeList(list)))
    }

    @Test
    fun `empty list encodes to an empty JSON array and decodes back to empty`() {
        assertEquals("[]", SimpleJson.encodeList(emptyList()))
        assertTrue(SimpleJson.decodeList("[]").isEmpty())
    }

    @Test
    fun `malformed JSON array decodes to an empty list instead of throwing`() {
        assertTrue(SimpleJson.decodeList("[oops").isEmpty())
        assertTrue(SimpleJson.decodeList(null).isEmpty())
    }
}
