package com.inventorysmartai.app.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.inventorysmartai.app.core.designsystem.component.AppTopBar

private data class PlaceholderInfo(val title: String, val message: String)

private val placeholders = mapOf(
    "account" to PlaceholderInfo("الحساب", "إدارة حساب المستخدم وتسجيل الدخول ستضاف مع تفعيل الخدمات السحابية."),
    "ai_settings" to PlaceholderInfo("إعدادات الذكاء الاصطناعي", "يمكن ضبط مسار الذكاء الاصطناعي ومفاتيحك الخاصة من «إعدادات الذكاء الاصطناعي».")
)

@Composable
fun PlaceholderScreen(navController: NavController, key: String) {
    val info = placeholders[key] ?: PlaceholderInfo("قريباً", "هذه الميزة ستتوفر في مرحلة لاحقة.")

    Scaffold(topBar = { AppTopBar(title = info.title, onBack = { navController.popBackStack() }) }) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                info.message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
