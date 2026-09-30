package com.inventorysmartai.app.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiDocumentInputTest {

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private val jpeg = bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10)
    private val png = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val pdf = "%PDF-1.7\n".toByteArray(Charsets.US_ASCII)

    /** "RIFF" + 4 size bytes + the given 4-letter format tag. */
    private fun riff(format: String): ByteArray =
        "RIFF".toByteArray(Charsets.US_ASCII) + bytes(0x24, 0x00, 0x00, 0x00) + format.toByteArray(Charsets.US_ASCII)

    @Test
    fun `recognises JPEG, PNG, PDF and WebP from their first bytes`() {
        assertEquals("image/jpeg", AiDocumentInput.sniffMimeType(jpeg))
        assertEquals("image/png", AiDocumentInput.sniffMimeType(png))
        assertEquals("application/pdf", AiDocumentInput.sniffMimeType(pdf))
        assertEquals("image/webp", AiDocumentInput.sniffMimeType(riff("WEBP")))
    }

    @Test
    fun `does not mistake other RIFF files or short or empty input for an image`() {
        assertNull(AiDocumentInput.sniffMimeType(riff("WAVE")))
        assertNull(AiDocumentInput.sniffMimeType(bytes(0x52, 0x49, 0x46, 0x46)))
        assertNull(AiDocumentInput.sniffMimeType(bytes(0xFF, 0xD8)))
        assertNull(AiDocumentInput.sniffMimeType(ByteArray(0)))
        assertNull(AiDocumentInput.sniffMimeType("plain text".toByteArray()))
    }

    @Test
    fun `what the bytes are beats what the picker claimed`() {
        assertEquals("image/jpeg", AiDocumentInput.resolveMimeType(jpeg, "application/octet-stream"))
        assertEquals("image/jpeg", AiDocumentInput.resolveMimeType(jpeg, "application/pdf"))
        assertEquals("application/pdf", AiDocumentInput.resolveMimeType(pdf, null))
        assertEquals("image/png", AiDocumentInput.resolveMimeType(png, ""))
    }

    @Test
    fun `unrecognised bytes fall back to the declared type, then to PDF`() {
        val unknown = "not a known format".toByteArray()
        assertEquals("image/heic", AiDocumentInput.resolveMimeType(unknown, " Image/HEIC "))
        assertEquals("application/pdf", AiDocumentInput.resolveMimeType(unknown, "application/octet-stream"))
        assertEquals("application/pdf", AiDocumentInput.resolveMimeType(unknown, null))
        assertEquals("application/pdf", AiDocumentInput.resolveMimeType(unknown, "   "))
    }

    @Test
    fun `strips a markdown fence around JSON and leaves plain JSON alone`() {
        assertEquals("""{"a":1}""", AiDocumentInput.stripCodeFence("```json\n{\"a\":1}\n```"))
        assertEquals("""{"a":1}""", AiDocumentInput.stripCodeFence("```JSON\n{\"a\":1}\n```"))
        assertEquals("[1]", AiDocumentInput.stripCodeFence("```\n[1]\n```"))
        assertEquals("""{"a":1}""", AiDocumentInput.stripCodeFence("  {\"a\":1}  "))
        assertEquals("""{"a":1}""", AiDocumentInput.stripCodeFence("{\"a\":1}"))
    }
}
