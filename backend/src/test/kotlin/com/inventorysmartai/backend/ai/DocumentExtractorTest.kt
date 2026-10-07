package com.inventorysmartai.backend.ai

import com.inventorysmartai.backend.config.AiProviderConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DocumentExtractorTest {
    private val validDocument = """{"documentType":"something-else","rows":[],"documentWarnings":[]}"""
    private val jpeg = UploadedFile("image/jpeg", byteArrayOf(1, 2, 3))
    private val pdf = UploadedFile("application/pdf", byteArrayOf(4, 5, 6))

    private fun extractor(transport: FakeTransport, vararg providers: AiProviderConfig): DocumentExtractor =
        DocumentExtractor(AiGateway(providers.toList(), transport, 5_000, 20_000), transport)

    // ---- parseDocument ----

    @Test
    fun `parseDocument accepts fenced json with chatter and forces the document type`() {
        val doc = DocumentExtractor.parseDocument("Sure!\n```json\n$validDocument\n```", ExtractionDocumentType.PRODUCTS)
        assertEquals("PRODUCTS", doc["documentType"]!!.jsonPrimitive.content)
        assertTrue(doc["rows"] is JsonArray)
    }

    @Test
    fun `parseDocument rejects text without json`() {
        try {
            DocumentExtractor.parseDocument("I cannot read this", ExtractionDocumentType.PRODUCTS)
            fail("expected ProviderInvalidOutputException")
        } catch (e: ProviderInvalidOutputException) {
            // expected
        }
    }

    @Test
    fun `parseDocument rejects json without a rows array`() {
        try {
            DocumentExtractor.parseDocument("""{"documentType":"PRODUCTS"}""", ExtractionDocumentType.PRODUCTS)
            fail("expected ProviderInvalidOutputException")
        } catch (e: ProviderInvalidOutputException) {
            // expected
        }
    }

    @Test
    fun `the instruction embeds the schema and the Arabic note`() {
        val text = DocumentExtractor.buildInstruction(ExtractionDocumentType.SALES_INVOICES)
        assertTrue(text.contains("INVOICE_NUMBER"))
        assertTrue(text.contains("Arabic"))
    }

    // ---- extract ----

    @Test
    fun `an image is read by OCR then structured by text`() = runBlocking {
        val transport = FakeTransport(ocrText = "اسم المنتج: شاي") { _, _ -> chatResponse(validDocument) }
        val outcome = extractor(transport, testProvider("mistral", ocr = true), testProvider("groq"))
            .extract(ExtractionDocumentType.PRODUCTS, listOf(jpeg))
        assertTrue(outcome.usedOcr)
        assertEquals(listOf("mistral"), transport.ocrCalls)
        assertEquals("mistral", outcome.providerId)
        assertEquals("text-mistral", outcome.model)
        // The OCR text, not the image, was sent to the model.
        val userContent = transport.chatBodies.single()["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonPrimitive.content
        assertTrue(userContent.contains("اسم المنتج: شاي"))
    }

    @Test
    fun `without OCR an image goes to a vision model`() = runBlocking {
        val transport = FakeTransport { _, _ -> chatResponse(validDocument) }
        val outcome = extractor(transport, testProvider("groq")).extract(ExtractionDocumentType.PRODUCTS, listOf(jpeg))
        assertFalse(outcome.usedOcr)
        assertEquals("vision-groq", outcome.model)
        val content = transport.chatBodies.single()["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonArray
        assertTrue(content.any { it.jsonObject["type"]!!.jsonPrimitive.content == "image_url" })
    }

    @Test
    fun `a PDF the server cannot read asks the app for page images`() = runBlocking {
        val transport = FakeTransport { _, _ -> chatResponse(validDocument) }
        try {
            extractor(transport, testProvider("groq")).extract(ExtractionDocumentType.PRODUCTS, listOf(pdf))
            fail("expected PdfNeedsImagesException")
        } catch (e: PdfNeedsImagesException) {
            assertTrue(transport.chatCalls.isEmpty())
        }
    }

    @Test
    fun `bad json from a provider falls through to the next provider`() = runBlocking {
        val transport = FakeTransport { p, _ -> if (p.id == "mistral") chatResponse("not json at all") else chatResponse(validDocument) }
        val outcome = extractor(transport, testProvider("mistral", ocr = true), testProvider("groq"))
            .extract(ExtractionDocumentType.PRODUCTS, listOf(jpeg))
        assertEquals("groq", outcome.providerId)
    }

    @Test
    fun `an image still works when OCR text could not be structured`() = runBlocking {
        // Both text attempts return garbage, the vision attempt (second call round) returns a valid document.
        var round = 0
        val transport = FakeTransport { _, body ->
            val isVision = body["model"]!!.jsonPrimitive.content.startsWith("vision-")
            round++
            if (isVision) chatResponse(validDocument) else chatResponse("garbage")
        }
        val outcome = extractor(transport, testProvider("mistral", ocr = true)).extract(ExtractionDocumentType.PRODUCTS, listOf(jpeg))
        assertFalse(outcome.usedOcr)
        assertTrue(round >= 2)
    }

    @Test
    fun `a PDF whose text could not be structured reports why instead of asking for images`() = runBlocking {
        val transport = FakeTransport { _, _ -> chatResponse("garbage") }
        try {
            extractor(transport, testProvider("mistral", ocr = true)).extract(ExtractionDocumentType.PRODUCTS, listOf(pdf))
            fail("expected an exception")
        } catch (e: AiUnavailableException) {
            assertFalse(e.allRateLimited)
        }
    }

    // ---- validation ----

    @Test
    fun `unsupported types, mixes and floods are refused before any AI call`() = runBlocking {
        val transport = FakeTransport { _, _ -> chatResponse(validDocument) }
        val extractor = extractor(transport, testProvider("groq"))

        suspend fun status(files: List<UploadedFile>): Int = try {
            extractor.extract(ExtractionDocumentType.PRODUCTS, files)
            -1
        } catch (e: RequestRejectedException) {
            e.status
        }

        assertEquals(415, status(listOf(UploadedFile("application/zip", byteArrayOf(1)))))
        assertEquals(400, status(listOf(pdf, jpeg)))
        assertEquals(400, status(emptyList()))
        assertEquals(400, status(List(DocumentExtractor.MAX_FILES + 1) { jpeg }))
        assertTrue(transport.chatCalls.isEmpty())
    }
}
