package com.inventorysmartai.app.domain.alerts

import com.inventorysmartai.app.domain.model.InventoryStatus
import java.time.Duration
import java.time.ZonedDateTime

/** أعداد الأصناف حسب حالتها (كل صنف في حالة واحدة فقط، فلا تتداخل الأعداد). */
data class StockAlertCounts(val expired: Int, val nearExpiry: Int, val zeroStock: Int, val lowStock: Int) {
    val total: Int get() = expired + nearExpiry + zeroStock + lowStock

    companion object {
        fun from(statuses: List<InventoryStatus>) = StockAlertCounts(
            expired = statuses.count { it == InventoryStatus.EXPIRED },
            nearExpiry = statuses.count { it == InventoryStatus.NEAR_EXPIRY },
            zeroStock = statuses.count { it == InventoryStatus.ZERO },
            lowStock = statuses.count { it == InventoryStatus.LOW }
        )
    }
}

data class StockAlertMessage(val title: String, val body: String)

/** نص إشعار تنبيه المخزون. */
object StockAlertMessageBuilder {

    /** null حين لا شيء يستدعي الانتباه: لا يُرسل إشعار (لا نزعج المستخدم بلا سبب). */
    fun build(counts: StockAlertCounts): StockAlertMessage? {
        if (counts.total == 0) return null
        val parts = buildList {
            if (counts.expired > 0) add("${counts.expired} منتهية الصلاحية")
            if (counts.nearExpiry > 0) add("${counts.nearExpiry} قريبة الانتهاء")
            if (counts.zeroStock > 0) add("${counts.zeroStock} نفد مخزونها")
            if (counts.lowStock > 0) add("${counts.lowStock} مخزونها منخفض")
        }
        val urgent = counts.expired > 0 || counts.zeroStock > 0
        return StockAlertMessage(
            title = if (urgent) "تنبيه مخزون عاجل" else "تنبيه مخزون",
            body = parts.joinToString(" • ")
        )
    }
}

object StockAlertSchedule {
    const val DEFAULT_HOUR = 9

    /** الوقت (بالميلي ثانية) حتى أقرب [hour]:00 قادمة بالتوقيت المحلي: إن كانت الساعة مرّت اليوم فغدًا. */
    fun delayMillisUntilNext(hour: Int, now: ZonedDateTime): Long {
        var next = now.withHour(hour.coerceIn(0, 23)).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis()
    }
}
