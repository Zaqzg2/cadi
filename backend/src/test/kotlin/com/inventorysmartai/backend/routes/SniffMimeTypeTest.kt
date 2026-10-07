package com.inventorysmartai.backend.routes

import org.junit.Assert.assertEquals
import org.junit.Test

class SniffMimeTypeTest {

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `recognises the four accepted formats by their magic bytes`() {
        assertEquals("application/pdf", sniffMimeType(bytes(0x25, 0x50, 0x44, 0x46, 0x2D, 0x31), "application/octet-stream"))
        assertEquals("image/jpeg", sniffMimeType(bytes(0xFF, 0xD8, 0xFF, 0xE0), null))
        assertEquals("image/png", sniffMimeType(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A), "application/octet-stream"))
        val webp = "RIFF".toByteArray() + bytes(0, 0, 0, 0) + "WEBP".toByteArray()
        assertEquals("image/webp", sniffMimeType(webp, null))
    }

    @Test
    fun `the real bytes win over a wrong declared type`() {
        assertEquals("image/jpeg", sniffMimeType(bytes(0xFF, 0xD8, 0xFF), "application/pdf"))
    }

    @Test
    fun `unknown bytes fall back to the declared type, lower-cased and without parameters`() {
        assertEquals("application/zip", sniffMimeType(bytes(0x50, 0x4B, 0x03, 0x04), "Application/ZIP; charset=binary"))
        assertEquals("application/octet-stream", sniffMimeType(bytes(1, 2, 3), null))
    }

    @Test
    fun `a RIFF file that is not WebP is not accepted as WebP`() {
        val wav = "RIFF".toByteArray() + bytes(0, 0, 0, 0) + "WAVE".toByteArray()
        assertEquals("application/octet-stream", sniffMimeType(wav, null))
    }

    @Test
    fun `an empty file has no type`() {
        assertEquals("application/octet-stream", sniffMimeType(ByteArray(0), null))
    }
}
