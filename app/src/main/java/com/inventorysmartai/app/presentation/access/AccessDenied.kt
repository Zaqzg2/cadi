package com.inventorysmartai.app.presentation.access

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * يغطّي الشاشة الحالية حين لا يملك المستخدم صلاحية فتحها. طبقة فوق الشاشة (لا استبدال لها) كي لا يُعاد بناء التنقل،
 * وتمتصّ اللمس فلا يصل إلى ما تحتها. شريط التنقل السفلي يبقى متاحًا للانتقال إلى ما هو مسموح.
 */
@Composable
fun AccessDeniedOverlay(onHome: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text("لا تملك صلاحية فتح هذه الشاشة", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
            Text(
                "اطلب من المدير تغيير دورك إن احتجتها.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )
            Button(onClick = onHome) { Text("العودة إلى الرئيسية") }
        }
    }
}
