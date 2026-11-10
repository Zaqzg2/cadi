package com.inventorysmartai.app.core.designsystem.theme

import kotlin.math.pow
import kotlin.math.roundToInt

/** حسابات ألوان بسيطة على قيم ARGB (Int) بلا اعتماد على أندرويد، كي تُختبر بسهولة. */
object ColorMath {
    const val NEAR_BLACK: Int = 0xFF101418.toInt()
    const val NEAR_WHITE: Int = 0xFFF1F4FA.toInt()

    private fun channel(argb: Int, shift: Int): Int = (argb shr shift) and 0xFF

    private fun linear(c: Int): Double {
        val s = c / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    /** السطوع النسبي (0 = أسود، 1 = أبيض) حسب WCAG. */
    fun luminance(argb: Int): Double =
        0.2126 * linear(channel(argb, 16)) + 0.7152 * linear(channel(argb, 8)) + 0.0722 * linear(channel(argb, 0))

    /** نسبة التباين بين لونين (من 1 إلى 21)؛ النص المقروء يحتاج 4.5 تقريبًا، و3 كحد أدنى للنص الكبير. */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    fun isDark(argb: Int): Boolean = luminance(argb) < 0.18

    /** مزج [from] نحو [to] بنسبة [amount] (0..1)، والناتج معتم دائمًا. */
    fun blend(from: Int, to: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        fun mix(shift: Int): Int {
            val a = channel(from, shift)
            val b = channel(to, shift)
            return (a + (b - a) * t).roundToInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    /** أنسب لون خط (قاتم أو فاتح) يعطي أعلى تباين أدنى مع كل الأسطح المعطاة. */
    fun bestTextFor(vararg surfaces: Int): Int {
        fun worst(text: Int) = surfaces.minOf { contrast(text, it) }
        return if (worst(NEAR_BLACK) >= worst(NEAR_WHITE)) NEAR_BLACK else NEAR_WHITE
    }

    /** لون بطاقة مناسب لخلفية معيّنة حين لا يحدد المستخدم لون البطاقة: أفتح قليلًا في الداكن، وأقرب للأبيض في الفاتح. */
    fun cardFor(background: Int): Int =
        if (isDark(background)) blend(background, 0xFFFFFFFF.toInt(), 0.09f) else blend(background, 0xFFFFFFFF.toInt(), 0.7f)

    /** «#RRGGBB» لقيمة ARGB. */
    fun toHex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

    /** يقرأ «#RRGGBB» أو «RRGGBB» (أو 3 خانات) ويعيد ARGB معتمًا، أو null إن كان النص غير صالح. */
    fun parseHex(text: String): Int? {
        var t = text.trim().removePrefix("#")
        if (t.length == 3) t = t.map { "$it$it" }.joinToString("")
        if (t.length != 6 || !t.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        return (0xFF shl 24) or t.toInt(16)
    }
}
