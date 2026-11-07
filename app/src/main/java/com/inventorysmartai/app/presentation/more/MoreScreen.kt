@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.inventorysmartai.app.presentation.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.inventorysmartai.app.navigation.Destination
import com.inventorysmartai.app.presentation.access.AccessGateViewModel
import com.inventorysmartai.app.core.designsystem.component.AppTopBar

private data class MoreItem(val label: String, val icon: ImageVector, val route: String)

private val moreItems = listOf(
    MoreItem("المبيعات", Icons.Filled.PointOfSale, Destination.SalesList.route),
    MoreItem("مركز البيانات", Icons.Filled.Storage, Destination.DataCenter.route),
    MoreItem("المساعد الذكي", Icons.Filled.SmartToy, Destination.AiAssistant.route),
    MoreItem("الإعدادات", Icons.Filled.SettingsSuggest, Destination.Settings.route)
)

@Composable
fun MoreScreen(navController: NavController, gate: AccessGateViewModel = hiltViewModel()) {
    val access by gate.state.collectAsStateWithLifecycle()
    // ما لا يملك المستخدم صلاحيته لا يظهر له أصلًا (القفل والصلاحيات مطفآن افتراضيًا فتظهر كلها).
    val visibleItems = moreItems.filter { access.canOpen(it.route) }

    Scaffold(topBar = { AppTopBar(title = "المزيد") }) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(visibleItems) { item ->
                Card(modifier = Modifier.fillMaxWidth(), onClick = { navController.navigate(item.route) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(item.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                    }
                }
            }
            if (access.enabled) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), onClick = gate::lock) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("قفل التطبيق الآن", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }
        }
    }
}
