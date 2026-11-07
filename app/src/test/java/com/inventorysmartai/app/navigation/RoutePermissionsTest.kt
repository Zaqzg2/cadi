package com.inventorysmartai.app.navigation

import com.inventorysmartai.app.domain.access.Permission
import com.inventorysmartai.app.domain.access.RoutePermissions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePermissionsTest {

    /** كل وجهات التطبيق (أجسام Destination المتداخلة) بالانعكاس، فلا تفوتنا واحدة تُضاف لاحقًا. */
    private fun allDestinations(): List<Destination> =
        Destination::class.java.declaredClasses
            .filter { Destination::class.java.isAssignableFrom(it) && it != Destination::class.java }
            .mapNotNull { cls -> runCatching { cls.getField("INSTANCE").get(null) as Destination }.getOrNull() }

    @Test
    fun `the reflection really finds the destinations`() {
        // حارس للاختبار التالي: لو عثر على قائمة فارغة لنجح بلا فحص أي شيء.
        assertTrue(allDestinations().size >= 30)
    }

    /**
     * كل وجهة في التطبيق لها قاعدة صلاحية صريحة (أو مفتوحة صراحةً). هذا الاختبار هو ما يمنع أن تُضاف شاشة جديدة فتبقى
     * بلا حماية بصمت: يفشل ويجبر المطوّر على تصنيفها في RoutePermissions.
     */
    @Test
    fun `every destination route has an explicit access rule`() {
        allDestinations().forEach { destination ->
            assertNotNull("لا قاعدة صلاحية للمسار ${destination.route}", RoutePermissions.required(destination.route))
        }
    }

    @Test
    fun `home and more are open to everyone`() {
        assertTrue(RoutePermissions.required(Destination.Home.route)!!.isEmpty())
        assertTrue(RoutePermissions.required(Destination.More.route)!!.isEmpty())
    }

    @Test
    fun `a query string does not change the rule`() {
        assertEquals(
            RoutePermissions.required("inventory"),
            RoutePermissions.required(Destination.InventoryFiltered.route)
        )
    }

    @Test
    fun `the more specific rule wins over the general one`() {
        assertEquals(setOf(Permission.MANAGE_BACKUP), RoutePermissions.required(Destination.Backup.route))
        assertEquals(setOf(Permission.MANAGE_USERS), RoutePermissions.required(Destination.Permissions.route))
        assertEquals(setOf(Permission.MANAGE_SETTINGS), RoutePermissions.required(Destination.SettingsInventory.route))
        assertEquals(setOf(Permission.MANAGE_GOALS), RoutePermissions.required(Destination.GoalDetail.route))
        assertEquals(setOf(Permission.MANAGE_STOCK), RoutePermissions.required(Destination.StockOperationNew.route))
    }

    @Test
    fun `sales purchases and counting need their own permission`() {
        assertEquals(setOf(Permission.MANAGE_SALES), RoutePermissions.required(Destination.SalesList.route))
        assertEquals(setOf(Permission.MANAGE_PURCHASES), RoutePermissions.required(Destination.PurchaseList.route))
        assertEquals(setOf(Permission.MANAGE_STOCK), RoutePermissions.required(Destination.CountingList.route))
    }

    @Test
    fun `no route means open and an unknown route has no rule`() {
        assertTrue(RoutePermissions.required(null)!!.isEmpty())
        assertEquals(null, RoutePermissions.required("some/new/screen"))
    }

    @Test
    fun `a path that merely starts with the same letters is not matched`() {
        // «goals» must not capture «goalsx», «stock» must not capture «stockpile»
        assertEquals(null, RoutePermissions.required("goalsx"))
        assertEquals(null, RoutePermissions.required("stockpile"))
    }
}
