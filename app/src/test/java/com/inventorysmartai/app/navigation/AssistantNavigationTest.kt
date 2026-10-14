package com.inventorysmartai.app.navigation

import com.inventorysmartai.app.domain.inventory.InventoryListFilter
import com.inventorysmartai.app.domain.model.InventoryStatusFilter
import com.inventorysmartai.app.domain.model.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantNavigationTest {

    // ------------------------------------------------------------------ the filtered inventory route

    @Test
    fun `no filter gives the bare route`() {
        assertEquals("inventory_view", Destination.InventoryFiltered.createRoute(InventoryListFilter()))
    }

    @Test
    fun `only the filters that are set appear in the route`() {
        val route = Destination.InventoryFiltered.createRoute(
            InventoryListFilter(status = InventoryStatusFilter.LOW, quantityBelow = 10.0)
        )
        assertEquals("inventory_view?status=LOW&below=10", route)
    }

    @Test
    fun `every filter has its own argument`() {
        val route = Destination.InventoryFiltered.createRoute(
            InventoryListFilter(
                status = InventoryStatusFilter.EXPIRED,
                quantityBelow = 10.5,
                quantityAbove = 2.0,
                inclusiveBounds = true,
                branchId = 3L,
                categoryId = 4L,
                sortOrder = SortOrder.QUANTITY_ASC
            )
        )
        assertEquals("inventory_view?status=EXPIRED&below=10.5&above=2&incl=1&branchId=3&categoryId=4&sort=QUANTITY_ASC", route)
    }

    @Test
    fun `a search text is encoded so spaces and Arabic survive navigation`() {
        val route = Destination.InventoryFiltered.createRoute(InventoryListFilter(searchQuery = "صابون سائل"))
        assertTrue(route.startsWith("inventory_view?q="))
        assertFalse("no raw space", route.contains(' '))
        assertFalse("no plus sign for a space", route.contains('+'))
        assertTrue(route.contains("%20"))
    }

    @Test
    fun `the route pattern declares every argument the builder writes`() {
        listOf(
            Destination.InventoryFiltered.ARG_STATUS,
            Destination.InventoryFiltered.ARG_BELOW,
            Destination.InventoryFiltered.ARG_ABOVE,
            Destination.InventoryFiltered.ARG_INCLUSIVE,
            Destination.InventoryFiltered.ARG_QUERY,
            Destination.InventoryFiltered.ARG_BRANCH_ID,
            Destination.InventoryFiltered.ARG_CATEGORY_ID,
            Destination.InventoryFiltered.ARG_SORT
        ).forEach { arg ->
            assertTrue("pattern must declare {$arg}", Destination.InventoryFiltered.route.contains("$arg={$arg}"))
        }
    }

    @Test
    fun `the filtered inventory is a top-level route so the bottom bar stays`() {
        assertTrue(Destination.InventoryFiltered.route in Destination.topLevelRoutes)
    }

    // ------------------------------------------------------------------ the screens the assistant may open

    @Test
    fun `every assistant screen has a route and an Arabic label`() {
        AssistantScreen.entries.forEach { screen ->
            assertTrue(screen.name, screen.route.isNotBlank())
            assertTrue(screen.name, screen.labelAr.isNotBlank())
        }
    }

    @Test
    fun `the keys offered to the model are the enum names, without repeats`() {
        assertEquals(AssistantScreen.entries.map { it.name }, AssistantScreen.keys)
        assertEquals(AssistantScreen.keys.size, AssistantScreen.keys.toSet().size)
    }

    @Test
    fun `a screen is found whatever the case or stray spaces`() {
        assertEquals(AssistantScreen.INVENTORY, AssistantScreen.fromKey("INVENTORY"))
        assertEquals(AssistantScreen.INVENTORY, AssistantScreen.fromKey("  inventory "))
        assertEquals(AssistantScreen.PURCHASES, AssistantScreen.fromKey("Purchases"))
    }

    @Test
    fun `an unknown or empty screen name is null`() {
        assertNull(AssistantScreen.fromKey("nonsense"))
        assertNull(AssistantScreen.fromKey(""))
        assertNull(AssistantScreen.fromKey("   "))
        assertNull(AssistantScreen.fromKey(null))
    }

    @Test
    fun `screens point at the real destinations`() {
        assertEquals(Destination.Inventory.route, AssistantScreen.INVENTORY.route)
        assertEquals(Destination.PurchaseList.route, AssistantScreen.PURCHASES.route)
        assertEquals(Destination.Goals.route, AssistantScreen.GOALS.route)
        assertEquals(Destination.Reports.route, AssistantScreen.REPORTS.route)
        assertEquals(Destination.DataCenter.route, AssistantScreen.DATA_CENTER.route)
        assertNotNull(AssistantScreen.NEW_PURCHASE_REQUEST.route)
        assertEquals("purchases/detail?requestId=-1", AssistantScreen.NEW_PURCHASE_REQUEST.route)
    }

    @Test
    fun `record routes carry the id`() {
        assertEquals("purchases/detail?requestId=42", Destination.PurchaseDetail.createRoute(42L))
        assertEquals("inventory/product/7", Destination.ProductDetail.createRoute(7L))
    }
}
