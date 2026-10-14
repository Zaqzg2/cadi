package com.inventorysmartai.app.data.assistant

import com.inventorysmartai.app.navigation.AssistantScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartToolsCatalogTest {

    @Suppress("UNCHECKED_CAST")
    private fun functionOf(name: String): Map<String, Any?> {
        val tool = AssistantToolCatalog.tools.first { (it["function"] as Map<String, Any?>)["name"] == name }
        return tool["function"] as Map<String, Any?>
    }

    @Suppress("UNCHECKED_CAST")
    private fun propertiesOf(name: String): Map<String, Any?> =
        (functionOf(name)["parameters"] as Map<String, Any?>)["properties"] as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun requiredOf(name: String): List<String> =
        ((functionOf(name)["parameters"] as Map<String, Any?>)["required"] as? List<String>).orEmpty()

    @Suppress("UNCHECKED_CAST")
    private fun allNames(tools: List<Map<String, Any?>>): Set<String> =
        tools.map { (it["function"] as Map<String, Any?>)["name"] as String }.toSet()

    @Test
    fun `the ten smart tools are all offered`() {
        assertEquals(
            setOf(
                "openScreen", "showInventory", "openRecord",
                "analyzeGoals", "compareFlows", "rankBranchesByGoals",
                "suggestPurchaseOrder", "draftPurchaseOrder",
                "matchProductNames", "addProductAlias"
            ),
            AssistantToolCatalog.SMART_TOOL_NAMES
        )
        assertTrue(allNames(AssistantToolCatalog.tools).containsAll(AssistantToolCatalog.SMART_TOOL_NAMES))
    }

    @Test
    fun `they are offered even when nothing is attached`() {
        assertTrue(allNames(AssistantToolCatalog.toolsFor(hasAttachments = false)).containsAll(AssistantToolCatalog.SMART_TOOL_NAMES))
    }

    @Test
    fun `the groups do not overlap each other or the older groups`() {
        val groups = listOf(
            AssistantToolCatalog.APP_CONTROL_TOOL_NAMES,
            AssistantToolCatalog.INSIGHT_TOOL_NAMES,
            AssistantToolCatalog.PLANNING_TOOL_NAMES,
            AssistantToolCatalog.MATCHING_TOOL_NAMES,
            AssistantToolCatalog.FILE_TOOL_NAMES,
            AssistantToolCatalog.WORKSPACE_TOOL_NAMES
        )
        assertEquals(groups.sumOf { it.size }, groups.flatten().toSet().size)
    }

    @Test
    fun `exactly the two tools that change data ask for confirmation`() {
        assertEquals(setOf("draftPurchaseOrder", "addProductAlias"), AssistantToolCatalog.SMART_WRITE_TOOL_NAMES)
        assertTrue(AssistantToolCatalog.SMART_TOOL_NAMES.containsAll(AssistantToolCatalog.SMART_WRITE_TOOL_NAMES))
        // the navigation and report tools never write, so they must never be in the confirmation set
        listOf(AssistantToolCatalog.APP_CONTROL_TOOL_NAMES, AssistantToolCatalog.INSIGHT_TOOL_NAMES).forEach { readOnly ->
            assertTrue(readOnly.none { it in AssistantToolCatalog.SMART_WRITE_TOOL_NAMES })
        }
        assertFalse("suggestPurchaseOrder" in AssistantToolCatalog.SMART_WRITE_TOOL_NAMES)
        assertFalse("matchProductNames" in AssistantToolCatalog.SMART_WRITE_TOOL_NAMES)
    }

    @Test
    fun `openScreen offers exactly the screens the app can open`() {
        @Suppress("UNCHECKED_CAST")
        val screen = propertiesOf("openScreen")["screen"] as Map<String, Any?>
        assertEquals(AssistantScreen.keys, screen["enum"])
        assertEquals(listOf("screen"), requiredOf("openScreen"))
    }

    @Test
    fun `showInventory exposes the quantity and status filters`() {
        val properties = propertiesOf("showInventory").keys
        assertTrue(properties.containsAll(setOf("status", "quantityBelow", "quantityAbove", "inclusive", "search", "branch", "branchId", "category", "categoryId", "sortBy")))
        assertTrue(requiredOf("showInventory").isEmpty())
    }

    @Test
    fun `the required arguments are declared for the tools that need them`() {
        assertEquals(listOf("items"), requiredOf("draftPurchaseOrder"))
        assertEquals(listOf("items"), requiredOf("matchProductNames"))
        assertEquals(listOf("productId", "alias"), requiredOf("addProductAlias"))
        assertEquals(listOf("type", "id"), requiredOf("openRecord"))
    }

    @Test
    fun `the report tools share one way of naming a period`() {
        listOf("analyzeGoals", "compareFlows", "rankBranchesByGoals").forEach { tool ->
            assertTrue(tool, propertiesOf(tool).keys.containsAll(setOf("month", "fromDate", "toDate")))
        }
    }

    @Test
    fun `the system prompt teaches the review-before-sending rule`() {
        val prompt = AssistantToolCatalog.systemPrompt
        listOf(
            "openScreen", "showInventory", "analyzeGoals", "compareFlows", "rankBranchesByGoals",
            "suggestPurchaseOrder", "draftPurchaseOrder", "matchProductNames", "addProductAlias"
        ).forEach { name -> assertTrue("prompt should mention $name", prompt.contains(name)) }
        assertTrue(prompt.contains("لا تقل أبدًا إنك أرسلت الطلب"))
    }
}
