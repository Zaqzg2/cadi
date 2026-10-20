package com.inventorysmartai.app.presentation.goals

import com.inventorysmartai.app.domain.model.Goal
import java.util.Calendar

/** سنة×12+شهر: لحظتان في نفس الشهر التقويمي لهما نفس المفتاح مهما اختلف اليوم أو الساعة. */
internal fun monthKey(epochMillis: Long): Int {
    val c = Calendar.getInstance().apply { timeInMillis = epochMillis }
    return c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH)
}

/**
 * أول لحظة وآخر لحظة في الشهر الذي تقع فيه [epochMillis].
 *
 * النسخة القديمة كانت تحفظ بداية الشهر بساعة «الآن» (لا بمنتصف الليل)، فتسقط مبيعات اليوم الأول قبل تلك الساعة من إنجاز الهدف
 * وتدخل في الهدف مبيعات أول يوم من الشهر التالي. هنا الحدّان دقيقان: 00:00:00.000 إلى 23:59:59.999.
 */
internal fun monthBounds(epochMillis: Long): Pair<Long, Long> {
    val start = Calendar.getInstance().apply {
        timeInMillis = epochMillis
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val end = (start.clone() as Calendar).apply {
        add(Calendar.MONTH, 1)
        add(Calendar.MILLISECOND, -1)
    }
    return start.timeInMillis to end.timeInMillis
}

/**
 * صفّ واحد لكل (صنف، شهر): كان النموذج القديم يُدخل صفًّا جديدًا في كل حفظ، فتتكرر بطاقات الهدف نفسه.
 * يبقى أحدث صفّ (الأكبر id) والقائمة مرتّبة بالأحدث شهرًا ثم باسم الصنف.
 */
internal fun List<Goal>.latestPerProductMonth(): List<Goal> =
    groupBy { it.productId to monthKey(it.periodStart) }
        .values
        .map { rows -> rows.maxBy { it.id } }
        .sortedWith(compareByDescending<Goal> { monthKey(it.periodStart) }.thenBy { it.productName.orEmpty() })
