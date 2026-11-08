package com.inventorysmartai.app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inventorysmartai.app.core.common.Formatters

/*
 * لبنات «لوحة المعلومات + الجدول التفاعلي» المشتركة بين صفحات الأهداف والجرد والمشتريات والتحويل والمبيعات، كي تبدو كلها
 * بنفس الأسلوب: بطاقات مؤشرات، رسوم بسيطة مرسومة بمكوّنات Compose (بلا مكتبة رسوم)، ثم جدول بفرز وفلاتر ونقر على الصف.
 */

/** مؤشر واحد في شبكة المؤشرات. [hint] سطر صغير تحت الاسم (اختياري). */
class Kpi(val label: String, val value: String, val color: Color, val hint: String? = null)

/** شبكة مؤشرات بعمودين. */
@Composable
fun KpiGrid(kpis: List<Kpi>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        kpis.chunked(2).forEach { pair ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { KpiTile(it, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun KpiTile(kpi: Kpi, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = kpi.color.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(kpi.value, style = MaterialTheme.typography.titleLarge, color = kpi.color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(kpi.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            if (kpi.hint != null) {
                Text(kpi.hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** جزء من شريط التوزيع. */
class Segment(val label: String, val value: Double, val color: Color)

/** شريط واحد مقسوم بحسب النسب، مع مفتاح تحته (اللون، الاسم، العدد، النسبة). الأجزاء الصفرية لا تُرسم. */
@Composable
fun DistributionCard(title: String, segments: List<Segment>, modifier: Modifier = Modifier) {
    val visible = segments.filter { it.value > 0.0 }
    val total = visible.sumOf { it.value }
    Card(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (visible.isEmpty()) {
                Text("لا توجد بيانات بعد", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().height(14.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    visible.forEach { Box(Modifier.weight(it.value.toFloat()).fillMaxHeight().background(it.color)) }
                }
                visible.forEach { segment ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(segment.color))
                        Text(segment.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(Formatters.formatNumber(segment.value), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            Formatters.formatPercent(segment.value / total * 100.0),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** سطر في بطاقة الترتيب. [fraction] من 0 إلى 1 هو طول الشريط. */
class RankItem(val label: String, val fraction: Float, val valueText: String, val color: Color)

/**
 * يحوّل أزواج (اسم، قيمة) إلى عناصر ترتيب بأطوال نسبية لأكبر قيمة مطلقة. [colorOf] يختار لون كل قيمة
 * (مثلًا الأحمر للنقص والأزرق للزيادة)، و[format] يكتب القيمة نصًا.
 */
fun rankItems(entries: List<Pair<String, Double>>, colorOf: (Double) -> Color, format: (Double) -> String): List<RankItem> {
    val max = entries.maxOfOrNull { kotlin.math.abs(it.second) }?.takeIf { it > 0.0 } ?: return emptyList()
    return entries.map { (label, value) ->
        RankItem(label, (kotlin.math.abs(value) / max).toFloat().coerceIn(0.03f, 1f), format(value), colorOf(value))
    }
}

/** ترتيب أفقي: اسم + قيمة فوق شريط يمثل النسبة إلى الأكبر. */
@Composable
fun RankBarsCard(title: String, items: List<RankItem>, modifier: Modifier = Modifier, emptyText: String = "لا توجد بيانات بعد") {
    Card(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (items.isEmpty()) {
                Text(emptyText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items.forEach { item ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(item.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(item.valueText, style = MaterialTheme.typography.labelLarge, color = item.color)
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth(item.fraction).fillMaxHeight().clip(CircleShape).background(item.color))
                    }
                }
            }
        }
    }
}

/** أعمدة رأسية لسلسلة زمنية (كل نقطة: تسمية قصيرة، قيمة). تظهر أول تسمية وآخرها تحت الأعمدة. */
@Composable
fun TrendBarsCard(
    title: String,
    points: List<Pair<String, Double>>,
    color: Color,
    totalText: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(totalText, style = MaterialTheme.typography.labelLarge, color = color)
            }
            val max = points.maxOfOrNull { it.second } ?: 0.0
            if (points.isEmpty() || max <= 0.0) {
                Text("لا توجد بيانات كافية بعد", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().height(110.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    points.forEach { (_, value) ->
                        val fraction = (value / max).toFloat().coerceIn(0.02f, 1f)
                        Box(
                            modifier = Modifier.weight(1f).fillMaxHeight(fraction)
                                .background(if (value > 0.0) color else MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(points.first().first, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(points.last().first, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** فلتر للجدول: اسم الشريحة والشرط الذي يمرّره الصف. «الكل» يُضاف تلقائيًا في أولها. */
class TableFilter<T>(val label: String, val predicate: (T) -> Boolean)

/**
 * الصفحة المشتركة: لسانان — «لوحة المعلومات» (ما يضيفه [dashboard] من بطاقات ورسوم) و«الجدول» (جدول تفاعلي: فرز بلمس العنوان،
 * فلاتر سريعة، نقر على الصف لفتحه، وحذف بزر في آخر الصف). اللسان المختار يُحفظ مع تدوير الشاشة.
 *
 * [rows] هي الصفوف الحالية (بعد بحث الشريط العلوي)؛ فلاتر الجدول تُضيّقها للجدول وحده، أما اللوحة فتبقى على كل [rows].
 */
@Composable
fun <T> DashboardTablePage(
    rows: List<T>,
    rowKey: (T) -> Any,
    columns: List<TableColumn<T>>,
    modifier: Modifier = Modifier,
    filters: List<TableFilter<T>> = emptyList(),
    onRowClick: ((T) -> Unit)? = null,
    onDelete: ((T) -> Unit)? = null,
    emptyText: String = "لا توجد بيانات",
    dashboard: LazyListScope.() -> Unit
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var filterIndex by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("لوحة المعلومات") }, modifier = Modifier.weight(1f))
            FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("الجدول") }, modifier = Modifier.weight(1f))
        }

        if (tab == 0) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = dashboard
            )
        } else {
            val activeFilter = filters.getOrNull(filterIndex - 1)
            val shown = remember(rows, activeFilter) { if (activeFilter == null) rows else rows.filter(activeFilter.predicate) }

            if (filters.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { FilterChip(selected = filterIndex == 0, onClick = { filterIndex = 0 }, label = { Text("الكل") }) }
                    items(filters.size) { i ->
                        FilterChip(selected = filterIndex == i + 1, onClick = { filterIndex = i + 1 }, label = { Text(filters[i].label) })
                    }
                }
            }
            Text(
                "${Formatters.formatInt(shown.size)} صف — المس عنوان العمود للفرز",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            DataTable(
                rows = shown,
                rowKey = rowKey,
                columns = columns,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                onRowClick = onRowClick,
                onDelete = onDelete,
                emptyText = emptyText
            )
        }
    }
}
