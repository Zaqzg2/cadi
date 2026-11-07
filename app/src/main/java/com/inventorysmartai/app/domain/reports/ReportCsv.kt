package com.inventorysmartai.app.domain.reports

import java.math.BigDecimal

/**
 * CSV لجدول تقرير. UTF-8 مع BOM كي يعرض Excel الحروف العربية صحيحة، وسطور CRLF، والحقول التي فيها فاصلة أو علامة اقتباس
 * أو سطر جديد تُقتبس بمضاعفة علامة الاقتباس (RFC 4180).
 *
 * حماية من «حقن الصيغ»: نصّ يبدأ بـ = أو + أو - أو @ يعامله Excel كصيغة عند فتح الملف (اسم صنف خبيث أو مستورد مثلًا)،
 * فيُسبق بعلامة ' ليبقى نصًّا. الأرقام الحقيقية (Number) لا تمسّها هذه القاعدة فتبقى سالبة كما هي.
 */
object ReportCsv {
    private const val BOM = "\uFEFF"
    private const val LINE_END = "\r\n"
    private const val FORMULA_STARTERS = "=+-@\t\r"

    fun build(headers: List<String>, rows: List<List<Any?>>): String = buildString {
        append(BOM)
        append(headers.joinToString(",") { cell(it) }).append(LINE_END)
        rows.forEach { row -> append(row.joinToString(",") { cell(it) }).append(LINE_END) }
    }

    internal fun cell(value: Any?): String {
        val text = when (value) {
            null -> ""
            is Double -> plain(value)
            is Float -> plain(value.toDouble())
            else -> value.toString()
        }
        val safe = if (value is String && text.isNotEmpty() && text[0] in FORMULA_STARTERS) "'$text" else text
        val needsQuotes = safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    /** بلا صيغة علمية (1.0E-5) ولا «.0» زائدة؛ القيم غير المنتهية (NaN/∞) خلية فارغة. */
    private fun plain(value: Double): String =
        if (!value.isFinite()) "" else BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
}
