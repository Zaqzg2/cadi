package com.inventorysmartai.app.domain.matching

/** One catalog entry a written name is compared against. [aliases] are the other names the product is known by (`Product.alternateNames`, one per line). */
data class MatchCandidate(
    val productId: Long,
    val name: String,
    val itemNumber: String? = null,
    val barcode: String? = null,
    val aliases: List<String> = emptyList()
)

/**
 * How sure the matcher is. Only [EXACT] may be used without asking the person; everything else is a SUGGESTION (the import
 * contract's rule: "matching must NEVER silently merge an uncertain match").
 *
 *  - [EXACT]: same barcode / item number, or the same name after Arabic normalisation (and no other product shares it).
 *  - [HIGH]: same words in another order, or one small spelling slip. Still shown to the person.
 *  - [MEDIUM] / [LOW]: plausible, needs a human decision.
 *  - [AMBIGUOUS]: two or more products fit almost equally well — the person must choose.
 *  - [NONE]: nothing resembles it.
 */
enum class MatchConfidence { EXACT, HIGH, MEDIUM, LOW, AMBIGUOUS, NONE }

/** [matchedOn] is the alias text when the score came from an alternate name instead of the product's own name. */
data class NameSuggestion(val productId: Long, val name: String, val score: Double, val matchedOn: String? = null)

data class NameMatch(
    val input: String,
    val confidence: MatchConfidence,
    val best: NameSuggestion?,
    val alternatives: List<NameSuggestion>,
    /** Short Arabic notes explaining why it is not a certain match (a different size, a matched alias). */
    val reasons: List<String> = emptyList()
) {
    val needsReview: Boolean get() = confidence != MatchConfidence.EXACT
}

/**
 * Matches names written in different ways ("صابون سائل ٥٠٠ مل" / "سائل صابون 500ml" / "صابون سايل 500 مل") to the catalog.
 *
 * Deterministic and explainable — no model involved, so the same input always gives the same answer and it works offline:
 *  1. barcode / item number equality;
 *  2. Arabic normalisation (alef and ta-marbuta variants, digits, tashkeel — [ArabicTextNormalizer]), then words are reduced
 *     lightly (the article "ال", the plural "ات", unit spellings such as ml/مل and kg/كجم);
 *  3. a score from how many words match (a word with a small typo still counts), taking word order out of the picture, or
 *     from the whole string with the spaces removed (OCR often drops or adds spaces) — whichever is higher;
 *  4. numbers are treated as part of the identity: "صابون 500 مل" is NOT "صابون 1 لتر", so differing numbers cut the score.
 *
 * Build one instance per batch of names (it prepares the catalog once) and call [match] for each name.
 */
class ProductNameMatcher(candidates: List<MatchCandidate>) {

    private class Prepared(val normalized: String, val squashed: String, val words: List<String>, val numbers: Set<String>)

    private class Entry(val candidate: MatchCandidate, val text: String, val isAlias: Boolean, val prepared: Prepared)

    private class Scored(val entry: Entry, val score: Double, val exactName: Boolean)

    private val entries: List<Entry> = candidates.flatMap { candidate ->
        buildList<Entry> {
            add(Entry(candidate, candidate.name, false, prepare(candidate.name)))
            candidate.aliases.filter { it.isNotBlank() }.forEach { alias -> add(Entry(candidate, alias, true, prepare(alias))) }
        }
    }

    private val byBarcode: Map<String, MatchCandidate> =
        candidates.filter { !it.barcode.isNullOrBlank() }.associateBy { identifier(it.barcode.orEmpty()) }

    private val byItemNumber: Map<String, MatchCandidate> =
        candidates.filter { !it.itemNumber.isNullOrBlank() }.associateBy { identifier(it.itemNumber.orEmpty()) }

    fun match(input: String, maxAlternatives: Int = 3): NameMatch {
        val raw = input.trim()
        if (raw.isEmpty()) return NameMatch(input, MatchConfidence.NONE, null, emptyList())

        // 1) a barcode or item number typed as the "name"
        val id = identifier(raw)
        val byCode = byBarcode[id]?.let { it to "الباركود" } ?: byItemNumber[id]?.let { it to "رقم الصنف" }
        if (byCode != null) {
            val suggestion = NameSuggestion(byCode.first.productId, byCode.first.name, 1.0)
            return NameMatch(input, MatchConfidence.EXACT, suggestion, emptyList(), listOf("طابق ${byCode.second}"))
        }

        // 2) score every catalog text, keep the best text per product
        val query = prepare(raw)
        if (query.normalized.isEmpty()) return NameMatch(input, MatchConfidence.NONE, null, emptyList())
        val bestPerProduct = LinkedHashMap<Long, Scored>()
        for (entry in entries) {
            val exact = entry.prepared.normalized == query.normalized
            val score = if (exact) 1.0 else similarity(query, entry.prepared)
            val current = bestPerProduct[entry.candidate.productId]
            if (current == null || score > current.score || (exact && !current.exactName)) {
                bestPerProduct[entry.candidate.productId] = Scored(entry, score, exact)
            }
        }
        val ranked = bestPerProduct.values.sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.entry.candidate.name })
        if (ranked.isEmpty()) return NameMatch(input, MatchConfidence.NONE, null, emptyList())

        val top = ranked[0]
        val rest = ranked.drop(1)
        val exactHits = ranked.filter { it.exactName }

        fun suggestion(scored: Scored) = NameSuggestion(
            productId = scored.entry.candidate.productId,
            name = scored.entry.candidate.name,
            score = round2(scored.score),
            matchedOn = if (scored.entry.isAlias) scored.entry.text else null
        )

        // 3) decide
        val confidence = when {
            exactHits.size == 1 -> MatchConfidence.EXACT
            exactHits.size > 1 -> MatchConfidence.AMBIGUOUS
            top.score < LOW_MIN -> MatchConfidence.NONE
            rest.isNotEmpty() && rest[0].score >= MEDIUM_MIN && top.score - rest[0].score < AMBIGUOUS_GAP -> MatchConfidence.AMBIGUOUS
            top.score >= HIGH_MIN -> MatchConfidence.HIGH
            top.score >= MEDIUM_MIN -> MatchConfidence.MEDIUM
            else -> MatchConfidence.LOW
        }

        val shownBest = if (confidence == MatchConfidence.EXACT) exactHits[0] else top
        val others = ranked.filter { it !== shownBest && it.score >= HINT_MIN }.take(maxAlternatives)
        val reasons = buildList<String> {
            if (confidence != MatchConfidence.NONE) {
                if (shownBest.entry.isAlias) add("طابق الاسم البديل «${shownBest.entry.text}»")
                val q = query.numbers
                val c = shownBest.entry.prepared.numbers
                if (q != c && (q.isNotEmpty() || c.isNotEmpty()) && !shownBest.exactName) {
                    add("اختلاف في الأرقام/الأحجام: «${q.joinToString(" ").ifEmpty { "—" }}» مقابل «${c.joinToString(" ").ifEmpty { "—" }}»")
                }
            }
        }
        return NameMatch(
            input = input,
            confidence = confidence,
            best = if (confidence == MatchConfidence.NONE) null else suggestion(shownBest),
            alternatives = others.map(::suggestion),
            reasons = reasons
        )
    }

    // ------------------------------------------------------------------ scoring

    private fun similarity(query: Prepared, candidate: Prepared): Double {
        val factor = numberFactor(query.numbers, candidate.numbers)
        val wordScore = wordDice(query.words, candidate.words)
        val squashedScore = if (query.squashed.isEmpty() || candidate.squashed.isEmpty()) 0.0 else stringSimilarity(query.squashed, candidate.squashed)
        return maxOf(wordScore, squashedScore) * factor
    }

    /** Same numbers: no cut. Some in common: a small cut. A number on one side only: a small cut. Different numbers: a big cut. */
    private fun numberFactor(a: Set<String>, b: Set<String>): Double = when {
        a == b -> 1.0
        a.isEmpty() || b.isEmpty() -> 0.9
        a.intersect(b).isNotEmpty() -> 0.85
        else -> 0.55
    }

    /** Dice coefficient over words, where two words match when they are equal or nearly equal (a one-letter slip in a longer word). */
    private fun wordDice(a: List<String>, b: List<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val pairs = ArrayList<Triple<Int, Int, Double>>()
        for (i in a.indices) for (j in b.indices) {
            val s = wordSimilarity(a[i], b[j])
            if (s >= WORD_MATCH_MIN) pairs += Triple(i, j, s)
        }
        pairs.sortByDescending { it.third }
        val usedA = BooleanArray(a.size)
        val usedB = BooleanArray(b.size)
        var matched = 0.0
        for ((i, j, s) in pairs) {
            if (usedA[i] || usedB[j]) continue
            usedA[i] = true
            usedB[j] = true
            matched += s
        }
        return 2.0 * matched / (a.size + b.size)
    }

    private fun wordSimilarity(a: String, b: String): Double {
        if (a == b) return 1.0
        // very short words must match exactly: one slip in a 3-letter word is a different word
        if (a.length < 4 || b.length < 4) return 0.0
        return stringSimilarity(a, b)
    }

    private fun stringSimilarity(a: String, b: String): Double {
        if (a == b) return 1.0
        val longest = maxOf(a.length, b.length)
        if (longest == 0) return 1.0
        return 1.0 - levenshtein(a, b).toDouble() / longest
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    // ------------------------------------------------------------------ preparation

    private fun prepare(text: String): Prepared {
        val normalized = ArabicTextNormalizer.normalize(text)
        val words = ArrayList<String>()
        val numbers = LinkedHashSet<String>()
        for (match in TOKEN.findAll(normalized)) {
            val token = match.value
            if (token[0].isDigit()) {
                numbers += canonicalNumber(token)
                continue
            }
            val letters = token.filter { it.isLetter() }
            if (letters.isEmpty()) continue
            words += reduceWord(UNIT_SYNONYMS[letters] ?: letters)
        }
        // a single leftover letter is noise ("و", stray OCR marks), but keep it when nothing else is left
        val kept = words.filter { it.length > 1 }.ifEmpty { words }
        return Prepared(
            normalized = normalized,
            squashed = (kept.joinToString("") + numbers.joinToString("")),
            words = kept,
            numbers = numbers
        )
    }

    /** Light Arabic reduction: the article "ال" and the plural "ات" — only on longer words so short names are never mangled. */
    private fun reduceWord(word: String): String {
        var result = word
        if (UNIT_VALUES.contains(result)) return result
        if (result.length >= 4 && result.startsWith("ال")) result = result.substring(2)
        if (result.length >= 5 && result.endsWith("ات")) result = result.substring(0, result.length - 2)
        return result
    }

    private fun canonicalNumber(token: String): String {
        // a very long run of digits is a code, not a size: keep it as written (a Double would round it)
        if (token.length > 15) return token
        val value = token.replace(',', '.').toDoubleOrNull() ?: return token
        return if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
    }

    /** Digits unified and spaces removed, so "٦٢٨٠٠١٠" and "6280010" compare equal. */
    private fun identifier(text: String): String = ArabicTextNormalizer.normalizeDigits(text.trim()).replace(" ", "").lowercase()

    private fun round2(value: Double): Double = Math.round(value * 100.0) / 100.0

    companion object {
        /** Scores at or above this are "the same word" when comparing two words. */
        const val WORD_MATCH_MIN = 0.78
        const val HIGH_MIN = 0.86
        const val MEDIUM_MIN = 0.70
        const val LOW_MIN = 0.52

        /** The best product must beat the runner-up by at least this much, or the match is [MatchConfidence.AMBIGUOUS]. */
        const val AMBIGUOUS_GAP = 0.04

        /** Weaker candidates are still shown as "closest names" when at least this similar. */
        const val HINT_MIN = 0.40

        // a run of digits (with an optional decimal part), or a run of anything that is not a digit or space
        private val TOKEN = Regex("\\d+(?:[.,]\\d+)?|[^\\d\\s]+")

        private val UNIT_SYNONYMS: Map<String, String> = buildMap {
            listOf("مل", "ملل", "مليلتر", "ml").forEach { put(it, "مل") }
            listOf("لتر", "ليتر", "ltr", "liter", "litre", "l").forEach { put(it, "لتر") }
            listOf("كجم", "كغ", "كغم", "كلغ", "كيلو", "كيلوجرام", "كيلوغرام", "kg").forEach { put(it, "كجم") }
            listOf("جم", "غم", "جرام", "غرام", "gm", "gr", "g").forEach { put(it, "جم") }
        }
        private val UNIT_VALUES: Set<String> = UNIT_SYNONYMS.values.toSet()
    }
}
