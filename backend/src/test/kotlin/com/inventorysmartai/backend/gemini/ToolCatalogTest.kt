package com.inventorysmartai.backend.gemini

import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCatalogTest {

    /** Verbatim from the Phase 4 brief's "FUNCTION CALLING" list — this test fails loudly if a
     *  tool is ever renamed or dropped without updating the brief-derived contract the app relies
     *  on for its own [ToolExecutionSite.LOCAL] dispatch table. */
    private val expectedToolNames = setOf(
        "getProducts", "getProduct", "searchProducts", "getInventory", "getBranchInventory",
        "getLowStock", "getZeroStock", "getExpiredItems", "getNearExpiryItems", "getInventoryMovements",
        "getCounting", "getPurchaseRequests", "getPurchaseRequest", "getSalesInvoices", "getSalesInvoice",
        "getGoals", "getGoalProgress", "compareBranches", "createPurchaseRequest", "createReport",
        "saveReportToDrive", "createGoogleDoc", "createCalendarEvent", "sendEmail"
    )

    @Test
    fun `every tool from the spec is present, and nothing extra was invented`() {
        assertEquals(expectedToolNames, ToolCatalog.all.map { it.name }.toSet())
    }

    @Test
    fun `no duplicate tool names`() {
        val names = ToolCatalog.all.map { it.name }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `every tool's parameters schema is a well-formed JSON Schema object`() {
        ToolCatalog.all.forEach { tool ->
            val type = tool.parameters["type"]?.jsonPrimitive?.content
            assertEquals("Tool \"${tool.name}\" parameters must be a JSON Schema object", "object", type)
            assertTrue("Tool \"${tool.name}\" must declare properties", tool.parameters.containsKey("properties"))
        }
    }

    @Test
    fun `write tools require confirmation, and only write tools do`() {
        val expectedWriteTools = setOf(
            "createPurchaseRequest", "saveReportToDrive", "createGoogleDoc", "createCalendarEvent", "sendEmail"
        )
        assertEquals(expectedWriteTools, ToolCatalog.writeToolNames)
    }

    @Test
    fun `backend-site tools are exactly the four Google Workspace write actions`() {
        assertEquals(setOf("saveReportToDrive", "createGoogleDoc", "createCalendarEvent", "sendEmail"), ToolCatalog.backendToolNames)
    }

    @Test
    fun `local tools never overlap with backend tools`() {
        assertTrue(ToolCatalog.localToolNames.intersect(ToolCatalog.backendToolNames).isEmpty())
    }

    @Test
    fun `find returns null for an unknown tool name rather than throwing`() {
        assertFalse(ToolCatalog.all.any { it.name == "deleteEverything" })
        assertNull(ToolCatalog.find("deleteEverything"))
    }
}
