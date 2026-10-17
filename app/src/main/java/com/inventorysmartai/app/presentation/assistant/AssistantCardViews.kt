package com.inventorysmartai.app.presentation.assistant

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inventorysmartai.app.core.designsystem.theme.LocalInventoryStatusColors
import com.inventorysmartai.app.domain.assistant.AssistantCard
import com.inventorysmartai.app.domain.assistant.AssistantCardBuilder
import com.inventorysmartai.app.domain.assistant.CardAction
import com.inventorysmartai.app.domain.assistant.CardButton
import com.inventorysmartai.app.domain.assistant.FlowBar
import com.inventorysmartai.app.domain.assistant.GoalRowCard
import com.inventorysmartai.app.domain.assistant.ItemRow
import com.inventorysmartai.app.domain.assistant.MatchRowCard
import com.inventorysmartai.app.domain.assistant.PlanRow
import com.inventorysmartai.app.domain.assistant.RankRow
import com.inventorysmartai.app.domain.assistant.Tile
import com.inventorysmartai.app.domain.assistant.Tone

/**
 * The cards under an assistant answer — the interactive part of the design: summary tiles, a status table with coloured pills,
 * progress bars, a purchase proposal with its buttons. Every card is plain data ([AssistantCard]); this file only draws it, and a
 * button hands its [CardAction] back through [onAction] (the ViewModel decides what it does).
 */
@Composable
fun AssistantCards(cards: List<AssistantCard>, onAction: (CardAction) -> Unit, modifier: Modifier = Modifier) {
    if (cards.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cards.forEach { card ->
            when (card) {
                is AssistantCard.ItemList -> ItemListCard(card, onAction)
                is AssistantCard.Goals -> GoalsCard(card)
                is AssistantCard.Ranking -> RankingCard(card)
                is AssistantCard.Flows -> FlowsCard(card)
                is AssistantCard.PurchasePlan -> PurchasePlanCard(card, onAction)
                is AssistantCard.NameMatches -> NameMatchesCard(card, onAction)
                is AssistantCard.Choices -> ChoicesCard(card, onAction)
            }
        }
    }
}

// ---------------------------------------------------------------------- shared pieces

/** What a [Tone] looks like: [content] for text and bars, [container] for the soft background behind it. */
private class ToneColors(val content: Color, val container: Color)

@Composable
private fun toneColors(tone: Tone): ToneColors {
    val scheme = MaterialTheme.colorScheme
    val status = LocalInventoryStatusColors.current
    return when (tone) {
        Tone.GOOD -> ToneColors(status.available, status.available.copy(alpha = 0.14f))
        Tone.WARNING -> ToneColors(status.nearExpiry, status.nearExpiry.copy(alpha = 0.16f))
        Tone.DANGER -> ToneColors(status.low, status.low.copy(alpha = 0.12f))
        Tone.INFO -> ToneColors(scheme.primary, scheme.primary.copy(alpha = 0.12f))
        Tone.NEUTRAL -> ToneColors(scheme.onSurfaceVariant, scheme.onSurfaceVariant.copy(alpha = 0.10f))
    }
}

@Composable
private fun CardFrame(title: String, subtitle: String?, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

/** The small summary boxes at the top of a card ("منخفض — 28 صنف"). */
@Composable
private fun TileRow(tiles: List<Tile>) {
    if (tiles.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        tiles.forEach { tile ->
            val colors = toneColors(tile.tone)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .background(colors.container, RoundedCornerShape(12.dp))
                    .padding(horizontal = 6.dp, vertical = 8.dp)
            ) {
                Text(tile.label, style = MaterialTheme.typography.labelSmall, color = colors.content, textAlign = TextAlign.Center, maxLines = 2)
                Text(tile.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.content)
            }
        }
    }
}

/** A rounded status pill ("منخفض" in red, "متوفر" in green). */
@Composable
private fun Pill(text: String, tone: Tone) {
    val colors = toneColors(tone)
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = colors.content,
        maxLines = 1,
        modifier = Modifier
            .background(colors.container, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    )
}

@Composable
private fun ActionButton(button: CardButton, onAction: (CardAction) -> Unit, modifier: Modifier = Modifier) {
    if (button.primary) {
        Button(
            onClick = { onAction(button.action) },
            shape = RoundedCornerShape(12.dp),
            modifier = modifier.fillMaxWidth()
        ) { Text(button.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    } else {
        OutlinedButton(
            onClick = { onAction(button.action) },
            shape = RoundedCornerShape(12.dp),
            modifier = modifier.fillMaxWidth()
        ) { Text(button.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier, align: TextAlign = TextAlign.Start) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
        modifier = modifier
    )
}

/** A horizontal bar whose filled part is [fraction] of the track. */
@Composable
private fun Bar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.14f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(color, RoundedCornerShape(4.dp))
        )
    }
}

// ---------------------------------------------------------------------- inventory list

@Composable
private fun ItemListCard(card: AssistantCard.ItemList, onAction: (CardAction) -> Unit) {
    CardFrame(card.title, card.subtitle) {
        if (card.rows.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                HeaderCell("الصنف", Modifier.weight(2f))
                HeaderCell("الرصيد", Modifier.weight(1f), TextAlign.Center)
                HeaderCell("الحالة", Modifier.weight(1.4f), TextAlign.End)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            card.rows.forEachIndexed { index, row ->
                ItemRowView(row)
                if (index < card.rows.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
        card.viewAll?.let { ActionButton(it, onAction) }
    }
}

@Composable
private fun ItemRowView(row: ItemRow) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            row.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(2f)
        )
        Text(
            row.quantity,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        Box(modifier = Modifier.weight(1.4f), contentAlignment = Alignment.CenterEnd) {
            Pill(row.statusLabel, row.tone)
        }
    }
}

// ---------------------------------------------------------------------- goals

@Composable
private fun GoalsCard(card: AssistantCard.Goals) {
    CardFrame(card.title, card.subtitle) {
        TileRow(card.tiles)
        card.rows.forEach { GoalRowView(it) }
    }
}

@Composable
private fun GoalRowView(row: GoalRowCard) {
    val colors = toneColors(row.tone)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                AssistantCardBuilder.formatPercent(row.percent),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colors.content
            )
        }
        LinearProgressIndicator(
            progress = { (row.percent / 100.0).toFloat().coerceIn(0f, 1f) },
            color = colors.content,
            trackColor = colors.container,
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
        )
        Text(row.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------------- branch ranking

@Composable
private fun RankingCard(card: AssistantCard.Ranking) {
    CardFrame(card.title, card.subtitle) {
        card.rows.forEach { RankRowView(it) }
    }
}

@Composable
private fun RankRowView(row: RankRow) {
    val colors = toneColors(row.tone)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(28.dp).background(colors.container, CircleShape)
        ) {
            Text(row.rank.toString(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = colors.content)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(row.valueText, style = MaterialTheme.typography.labelMedium, color = colors.content, fontWeight = FontWeight.Bold)
            }
            Bar(row.fraction, colors.content, Modifier.fillMaxWidth())
        }
    }
}

// ---------------------------------------------------------------------- inbound vs sales

@Composable
private fun FlowsCard(card: AssistantCard.Flows) {
    val inboundColor = MaterialTheme.colorScheme.secondary
    val soldColor = MaterialTheme.colorScheme.primary
    val longest = card.rows.maxOfOrNull { maxOf(it.inbound, it.sold) } ?: 0.0
    CardFrame(card.title, card.subtitle) {
        TileRow(card.tiles)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend("الوارد", inboundColor)
            Legend("المبيعات", soldColor)
        }
        card.rows.forEach { FlowRowView(it, longest, inboundColor, soldColor) }
    }
}

@Composable
private fun Legend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FlowRowView(row: FlowBar, longest: Double, inboundColor: Color, soldColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(row.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        ValueBar(row.inbound, longest, inboundColor)
        ValueBar(row.sold, longest, soldColor)
        row.goalTarget?.let {
            Text(
                "مستهدف الهدف: ${AssistantCardBuilder.formatNumber(it)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ValueBar(value: Double, longest: Double, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Bar(if (longest > 0.0) (value / longest).toFloat() else 0f, color, Modifier.weight(1f))
        Text(
            AssistantCardBuilder.formatNumber(value),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.width(48.dp)
        )
    }
}

// ---------------------------------------------------------------------- purchase proposal

@Composable
private fun PurchasePlanCard(card: AssistantCard.PurchasePlan, onAction: (CardAction) -> Unit) {
    CardFrame(card.title, card.subtitle) {
        TileRow(card.tiles)
        Row(modifier = Modifier.fillMaxWidth()) {
            HeaderCell("الصنف", Modifier.weight(2f))
            HeaderCell("الحالي", Modifier.weight(1f), TextAlign.Center)
            HeaderCell("المقترح", Modifier.weight(1f), TextAlign.Center)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        card.rows.forEachIndexed { index, row ->
            PlanRowView(row)
            if (index < card.rows.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
        if (card.hiddenCount > 0) {
            Text(
                "و ${card.hiddenCount} أصناف أخرى ستُضاف إلى المسودة",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        card.buttons.forEach { ActionButton(it, onAction) }
    }
}

@Composable
private fun PlanRowView(row: PlanRow) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(2f)) {
            Text(row.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            row.note?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) }
        }
        Text(row.current, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (row.suggested != null) Pill(row.suggested, Tone.INFO) else Pill("حدّدها", Tone.WARNING)
        }
    }
}

// ---------------------------------------------------------------------- name matching

@Composable
private fun NameMatchesCard(card: AssistantCard.NameMatches, onAction: (CardAction) -> Unit) {
    CardFrame(card.title, card.subtitle) {
        card.rows.forEachIndexed { index, row ->
            MatchRowView(row, onAction)
            if (index < card.rows.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun MatchRowView(row: MatchRowCard, onAction: (CardAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                row.input,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Pill(row.confidenceLabel, row.tone)
        }
        row.matchedName?.let {
            Text("← $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
        row.detail?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        row.choices.forEach { ActionButton(it, onAction) }
        Spacer(Modifier.height(2.dp))
    }
}

// ---------------------------------------------------------------------- questions with answers to tap

@Composable
private fun ChoicesCard(card: AssistantCard.Choices, onAction: (CardAction) -> Unit) {
    CardFrame(card.title, card.subtitle) {
        // two buttons per row, like the quick-command grid of the design
        card.buttons.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                pair.forEach { button -> ActionButton(button, onAction, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
