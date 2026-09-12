package com.inventorysmartai.app.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.inventorysmartai.app.BuildConfig
import com.inventorysmartai.app.core.designsystem.component.AppTopBar

@Composable
fun AppInfoScreen(navController: NavController) {
    Scaffold(topBar = { AppTopBar(title = "معلومات التطبيق", onBack = { navController.popBackStack() }) }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    InfoRow("اسم التطبيق", "Inventory Smart AI")
                    InfoRow("الإصدار", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    InfoRow("المرحلة الحالية", "التأسيس فقط — بدون تكاملات خارجية بعد")
                }
            }
            Text(
                "سيتم لاحقاً دمج Gemini API وGoogle Drive/Sheets/Docs وGmail والتقويم وFirebase وقارئ الباركود والكاميرا واستيراد الملفات، وفق خارطة الطريق المتفق عليها.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
