package com.inventorysmartai.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FactCheck
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

/**
 * Material 3's NavigationBar is designed for 4-5 destinations, not the 10 sections in the
 * spec — cramming all 10 in would fight "modern, clean, practical" directly. So: 4 of the most
 * frequent sections plus "المزيد" (More), which highlights as selected for every section
 * reached through it. Every one of the 10 sections is still exactly one tap away.
 */
private val bottomNavItems = listOf(
    BottomNavItem(Destination.Home.route, "الرئيسية", Icons.Filled.Home) { it == Destination.Home.route },
    BottomNavItem(Destination.Inventory.route, "المخزون", Icons.Filled.Inventory2) { it == Destination.Inventory.route },
    BottomNavItem(Destination.CountingList.route, "الجرد", Icons.Filled.FactCheck) { it == Destination.CountingList.route },
    BottomNavItem(Destination.SalesList.route, "المبيعات", Icons.Filled.PointOfSale) { it == Destination.SalesList.route },
    BottomNavItem(Destination.More.route, "المزيد", Icons.Filled.MoreHoriz) { currentRoute ->
        currentRoute != null && currentRoute !in setOf(
            Destination.Home.route, Destination.Inventory.route, Destination.CountingList.route, Destination.SalesList.route
        ) && currentRoute in Destination.topLevelRoutes
    }
)

@Composable
fun BottomNavBar(currentRoute: String?, navController: NavController) {
    NavigationBar {
        bottomNavItems.forEach { item ->
            NavigationBarItem(
                selected = item.isSelected(currentRoute),
                onClick = {
                    if (currentRoute != item.route) {
                        navController.navigate(item.route) {
                            popUpTo(Destination.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) }
            )
        }
    }
}
