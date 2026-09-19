package com.inventorysmartai.app.core.common

import java.text.NumberFormat
import java.util.Locale

/**
 * Central number formatting so "Arabic numbers where appropriate" is one decision, not
 * scattered `.toString()` calls. Business/inventory apps across most Arabic-speaking markets
 * default to Western (0-9) digits, so that's the default here — [useArabicIndicDigits] flips
 * every call site to Eastern Arabic-Indic digits (٠-٩) instead, driven by a Settings toggle
 * backed by SettingsLocalDataSource.
 */
object Formatters {
    // Unicode "-u-nu-latn" forces Western digits while keeping Arabic grouping/decimal rules.
    private val westernDigitsLocale: Locale = Locale.forLanguageTag("ar-u-nu-latn")
    private val easternDigitsLocale: Locale = Locale.forLanguageTag("ar-u-nu-arab")

    private fun localeFor(useArabicIndicDigits: Boolean) =
        if (useArabicIndicDigits) easternDigitsLocale else westernDigitsLocale

    fun formatNumber(value: Double, useArabicIndicDigits: Boolean = false, maxFractionDigits: Int = 2): String {
        val formatter = NumberFormat.getNumberInstance(localeFor(useArabicIndicDigits))
        formatter.maximumFractionDigits = maxFractionDigits
        return formatter.format(value)
    }

    fun formatInt(value: Int, useArabicIndicDigits: Boolean = false): String =
        NumberFormat.getIntegerInstance(localeFor(useArabicIndicDigits)).format(value)

    fun formatPercent(value: Double, useArabicIndicDigits: Boolean = false): String {
        val formatter = NumberFormat.getNumberInstance(localeFor(useArabicIndicDigits))
        formatter.maximumFractionDigits = 1
        return "%${formatter.format(value)}"
    }

    fun formatCurrency(value: Double, useArabicIndicDigits: Boolean = false): String {
        val formatter = NumberFormat.getNumberInstance(localeFor(useArabicIndicDigits))
        formatter.maximumFractionDigits = 2
        return "${formatter.format(value)} ${Constants.DEFAULT_CURRENCY_SYMBOL}"
    }

    fun formatDate(epochMillis: Long): String {
        val formatter = java.text.SimpleDateFormat("yyyy/MM/dd", Locale.US)
        return formatter.format(java.util.Date(epochMillis))
    }

    /** Phase 3: shown on the file-picker step (spec section 2's "file size"). Deliberately plain
     *  KB/MB arithmetic, always Western digits — a file size is a technical detail, not a
     *  business number, so it doesn't participate in [useArabicIndicDigits]. */
    fun formatFileSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes بايت"
        bytes < 1024 * 1024 -> "%.1f كيلوبايت".format(bytes / 1024.0)
        else -> "%.1f ميجابايت".format(bytes / (1024.0 * 1024.0))
    }
}
