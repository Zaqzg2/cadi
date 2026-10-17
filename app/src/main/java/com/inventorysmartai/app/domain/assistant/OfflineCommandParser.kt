package com.inventorysmartai.app.domain.assistant

import com.inventorysmartai.app.domain.matching.ArabicTextNormalizer
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** A command the app can carry out by itself, with no network and no language model. */
sealed interface OfflineCommand {
    /** [screenKey] is an `AssistantScreen` name (INVENTORY, PURCHASES...). */
    data class OpenScreen(val screenKey: String) : OfflineCommand

    /**
     * [status] is an `InventoryStatusFilter` name. [openScreen] is true when the person said "افتح / فلتر / انتقل" (take me there),
     * false for "اعرض / ما / كم" (tell me here, with a button to open the list).
     */
    data class ShowInventory(
        val status: String?,
        val below: Double?,
        val above: Double?,
        val inclusive: Boolean,
        val search: String?,
        val branch: String?,
        val category: String?,
        val openScreen: Boolean
    ) : OfflineCommand

    data class Goals(val month: String?, val onlyUnmet: Boolean) : OfflineCommand
    data class Flows(val month: String?, val byBranch: Boolean) : OfflineCommand
    data class RankBranches(val month: String?) : OfflineCommand
    data class SuggestPurchase(val targetFactor: Double?) : OfflineCommand

    /** "جهّز المسودة" — save the proposal just shown as a draft. */
    data object DraftFromSuggestion : OfflineCommand

    data class MatchNames(val names: List<String>) : OfflineCommand
    data object Help : OfflineCommand
}

/** One-letter prefixes that can stick to a word: و (and) ف (so) ب (with) ل (for) ك (like) س (will). */
private const val CLITICS = "وفبلكس"

/** The token itself, without a one-letter prefix, and without the article "ال" — so "للأصناف" yields "اصناف". */
private fun variantsOf(token: String): List<String> {
    val out = ArrayList<String>(5)
    out.add(token)
    if (token.length >= 5 && token.startsWith("لل")) out.add(token.substring(2))
    var t = token
    if (t.length >= 4 && t[0] in CLITICS) {
        t = t.substring(1)
        out.add(t)
    }
    if (t.length >= 5 && t.startsWith("ال")) out.add(t.substring(2))
    if (token.length >= 5 && token.startsWith("ال")) out.add(token.substring(2))
    return out
}

/**
 * Reads an Arabic sentence and decides which of the app's own abilities it asks for — «افتح صفحة الأصناف»، «فلتر الأصناف التي رصيدها
 * أقل من 10»، «ما الأصناف التي لم تحقق هدف الشهر؟»، «رتب الفروع حسب نسبة تحقيق الهدف»، «جهّز أمر شراء للنواقص»…
 *
 * This is what makes the assistant work with NO internet: a recognised command never waits for a model. It is deliberately
 * conservative — a sentence it does not clearly recognise returns null, so the caller can hand it to the language model when there
 * is one — and rule-based, so the same words always do the same thing.
 *
 * Everything is matched on the normalised text ([ArabicTextNormalizer]: أإآ→ا, ة→ه, ى→ي, Indic digits→0-9, punctuation→space), so the
 * keyword lists below are written in that spelling. One-word keywords are also found behind a one-letter prefix and the article
 * ("للأصناف" finds "اصناف"); several-word phrases must match as whole words.
 */
object OfflineCommandParser {

    fun parse(text: String, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): OfflineCommand? {
        val normalized = ArabicTextNormalizer.normalize(text)
        val w = Words(normalized)
        if (w.tokens.isEmpty()) return null

        // 1) help
        if (isHelp(w)) return OfflineCommand.Help

        // 2) "جهّز المسودة" — a follow-up to a purchase proposal
        if (w.has("مسوده") && w.has("جهز", "جهزي", "احفظ", "احفظي", "انشئ", "انشي", "اعمل", "سو", "اصنع", "حضر", "حضري", "جهزها")) {
            return OfflineCommand.DraftFromSuggestion
        }

        // 3) name matching — the names are taken from the ORIGINAL text, because commas and new lines are what separate them
        if (w.has("طابق", "طابقي", "طابقلي", "مطابقه", "تطابق")) {
            return OfflineCommand.MatchNames(extractNames(text))
        }

        // 4) a purchase proposal from the shortages
        if (asksForPurchaseProposal(w)) return OfflineCommand.SuggestPurchase(targetFactor(w, normalized))

        val navigates = w.has(*NAVIGATE_VERBS)
        val shows = w.has(*SHOW_VERBS)
        val screen = screenFor(w)

        // 5) "افتح الأهداف" is a trip to the Goals screen, not a question about the goals
        if (navigates && screen == "GOALS" && !w.has(*ANALYSIS_WORDS)) return OfflineCommand.OpenScreen("GOALS")

        // 6) reports: inbound vs sales, branch ranking, goals
        val goalWord = w.has("هدف", "اهداف")
        val month = parseMonth(normalized, now, zone)
        if (w.has("وارد") && (w.has("مبيعات") || w.has("قارن", "مقارنه", "مقابل", "بين"))) {
            val byBranch = w.hasPhrase("لكل فرع", "حسب الفرع", "حسب الفروع", "كل فرع", "بين الفروع", "علي مستوي الفروع")
            return OfflineCommand.Flows(month, byBranch)
        }
        if ((w.has("فروع") || w.has("فرع")) && w.has("رتب", "رتبي", "ترتيب", "افضل", "اسوا", "اضعف", "اقوي") &&
            (goalWord || w.has("تحقيق", "نسبه", "مبيعات", "اداء"))
        ) {
            return OfflineCommand.RankBranches(month)
        }
        if (goalWord) return OfflineCommand.Goals(month, onlyUnmet = asksForUnmet(w))

        // 7) the item list with filters
        val status = statusOf(w, normalized)
        val bounds = quantityBounds(w.tokens)
        val search = searchTerm(normalized)
        val branch = nameAfter(w.tokens, BRANCH_WORDS)
        val category = nameAfter(w.tokens, CATEGORY_WORDS)
        val hasBounds = bounds.below != null || bounds.above != null
        val itemWord = w.has("اصناف", "صنف", "منتجات", "منتج", "مخزون", "رصيد", "رصيدها", "رصيده", "بضاعه", "سلع", "كميه", "كميتها", "مخزونها")
        val hasFilter = status != null || hasBounds || search != null || branch != null || category != null
        if (hasFilter && (itemWord || status != null || search != null || (hasBounds && (navigates || shows)))) {
            return OfflineCommand.ShowInventory(
                status = status,
                below = bounds.below,
                above = bounds.above,
                inclusive = bounds.inclusive,
                search = search,
                branch = branch,
                category = category,
                openScreen = navigates
            )
        }

        // 8) just open a screen
        if (screen != null && (navigates || shows || w.tokens.size <= 2)) return OfflineCommand.OpenScreen(screen)

        return null
    }

    // ------------------------------------------------------------------ word matching

    private class Words(val normalized: String) {
        val tokens: List<String> = normalized.split(' ').filter { it.isNotEmpty() }
        private val padded = " $normalized "
        private val stems: Set<String> = tokens.flatMapTo(HashSet<String>()) { variantsOf(it) }

        fun has(vararg words: String): Boolean = words.any { it in stems }
        fun hasPhrase(vararg phrases: String): Boolean = phrases.any { padded.contains(" $it ") }
    }

    // ------------------------------------------------------------------ help, proposals

    private fun isHelp(w: Words): Boolean =
        w.hasPhrase(
            "ماذا تستطيع", "ماذا تفعل", "ماذا تقدر", "ايش تقدر", "شو تقدر", "كيف استخدمك", "كيف اتعامل معك",
            "ما هي اوامرك", "ما الاوامر", "الاوامر المتاحه", "ماذا يمكنك"
        ) || (w.has("مساعده", "اوامر", "help") && w.tokens.size <= 3)

    private fun asksForPurchaseProposal(w: Words): Boolean {
        val shortage = w.has("نواقص", "ناقص", "ناقصه", "نقص", "منخفضه", "منخفض")
        val prepares = w.has("جهز", "جهزي", "حضر", "حضري", "انشئ", "انشي", "اقترح", "اقتراح", "اعمل", "سو", "اصنع", "ابني", "اطلب", "اشتر", "اشتري")
        val purchase = w.has("شراء", "طلبيه", "طلب", "امر", "اوامر")
        if (prepares && purchase && shortage) return true
        if (w.has("اطلب", "اشتر", "اشتري") && shortage) return true
        if (w.has("اقترح", "اقتراح") && w.has("شراء")) return true
        return false
    }

    /** "ضعف" / "ضعفين" = 2, "ثلاثة أضعاف" / "3 أضعاف" = 3; null = the default. */
    private fun targetFactor(w: Words, normalized: String): Double? {
        Regex("""(\d+(?:\.\d+)?)\s*(?:اضعاف|ضعف|مرات|مره)""").find(normalized)?.groupValues?.get(1)?.toDoubleOrNull()?.let { return it }
        // "بثلاثة أضعاف": the number word can carry a one-letter prefix, so compare the token's variants, not the whole phrase
        for (i in 0 until w.tokens.size - 1) {
            if (w.tokens[i + 1] != "اضعاف" && w.tokens[i + 1] != "مرات") continue
            val forms = variantsOf(w.tokens[i])
            for ((word, value) in FACTOR_WORDS) if (word in forms) return value
        }
        if (w.has("ضعف", "ضعفين")) return 2.0
        return null
    }

    private fun asksForUnmet(w: Words): Boolean =
        w.hasPhrase(
            "لم تحقق", "لم يحقق", "لم تصل", "لم تبلغ", "ما حققت", "ما تحققت", "لم يتحقق", "لم تتحقق", "لم ينجز", "لم تنجز",
            "لم تكتمل", "لم يكتمل", "غير محققه", "غير المحققه", "غير منجزه", "دون الهدف", "تحت الهدف", "اقل من الهدف"
        ) || w.has("متاخره", "متخلفه", "مقصره") || (w.has("غير") && w.has("محققه", "منجزه", "مكتمله"))

    // ------------------------------------------------------------------ screens

    private fun screenFor(w: Words): String? = when {
        w.hasPhrase("طلب شراء جديد", "طلب شراء جديده", "امر شراء جديد", "فاتوره شراء جديده", "اضافه طلب شراء", "انشاء طلب شراء", "طلب جديد") -> "NEW_PURCHASE_REQUEST"
        w.hasPhrase("فاتوره بيع جديده", "فاتوره مبيعات جديده", "بيع جديد", "اضافه فاتوره بيع", "انشاء فاتوره بيع") -> "NEW_SALES_INVOICE"
        w.has("مشتريات", "شراء", "طلبيات") -> "PURCHASES"
        w.has("مبيعات", "بيع") -> "SALES"
        w.has("جرد") -> "COUNTING"
        w.has("اهداف", "هدف") -> "GOALS"
        w.has("تقارير", "تقرير") -> "REPORTS"
        w.hasPhrase("مركز البيانات") || w.has("استيراد") -> "DATA_CENTER"
        w.has("اعدادات", "ضبط") -> "SETTINGS"
        w.has("مزيد") -> "MORE"
        w.has("رئيسيه", "بدايه") -> "HOME"
        w.has("اصناف", "منتجات", "بضاعه", "سلع") -> "INVENTORY"
        w.has("مخزون") -> "STOCK"
        else -> null
    }

    // ------------------------------------------------------------------ inventory filters

    private fun statusOf(w: Words, normalized: String): String? {
        if (NEAR_EXPIRY.containsMatchIn(normalized)) return "NEAR_EXPIRY"
        if (w.hasPhrase("منتهيه الصلاحيه", "منتهي الصلاحيه", "انتهت صلاحيتها", "انتهت صلاحيه") || w.has("منتهيه", "منتهي", "منتهيات", "منتهين")) return "EXPIRED"
        if (w.hasPhrase("رصيدها صفر", "رصيد صفر", "بدون رصيد", "مخزونها صفر", "كميتها صفر", "نفذ مخزونها", "نفد مخزونها", "نفذت كميتها", "غير متوفره", "غير متوفر") ||
            w.has("نفذت", "نفد", "نفدت", "منعدمه", "خلصت", "صفريه")
        ) return "ZERO"
        if (w.hasPhrase("اوشكت علي النفاد", "توشك علي النفاد", "قاربت علي النفاد", "شارفت علي النفاد", "علي وشك النفاد", "قرب النفاد", "قاربت النفاد", "تحت الحد") ||
            w.has("منخفض", "منخفضه", "منخفضين", "ناقص", "ناقصه", "نواقص", "قليله", "قليل", "نفاد", "نقص")
        ) return "LOW"
        if (w.has("متوفره", "متوفر", "متاحه", "متاح")) return "AVAILABLE"
        return null
    }

    private class Bounds(val below: Double?, val above: Double?, val inclusive: Boolean)

    /**
     * Reads the quantity limits: «أقل من 10»، «تحت 5»، «أكثر من 20»، «10 أو أقل»، «لا يزيد عن 8»، «على الأقل 3»، «بين 5 و 10».
     * "أقل من 10" is strict (10 is NOT included) unless the person said "أو أقل / لا يزيد / على الأكثر".
     */
    private fun quantityBounds(tokens: List<String>): Bounds {
        val between = tokens.indexOf("بين")
        if (between >= 0) {
            val numbers = tokens.drop(between + 1).take(4).mapNotNull { numberOf(it) }
            if (numbers.size >= 2) return Bounds(below = maxOf(numbers[0], numbers[1]), above = minOf(numbers[0], numbers[1]), inclusive = false)
        }
        var below: Double? = null
        var above: Double? = null
        var inclusive = false
        for (i in tokens.indices) {
            val n = numberOf(tokens[i]) ?: continue
            val before = tokens.subList(maxOf(0, i - 3), i)
            val after = tokens.subList(i + 1, minOf(tokens.size, i + 3)).joinToString(" ")
            val beforeText = before.joinToString(" ")
            when {
                after.startsWith("او اقل") || after.startsWith("فاقل") || after.startsWith("فما دون") -> { below = n; inclusive = true }
                after.startsWith("او اكثر") || after.startsWith("فاكثر") || after.startsWith("فما فوق") -> { above = n; inclusive = true }
                beforeText.contains("علي الاقل") || beforeText.contains("كحد ادني") -> { above = n; inclusive = true }
                beforeText.contains("علي الاكثر") || beforeText.contains("كحد اقصي") -> { below = n; inclusive = true }
                before.any { it in GROW_VERBS } && "لا" in before -> { below = n; inclusive = true }
                before.any { it == "حتي" } -> { below = n; inclusive = true }
                before.any { it in BELOW_WORDS } -> below = n
                before.any { it in ABOVE_WORDS } -> above = n
            }
        }
        return Bounds(below, above, inclusive)
    }

    private fun numberOf(token: String): Double? {
        token.replace(',', '.').toDoubleOrNull()?.takeIf { !it.isNaN() && !it.isInfinite() }?.let { return it }
        return NUMBER_WORDS[token]
    }

    /** «ابحث عن صابون» → "صابون". */
    private fun searchTerm(normalized: String): String? {
        val match = Regex("""(?:ابحث|بحث|دور|دوري|فتش)\s+(?:لي\s+)?(?:عن|علي)\s+(?:صنف\s+|منتج\s+)?(.+)""").find(normalized) ?: return null
        return match.groupValues[1].trim().ifEmpty { null }
    }

    /** The words after "فرع" / "تصنيف" up to the next keyword — «في الفرع الأول» gives "الاول" (the tool matches it against the real names). */
    private fun nameAfter(tokens: List<String>, markers: Set<String>): String? {
        val start = tokens.indexOfFirst { token -> variantsOf(token).any { it in markers } }
        if (start < 0) return null
        val words = ArrayList<String>()
        for (i in start + 1 until tokens.size) {
            val token = tokens[i]
            if (token in STOP_AFTER_NAME || numberOf(token) != null) break
            words.add(token)
            if (words.size == 3) break
        }
        return words.joinToString(" ").ifEmpty { null }
    }

    // ------------------------------------------------------------------ months

    /** "2026-10", or null for "this month" (the default). Understands «الشهر الماضي», «2026-09», and month names such as «سبتمبر» or «أيلول». */
    private fun parseMonth(normalized: String, now: Long, zone: ZoneId): String? {
        val current = YearMonth.from(Instant.ofEpochMilli(now).atZone(zone))
        // the normaliser turns "-" and "/" into spaces, so "2026-10" arrives as the two tokens "2026" and "10"
        val tokens = normalized.split(' ').filter { it.isNotEmpty() }
        for (i in 0 until tokens.size - 1) {
            val year = tokens[i].takeIf { it.length == 4 && it.all { c -> c in '0'..'9' } }?.toInt() ?: continue
            val month = tokens[i + 1].takeIf { it.length <= 2 && it.isNotEmpty() && it.all { c -> c in '0'..'9' } }?.toInt() ?: continue
            if (year in 2000..2100 && month in 1..12) return YearMonth.of(year, month).toString()
        }
        val padded = " $normalized "
        if (padded.contains(" الشهر الماضي ") || padded.contains(" الشهر السابق ") || padded.contains(" الشهر الفائت ") || padded.contains(" الشهر اللي فات ")) {
            return current.minusMonths(1).toString()
        }
        for ((name, month) in MONTH_NAMES) {
            if (padded.contains(" $name ")) return YearMonth.of(current.year, month).toString()
        }
        return null
    }

    // ------------------------------------------------------------------ names to match

    /**
     * The names after the word "طابق" in the ORIGINAL text, one per line or separated by a comma / semicolon / "|". Leading filler such as
     * «الأسماء التالية» and list bullets ("- ", "1) ") are dropped. May be empty (the person said "طابق" and gave no names).
     */
    private fun extractNames(original: String): List<String> {
        val keywordAt = listOf("طابق", "مطابقة", "تطابق").map { original.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: return emptyList()
        var rest = original.substring(keywordAt)
        // skip the keyword itself (the rest of that word), and an optional colon
        val wordEnd = rest.indexOfFirst { it.isWhitespace() || it == ':' || it == '：' }
        rest = if (wordEnd < 0) "" else rest.substring(wordEnd).trimStart(' ', ':', '：', '-', '–', '\t')

        val lines = rest.split(Regex("""[\n\r،,;؛|]+""")).map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (lines.isNotEmpty()) lines[0] = dropFiller(lines[0])
        return lines
            .map { it.replace(Regex("""^\s*(?:[-•*]|\d+[.)])\s*"""), "").trim() }
            .filter { it.length >= 2 }
            .take(MAX_NAMES)
    }

    /** «الأسماء التالية صابون» → «صابون»: a few leading words that only introduce the list. */
    private fun dropFiller(line: String): String {
        val words = line.split(Regex("""\s+""")).toMutableList()
        var dropped = 0
        while (words.isNotEmpty() && dropped < 3 && ArabicTextNormalizer.normalize(words[0]) in FILLERS) {
            words.removeAt(0)
            dropped++
        }
        return words.joinToString(" ")
    }

    // ------------------------------------------------------------------ vocabulary (normalised spelling)

    private const val MAX_NAMES = 40

    private val NAVIGATE_VERBS = arrayOf("افتح", "افتحي", "افتحلي", "اذهب", "اذهبي", "انتقل", "روح", "خذني", "وديني", "توجه", "فلتر", "صفي")
    private val SHOW_VERBS = arrayOf("اعرض", "اعرضي", "اعرضلي", "اظهر", "اظهري", "اظهرلي", "وريني", "ورني", "شوف", "اريد", "ابي", "ابغي", "عرض", "هات", "اعطني")
    private val ANALYSIS_WORDS = arrayOf("لم", "ما", "كم", "نسبه", "حقق", "تحقق", "تحقيق", "حققت", "غير", "متاخره", "اصناف", "قارن", "رتب", "هل", "اي", "كيف", "وضع", "حاله", "تقدم")

    private val BELOW_WORDS = setOf("اقل", "ادني", "تحت", "دون", "اصغر", "يقل", "تقل")
    private val ABOVE_WORDS = setOf("اكثر", "اكبر", "فوق", "اعلي", "اكتر", "يزيد", "تزيد", "تتجاوز", "يتجاوز", "تفوق", "يفوق")
    private val GROW_VERBS = setOf("يزيد", "تزيد", "تتجاوز", "يتجاوز", "تفوق", "يفوق")

    private val BRANCH_WORDS = setOf("فرع")
    private val CATEGORY_WORDS = setOf("تصنيف", "فئه", "قسم")

    /** Words that end a branch / category name («الفرع الأول رصيدها أقل من…»). */
    private val STOP_AFTER_NAME = setOf(
        "التي", "الذي", "اللي", "الي", "رصيدها", "رصيده", "رصيد", "كميتها", "مخزونها", "اقل", "اكثر", "اكبر", "اعلي", "ادني", "تحت", "فوق",
        "منخفض", "منخفضه", "ناقص", "ناقصه", "نواقص", "منتهيه", "منتهي", "نفذت", "نفد", "صفر", "متوفره", "متوفر", "من", "او", "و", "ثم", "مع",
        "حسب", "بين", "عن", "علي", "لا", "هل", "كم", "ما", "ماذا", "قريبه", "قريب", "اوشكت", "قاربت", "تنتهي", "فقط", "الان", "لو", "سمحت", "ارجوك"
    )

    private val NEAR_EXPIRY = Regex("""قريب[هة]?\s+(?:من\s+)?(?:ال)?انتهاء|(?:توشك|اوشكت|تشارف|شارفت|قاربت|تقارب)\s+(?:علي\s+)?(?:ال)?انتهاء""")

    private val FILLERS = setOf("الاسماء", "اسماء", "هذه", "هذي", "الاصناف", "اصناف", "التاليه", "التالي", "الاتيه", "الاتي", "لي", "التالية", "الاسم", "اسم", "هذا")

    private val FACTOR_WORDS = listOf("ثلاثه" to 3.0, "ثلاث" to 3.0, "اربعه" to 4.0, "اربع" to 4.0, "خمسه" to 5.0, "خمس" to 5.0, "اثنين" to 2.0, "ضعفين" to 2.0)

    private val NUMBER_WORDS: Map<String, Double> = mapOf(
        "صفر" to 0.0, "واحد" to 1.0, "اثنين" to 2.0, "اثنان" to 2.0, "ثلاثه" to 3.0, "اربعه" to 4.0, "خمسه" to 5.0, "سته" to 6.0,
        "سبعه" to 7.0, "ثمانيه" to 8.0, "تسعه" to 9.0, "عشره" to 10.0, "عشرين" to 20.0, "ثلاثين" to 30.0, "اربعين" to 40.0,
        "خمسين" to 50.0, "ستين" to 60.0, "سبعين" to 70.0, "ثمانين" to 80.0, "تسعين" to 90.0, "مئه" to 100.0, "ميه" to 100.0, "مائه" to 100.0
    )

    /** Longer names first, so «كانون الاول» is found before a shorter name could match inside it. */
    private val MONTH_NAMES: List<Pair<String, Int>> = listOf(
        "كانون الثاني" to 1, "كانون الاول" to 12, "تشرين الثاني" to 11, "تشرين الاول" to 10,
        "يناير" to 1, "فبراير" to 2, "مارس" to 3, "ابريل" to 4, "مايو" to 5, "يونيو" to 6, "يوليو" to 7, "اغسطس" to 8,
        "سبتمبر" to 9, "اكتوبر" to 10, "نوفمبر" to 11, "ديسمبر" to 12,
        "شباط" to 2, "اذار" to 3, "نيسان" to 4, "ايار" to 5, "حزيران" to 6, "تموز" to 7, "ايلول" to 9
    )
}
