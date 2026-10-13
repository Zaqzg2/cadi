package com.inventorysmartai.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController

private data class BottomNavItem(val route: String, val label: String, val icon: ImageVector, val isSelected: (String?) -> Boolean)

/** الأقسام الثلاثة التي تجمعها صفحة «المخزون»: تُظهر تبويب المخزون مفعّلًا. */
private val stockGroup = setOf(
    Destination.Stock.route, Destination.Inventory.route, Destination.CountingList.route, Destination.PurchaseList.route
)
private val mainRoutes = stockGroup + setOf(Destination.Home.route, Destination.SalesList.route, Destination.Reports.route)

/** ترتيب التبويبات الرئيسية (من اليمين لليسار) — يُستخدم أيضًا للتنقل بالسحب. */
val mainTabRoutes = listOf(
    Destination.Home.route, Destination.Stock.route, Destination.SalesList.route, Destination.Reports.route, Destination.More.route
)

private val bottomNavItems = listOf(
    BottomNavItem(Destination.Home.route, "الرئيسية", Icons.Filled.Home) { it == Destination.Home.route },
    BottomNavItem(Destination.Stock.route, "المخزون", Icons.Filled.Inventory2) { it != null && it in stockGroup },
    BottomNavItem(Destination.SalesList.route, "المبيعات", Icons.Filled.PointOfSale) { it == Destination.SalesList.route },
    BottomNavItem(Destination.Reports.route, "التقارير", Icons.Filled.BarChart) { it == Destination.Reports.route },
    BottomNavItem(Destination.More.route, "المزيد", Icons.Filled.MoreHoriz) {
        it != null && it !in mainRoutes && it in Destination.topLevelRoutes
    }
)

fun navigateToTab(navController: NavController, route: String) {
    navController.navigate(route) {
        popUpTo(Destination.Home.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun BottomNavBar(currentRoute: String?, navController: NavController) {
    NavigationBar {
        bottomNavItems.forEach { item ->
            NavigationBarItem(
                selected = item.isSelected(currentRoute),
                onClick = { if (currentRoute != item.route) navigateToTab(navController, item.route) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) }
            )
        }
    }
}
