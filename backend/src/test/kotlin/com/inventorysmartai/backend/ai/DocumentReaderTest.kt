package com.inventorysmartai.backend.ai

import com.inventorysmartai.backend.config.AiProviderConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DocumentReaderTest {
    private val jpeg = UploadedFile("image/jpeg", byteArrayOf(1, 2, 3))
    private val pdf = UploadedFile("application/pdf", byteArrayOf(4, 5, 6))

    private fun reader(transport: FakeTransport, vararg providers: AiProviderConfig): DocumentReader =
        DocumentReader(AiGateway(providers.toList(), transport, 5_000, 20_000), transport)

    @Test
    fun `an image is read by OCR and returned as text without any chat call`() {
        runBlocking {
            val transport = FakeTransport(ocrText = "| الصنف | الكمية |\n| --- | --- |\n| حليب | 10 |") { _, _ -> chatResponse("unused") }
            val outcome = reader(transport, testProvider("mistral", ocr = true), testProvider("groq")).read(listOf(jpeg))
            assertTrue(outcome.usedOcr)
            assertEquals("mistral", outcome.providerId)
            assertTrue(outcome.text.contains("| حليب | 10 |"))
            assertEquals(listOf("mistral"), transport.ocrCalls)
            assertTrue(transport.chatCalls.isEmpty())
        }
    }

    @Test
    fun `without an OCR provider an image is transcribed by a vision model`() {
        runBlocking {
            val transport = FakeTransport { _, _ -> chatResponse("النص المنسوخ من الصورة") }
            val outcome = reader(transport, testProvider("groq")).read(listOf(jpeg))
            assertFalse(outcome.usedOcr)
            assertEquals("النص المنسوخ من الصورة", outcome.text)
            assertEquals("groq", outcome.providerId)
            assertEquals("vision-groq", transport.chatBodies.single()["model"]!!.jsonPrimitive.content)
            // the image really travelled as an image_url part
            val userContent = transport.chatBodies.single()["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonArray
            assertTrue(userContent.any { it.jsonObject["type"]?.jsonPrimitive?.content == "image_url" })
        }
    }

    @Test
    fun `empty OCR output falls through to the vision model for an image`() {
        runBlocking {
            val transport = FakeTransport(ocrText = "") { _, _ -> chatResponse("نص من الرؤية") }
            val outcome = reader(transport, testProvider("mistral", ocr = true)).read(listOf(jpeg))
            assertFalse(outcome.usedOcr)
            assertEquals("نص من الرؤية", outcome.text)
        }
    }

    @Test
    fun `a PDF that OCR cannot read asks the app for page images`() {
        runBlocking {
            val transport = FakeTransport(ocrText = "") { _, _ -> chatResponse("unused") }
            try {
                reader(transport, testProvider("mistral", ocr = true)).read(listOf(pdf))
                fail("expected PdfNeedsImagesException")
            } catch (e: PdfNeedsImagesException) {
                // expected
            }
            assertTrue(transport.chatCalls.isEmpty())
        }
    }

    @Test
    fun `unsupported types, mixed pdf and images, and too many files are refused before any AI call`() {
        runBlocking {
            val transport = FakeTransport { _, _ -> chatResponse("unused") }
            val r = reader(transport, testProvider("mistral", ocr = true))
            val codes = listOf(
                listOf(UploadedFile("text/plain", byteArrayOf(1))),
                listOf(pdf, jpeg),
                List(6) { jpeg },
                emptyList()
            ).map { files ->
                try {
                    r.read(files)
                    "no error"
                } catch (e: RequestRejectedException) {
                    e.code
                }
            }
            assertEquals(listOf("UNSUPPORTED_FILE_TYPE", "BAD_REQUEST", "TOO_MANY_FILES", "MISSING_FILE"), codes)
            assertTrue(transport.ocrCalls.isEmpty())
            assertTrue(transport.chatCalls.isEmpty())
        }
    }
}
