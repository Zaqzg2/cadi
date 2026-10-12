package com.inventorysmartai.app.data.ai.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrSpaceResponseTest {

    private fun page(text: String) = """{"FileParseExitCode":1,"ParsedText":"$text","ErrorMessage":"","ErrorDetails":""}"""

    private fun failureOf(httpCode: Int, body: String): OcrSpaceException =
        assertThrows(OcrSpaceException::class.java) { OcrSpaceResponse.parse(httpCode, body) }

    @Test
    fun `the pages of a successful answer are joined`() {
        val body = """{"ParsedResults":[${page("first")},${page("second")}],"OCRExitCode":1,"IsErroredOnProcessing":false}"""
        assertEquals("first\n\nsecond", OcrSpaceResponse.parse(200, body))
    }

    @Test
    fun `a page without text is not an error`() {
        val body = """{"ParsedResults":[{"FileParseExitCode":1,"ParsedText":"","ErrorMessage":null,"ErrorDetails":null}],"OCRExitCode":1,"IsErroredOnProcessing":false}"""
        assertEquals("", OcrSpaceResponse.parse(200, body))
    }

    @Test
    fun `text that came back wins over an errored flag`() {
        val body = """{"ParsedResults":[${page("kept")}],"OCRExitCode":2,"IsErroredOnProcessing":true,"ErrorMessage":["page 2 failed"]}"""
        assertEquals("kept", OcrSpaceResponse.parse(200, body))
    }

    @Test
    fun `an error message is read whether it is a list or a string`() {
        val asList = """{"ParsedResults":null,"OCRExitCode":99,"IsErroredOnProcessing":true,"ErrorMessage":["Timed out waiting for results"]}"""
        val first = failureOf(200, asList)
        assertEquals(OcrSpaceException.Kind.FAILED, first.kind)
        assertTrue(first.detail.contains("Timed out"))

        val asText = """{"ParsedResults":null,"IsErroredOnProcessing":true,"ErrorMessage":"File size exceeds the maximum permissible limit"}"""
        assertEquals(OcrSpaceException.Kind.TOO_LARGE, failureOf(200, asText).kind)
    }

    @Test
    fun `quota and key problems are told apart`() {
        val quota = """{"ParsedResults":null,"IsErroredOnProcessing":true,"ErrorMessage":"The daily request limit was exceeded"}"""
        assertEquals(OcrSpaceException.Kind.QUOTA, failureOf(200, quota).kind)
        assertEquals(OcrSpaceException.Kind.QUOTA, failureOf(429, "").kind)
        assertEquals(OcrSpaceException.Kind.INVALID_KEY, failureOf(403, """{"ErrorMessage":"Invalid API key"}""").kind)
    }

    @Test
    fun `a non-JSON server error is a plain failure`() {
        assertEquals(OcrSpaceException.Kind.FAILED, failureOf(502, "<html>Bad gateway</html>").kind)
    }
}
