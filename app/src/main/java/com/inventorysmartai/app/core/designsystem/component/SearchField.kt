package com.inventorysmartai.app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/** حقل بحث مدمج (40dp) على شكل كبسولة بدل الحقل الكبير ذي الإطار. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "بحث"
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier.fillMaxWidth().height(40.dp),
        decorationBox = { inner ->
            Row(
                modifier = Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(50))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = muted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = muted)
                    inner()
                }
                if (value.isNotEmpty()) {
                    Box(
                        modifier = Modifier.size(32.dp).clickable { onValueChange("") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "مسح", tint = muted, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    )
}
