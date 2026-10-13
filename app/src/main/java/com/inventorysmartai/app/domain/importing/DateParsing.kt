package com.inventorysmartai.app.domain.importing

import com.inventorysmartai.app.domain.matching.ArabicTextNormalizer
import java.util.Calendar
import java.util.TimeZone

/**
 * قراءة تاريخ الانتهاء كما يُكتب في الملفات: 2026-12-31 · 2026/12/31 · 31/12/2026 · 12/2026 (آخر الشهر)
 * · رقم تسلسلي من Excel. تُرجع منتصف الليل بالتوقيت المحلي، أو null إن تعذّرت القراءة.
 */
object DateParsing {
    fun parseToMillis(input: String?): Long? {
        val digits = ArabicTextNormalizer.normalizeDigits(input.orEmpty()).trim()
        if (digits.isEmpty()) return null
        digits.toDoubleOrNull()?.let { if (it in 20000.0..80000.0) return fromExcelSerial(it.toLong()) }
        val s = digits.replace('.', '/').replace('-', '/')
        Regex("^(\\d{4})/(\\d{1,2})/(\\d{1,2})$").matchEntire(s)?.let { return build(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }
        Regex("^(\\d{1,2})/(\\d{1,2})/(\\d{4})$").matchEntire(s)?.let { return build(it.groupValues[3], it.groupValues[2], it.groupValues[1]) }
        Regex("^(\\d{1,2})/(\\d{4})$").matchEntire(s)?.let { return build(it.groupValues[2], it.groupValues[1], "1", endOfMonth = true) }
        return null
    }

    private fun build(y: String, m: String, d: String, endOfMonth: Boolean = false): Long? {
        val year = y.toInt()
        val month = m.toInt()
        if (month !in 1..12 || year !in 2000..2100) return null
        val cal = Calendar.getInstance().apply { clear(); set(year, month - 1, 1) }
        val max = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val day = if (endOfMonth) max else d.toInt()
        if (day !in 1..max) return null
        cal.set(year, month - 1, day)
        return cal.timeInMillis
    }

    private fun fromExcelSerial(days: Long): Long {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear(); set(1899, Calendar.DECEMBER, 30); add(Calendar.DAY_OF_MONTH, days.toInt())
        }
        return Calendar.getInstance().apply {
            clear(); set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }
}
