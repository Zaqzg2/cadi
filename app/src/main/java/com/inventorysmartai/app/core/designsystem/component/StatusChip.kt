package com.inventorysmartai.app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.inventorysmartai.app.core.designsystem.theme.LocalInventoryStatusColors
import com.inventorysmartai.app.domain.model.InventoryStatus

@Composable
fun StatusChip(status: InventoryStatus, modifier: Modifier = Modifier) {
    val colors = LocalInventoryStatusColors.current
    val (color, label) = when (status) {
        InventoryStatus.AVAILABLE -> colors.available to "متوفر"
        InventoryStatus.LOW -> colors.low to "منخفض"
        InventoryStatus.ZERO -> colors.zero to "صفر"
        InventoryStatus.NEAR_EXPIRY -> colors.nearExpiry to "قريب الانتهاء"
        InventoryStatus.EXPIRED -> colors.expired to "منتهي"
    }
    StatusPill(text = label, color = color, modifier = modifier)
}

@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalContentColor provides color) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = modifier
                .background(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
