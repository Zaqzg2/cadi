package com.inventorysmartai.app.data.importing

import com.inventorysmartai.app.domain.importing.OpenedFile
import com.inventorysmartai.app.domain.model.ImportSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/**
 * FileDetector decides which pipeline a picked file goes to. Its old plain-text guess looked at only
 * the first 8 bytes for a NUL, so a PNG or most phone JPEGs and PDFs counted as "text", were treated as
 * CSV, and never reached the AI path — the regression tests below pin exactly those files.
 */
class FileDetectorTest {

    private class FakeFile(
        override val displayName: String,
        private val content: ByteArray,
        override val mimeType: String? = null
    ) : OpenedFile {
        override val sizeBytes: Long get() = content.size.toLong()
        override fun inputStream(): InputStream = ByteArrayInputStream(content)
    }

    private class UnreadableFile : OpenedFile {
        override val displayName = "broken.bin"
        override val sizeBytes = 10L
        override val mimeType: String? = null
        override fun inputStream(): InputStream = throw IOException("permission denied")
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun detect(name: String, content: ByteArray, mime: String? = null): FileDetectionResult =
        FileDetector.detect(FakeFile(name, content, mime))

    private fun recognized(type: ImportSourceType) = FileDetectionResult.Recognized(type)

    // ---- photos and PDFs (the regression) ----

    @Test
    fun `a PNG screenshot is an image, not text`() {
        // 89 50 4E 47 0D 0A 1A 0A — no NUL byte in the first 8 bytes.
        val png = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D)
        assertEquals(recognized(ImportSourceType.IMAGE), detect("Screenshot_2026.png", png, "image/png"))
    }

    @Test
    fun `a JPEG is an image, with or without a NUL in its first bytes`() {
        val jfif = bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00)
        val exif = bytes(0xFF, 0xD8, 0xFF, 0xE1, 0x2A, 0x3C, 0x45, 0x78, 0x69, 0x66, 0x00, 0x00) // no NUL in the first 8
        assertEquals(recognized(ImportSourceType.IMAGE), detect("invoice.jpg", jfif, "image/jpeg"))
        assertEquals(recognized(ImportSourceType.IMAGE), detect("IMG_0001.jpeg", exif, "image/jpeg"))
    }

    @Test
    fun `a PDF is a PDF, not text`() {
        val pdf = "%PDF-1.7\n%âãÏÓ\n1 0 obj".toByteArray(Charsets.ISO_8859_1)
        assertEquals(recognized(ImportSourceType.PDF), detect("invoice.pdf", pdf, "application/pdf"))
    }

    @Test
    fun `WebP and HEIC phone photos are images`() {
        val webp = "RIFF".toByteArray() + bytes(0x24, 0x00, 0x00, 0x00) + "WEBPVP8 ".toByteArray()
        val heic = bytes(0x00, 0x00, 0x00, 0x18) + "ftypheic".toByteArray() + ByteArray(8)
        assertEquals(recognized(ImportSourceType.IMAGE), detect("photo.webp", webp))
        assertEquals(recognized(ImportSourceType.IMAGE), detect("photo.heic", heic))
    }

    @Test
    fun `what the bytes are beats the extension and the reported MIME type`() {
        val png = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00)
        assertEquals(recognized(ImportSourceType.IMAGE), detect("renamed.csv", png, "text/csv"))
        assertEquals(recognized(ImportSourceType.IMAGE), detect("renamed.xlsx", png, "application/octet-stream"))
    }

    // ---- spreadsheets and text (unchanged behaviour) ----

    @Test
    fun `an xlsx is Excel and a legacy xls is rejected`() {
        val zip = bytes(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00)
        assertEquals(recognized(ImportSourceType.EXCEL), detect("book.xlsx", zip))
        val ole = bytes(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)
        assertTrue(detect("old.xls", ole) is FileDetectionResult.Unsupported)
    }

    @Test
    fun `CSV is recognised by extension, by MIME type, and as a last resort by looking like text`() {
        val csv = "name,qty\napple,3\n".toByteArray()
        assertEquals(recognized(ImportSourceType.CSV), detect("stock.csv", csv))
        assertEquals(recognized(ImportSourceType.CSV), detect("export", csv, "text/csv"))
        assertEquals(recognized(ImportSourceType.CSV), detect("stock.dat", csv))
        val arabic = "الصنف,الكمية\nأرز,3\n".toByteArray(Charsets.UTF_8)
        assertEquals(recognized(ImportSourceType.CSV), detect("مخزون.txt", arabic))
    }

    // ---- everything else ----

    @Test
    fun `other binary formats are unsupported instead of being parsed as text`() {
        val gif = "GIF89a".toByteArray() + bytes(0x01, 0x00, 0x01, 0x00, 0x80, 0x00, 0x00)
        assertTrue(detect("anim.gif", gif) is FileDetectionResult.Unsupported)
        val avif = bytes(0x00, 0x00, 0x00, 0x1C) + "ftypavif".toByteArray() + ByteArray(8)
        assertTrue(detect("photo.avif", avif) is FileDetectionResult.Unsupported)
        assertTrue(detect("blob.bin", bytes(0x01, 0x02, 0x00, 0x04, 0x05)) is FileDetectionResult.Unsupported)
    }

    @Test
    fun `empty and unreadable files are reported as such`() {
        assertEquals(FileDetectionResult.Empty, detect("empty.csv", ByteArray(0)))
        assertTrue(FileDetector.detect(UnreadableFile()) is FileDetectionResult.Unreadable)
    }
}
