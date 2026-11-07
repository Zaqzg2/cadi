package com.inventorysmartai.app.presentation.settings.alerts

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.inventorysmartai.app.core.designsystem.component.AppTopBar

/**
 * تنبيه يومي بالأصناف المنتهية أو القريبة من الانتهاء أو النافدة أو المنخفضة، يصل حتى والتطبيق مغلق.
 * العتبات (أيام قرب الانتهاء وحدّ الانخفاض) هي نفسها في «إعدادات المخزون»، فتتطابق مع ما تراه في لوحة التحكم.
 */
@Composable
fun StockAlertsScreen(navController: NavController, viewModel: StockAlertsViewModel = hiltViewModel()) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val hour by viewModel.hour.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); viewModel.onMessageShown() } }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.setEnabled(true) else viewModel.onPermissionDenied()
    }

    Scaffold(
        topBar = { AppTopBar(title = "تنبيهات المخزون", onBack = { navController.popBackStack() }) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("تنبيه يومي بحالة المخزون", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(
                            checked = enabled,
                            onCheckedChange = { checked ->
                                val needsPermission = checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                if (needsPermission) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else viewModel.setEnabled(checked)
                            }
                        )
                    }
                    Text(
                        "يصلك إشعار واحد في اليوم إن وُجد صنف منتهي الصلاحية أو قريب الانتهاء أو نافد أو منخفض. لا إشعار إن كان كل شيء سليمًا.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("وقت التنبيه", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.setHour((hour + 23) % 24) }) { Icon(Icons.Filled.Remove, contentDescription = "ساعة أبكر") }
                        Text("%02d:00".format(hour), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 16.dp))
                        IconButton(onClick = { viewModel.setHour((hour + 1) % 24) }) { Icon(Icons.Filled.Add, contentDescription = "ساعة أبعد") }
                    }
                    Text(
                        "الوقت تقريبي: يحدده النظام ضمن توفير البطارية، وقد يتأخر قليلًا.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedButton(onClick = viewModel::sendTestNow, modifier = Modifier.fillMaxWidth()) {
                Text("إرسال التنبيه الآن (تجربة)")
            }
        }
    }
}
