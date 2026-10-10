package com.inventorysmartai.app.data.assistant

import com.inventorysmartai.app.data.ai.provider.AiJson
import com.inventorysmartai.app.data.remote.BackendFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AssistantToolsTest {

    @Suppress("UNCHECKED_CAST")
    private fun toolNames(): List<String> =
        AssistantToolCatalog.tools.map { ((it["function"] as Map<String, Any?>)["name"]) as String }

    @Test
    fun `tool names are unique`() {
        val names = toolNames()
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `the four workspace tools are offered to the model`() {
        val names = toolNames().toSet()
        assertEquals(setOf("saveReportToDrive", "createGoogleDoc", "createCalendarEvent", "sendEmail"), AssistantToolCatalog.WORKSPACE_TOOL_NAMES)
        assertTrue(names.containsAll(AssistantToolCatalog.WORKSPACE_TOOL_NAMES))
        assertTrue("local write tool still present", "createPurchaseRequest" in names)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `every tool declares object parameters`() {
        AssistantToolCatalog.tools.forEach { tool ->
            val function = tool["function"] as Map<String, Any?>
            val parameters = function["parameters"] as Map<String, Any?>
            assertEquals("object", parameters["type"])
        }
    }

    @Test
    fun `the system prompt no longer says workspace actions are unavailable`() {
        assertFalse(AssistantToolCatalog.systemPrompt.contains("غير متاحة في هذا الوضع"))
        assertTrue(AssistantToolCatalog.systemPrompt.contains("sendEmail"))
    }

    @Test
    fun `confirmation texts name what will happen`() {
        val mail = WorkspaceToolSupport.describe("sendEmail", mapOf("to" to "a@b.com", "subject" to "تقرير"))
        assertTrue(mail.contains("a@b.com") && mail.contains("تقرير"))
        val event = WorkspaceToolSupport.describe("createCalendarEvent", mapOf("title" to "جرد", "startIso" to "2026-10-12T09:00:00+03:00", "endIso" to "2026-10-12T10:00:00+03:00"))
        assertTrue(event.contains("جرد") && event.contains("2026-10-12T09:00:00+03:00"))
        assertTrue(WorkspaceToolSupport.describe("saveReportToDrive", mapOf("title" to "المبيعات")).contains("Drive"))
        assertTrue(WorkspaceToolSupport.describe("createGoogleDoc", mapOf("title" to "المبيعات")).contains("Docs"))
    }

    @Test
    fun `an email header with a line break is refused`() {
        assertTrue(WorkspaceToolSupport.isSafeEmailHeader("تقرير المبيعات"))
        assertFalse(WorkspaceToolSupport.isSafeEmailHeader("hi\r\nBcc: someone@else.com"))
        assertFalse(WorkspaceToolSupport.isSafeEmailHeader("hi\nBcc: x"))
    }

    @Test
    fun `success results carry the link and skip nulls`() {
        val json = AiJson.parseObject(WorkspaceToolSupport.success("saved_to_drive", "fileId" to "F1", "link" to null))!!
        assertEquals("saved_to_drive", json["status"])
        assertEquals("F1", json["fileId"])
        assertFalse(json.containsKey("link"))
    }

    @Test
    fun `failures are explained to the model in a stable vocabulary`() {
        fun errorOf(t: Throwable) = AiJson.parseObject(WorkspaceToolSupport.failure(t))!!["error"]
        assertEquals("google_not_linked", errorOf(BackendFailure.Structured("GOOGLE_NOT_LINKED", "x")))
        assertEquals("google_not_linked", errorOf(BackendFailure.Structured("GOOGLE_AUTH_REFRESH_FAILED", "x")))
        assertEquals("google_not_configured", errorOf(BackendFailure.Structured("GOOGLE_NOT_CONFIGURED", "x")))
        assertEquals("backend_unreachable", errorOf(BackendFailure.NetworkUnavailable(IOException())))
        assertEquals("tool_failed", errorOf(BackendFailure.Structured("SOMETHING", "x")))
        assertEquals("tool_failed", errorOf(IllegalStateException("boom")))
    }

    @Test
    fun `the failure message is the Arabic text the person would read`() {
        val message = AiJson.parseObject(WorkspaceToolSupport.failure(BackendFailure.Structured("GOOGLE_NOT_LINKED", "يجب ربط حساب Google أولًا")))!!["message"]
        assertEquals("يجب ربط حساب Google أولًا", message)
    }

    @Test
    fun `argument helpers trim and tolerate missing values`() {
        assertEquals("x", WorkspaceToolSupport.text(mapOf("k" to "  x  "), "k"))
        assertEquals("", WorkspaceToolSupport.text(emptyMap(), "k"))
        assertEquals("", WorkspaceToolSupport.text(mapOf("k" to 5.0), "k"))
    }

    @Suppress("UNCHECKED_CAST")
    private fun namesOf(tools: List<Map<String, Any?>>): Set<String> =
        tools.map { ((it["function"] as Map<String, Any?>)["name"]) as String }.toSet()

    @Test
    fun `the file tools are offered, and the attachment ones only once something is attached`() {
        val withFiles = namesOf(AssistantToolCatalog.toolsFor(hasAttachments = true))
        val withoutFiles = namesOf(AssistantToolCatalog.toolsFor(hasAttachments = false))
        assertTrue(withFiles.containsAll(AssistantToolCatalog.FILE_TOOL_NAMES))
        assertTrue(withoutFiles.containsAll(setOf("calculate", "calculateTieredCommission", "createSpreadsheet", "buildDashboard")))
        assertTrue(withoutFiles.none { it in AssistantToolCatalog.ATTACHMENT_TOOL_NAMES })
        assertTrue("the data tools stay available either way", "getProducts" in withoutFiles)
    }

    @Test
    fun `file tools are neither workspace tools nor local write tools`() {
        assertTrue(AssistantToolCatalog.FILE_TOOL_NAMES.intersect(AssistantToolCatalog.WORKSPACE_TOOL_NAMES).isEmpty())
        assertFalse("createPurchaseRequest" in AssistantToolCatalog.FILE_TOOL_NAMES)
    }

    @Test
    fun `the system prompt tells the model how to use files`() {
        listOf("readAttachment", "queryTable", "calculate", "calculateTieredCommission", "fillForm", "createSpreadsheet", "buildDashboard")
            .forEach { assertTrue("$it should be explained", AssistantToolCatalog.systemPrompt.contains(it)) }
    }
}
