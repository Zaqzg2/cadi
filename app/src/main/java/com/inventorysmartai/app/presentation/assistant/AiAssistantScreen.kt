@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.inventorysmartai.app.presentation.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

/**
 * "For now, AI Assistant is only a UI placeholder" — intentionally no ViewModel, no repository
 * calls, no Gemini wiring. The input field is disabled on purpose.
 */
@Composable
fun AiAssistantScreen(navController: NavController) {
    Scaffold(topBar = { TopAppBar(title = { Text("المساعد الذكي") }) }) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Filled.SmartToy,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                "المساعد الذكي قادم قريباً",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                "سيتمكن المساعد من الإجابة عن أسئلتك حول المخزون والمبيعات والتقارير فور ربطه بواجهة Gemini API.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
            )
            OutlinedTextField(
                value = "",
                onValueChange = {},
                enabled = false,
                placeholder = { Text("اكتب سؤالك هنا...") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
