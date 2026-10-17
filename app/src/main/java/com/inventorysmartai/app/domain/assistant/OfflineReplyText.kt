package com.inventorysmartai.app.domain.assistant

/**
 * The one short sentence that goes with each card when the assistant answers by itself (no model to word it). Pure and small:
 * the numbers are on the card, so the sentence only says what the card means. Arabic number agreement is handled here
 * ("صنف واحد"، "صنفان"، "5 أصناف"، "12 صنفًا") so no screen ever prints "1 أصناف".
 */
object OfflineReplyText {

    /** "صنف واحد" / "صنفان" / "5 أصناف" / "12 صنفًا". */
    fun items(count: Int): String = when {
        count == 1 -> "صنف واحد"
        count == 2 -> "صنفان"
        count in 3..10 -> "$count أصناف"
        else -> "$count صنفًا"
    }

    /** "هدف واحد" / "هدفان" / "5 أهداف" / "12 هدفًا". */
    fun goals(count: Int): String = when {
        count == 1 -> "هدف واحد"
        count == 2 -> "هدفان"
        count in 3..10 -> "$count أهداف"
        else -> "$count هدفًا"
    }

    fun inventory(result: Map<String, Any?>, opened: Boolean): String {
        val count = int(result, "matchingCount") ?: 0
        return when {
            count == 0 -> "لا توجد أصناف مطابقة لهذا الشرط."
            opened -> "فتحتُ قائمة الأصناف بعد تطبيق الفلتر: ${items(count)}."
            count > PREVIEW_ROWS -> "وجدتُ ${items(count)}؛ هذه أول $PREVIEW_ROWS، و«عرض الكل» يفتح القائمة كاملة."
            else -> "وجدتُ ${items(count)}."
        }
    }

    fun goalsAnalysis(result: Map<String, Any?>, onlyUnmet: Boolean): String {
        val period = str(result, "period") ?: "هذا الشهر"
        val total = int(result, "goalsInPeriod") ?: 0
        val met = int(result, "metCount") ?: 0
        val unmet = int(result, "unmetCount") ?: 0
        return when {
            total == 0 -> "لا توجد أهداف ذات كمية مستهدفة في $period."
            onlyUnmet && unmet == 0 -> "كل الأهداف (${goals(total)}) محققة في $period."
            onlyUnmet -> "لم يحقق $unmet من أصل ${goals(total)} في $period — الأبعد عن هدفه أولًا."
            else -> "في $period: حقق $met من أصل ${goals(total)}."
        }
    }

    fun flows(result: Map<String, Any?>): String {
        val period = str(result, "period") ?: "هذا الشهر"
        val inbound = number(result, "totalInbound") ?: 0.0
        val sold = number(result, "totalSold") ?: 0.0
        if (inbound == 0.0 && sold == 0.0) return "لا توجد حركة وارد ولا مبيعات مؤكدة في $period."
        val net = when {
            inbound > sold -> " تراكم ${AssistantCardBuilder.formatNumber(inbound - sold)} في المخزون."
            sold > inbound -> " خرج ${AssistantCardBuilder.formatNumber(sold - inbound)} أكثر مما دخل."
            else -> ""
        }
        return "في $period: الوارد ${AssistantCardBuilder.formatNumber(inbound)} مقابل مبيعات ${AssistantCardBuilder.formatNumber(sold)}.$net"
    }

    fun ranking(result: Map<String, Any?>): String {
        val branches = (result["branches"] as? List<*>).orEmpty()
        val top = branches.firstOrNull() as? Map<*, *>
        if (top == null) return str(result, "message") ?: "لا توجد أهداف في هذه الفترة لترتيب الفروع."
        val name = top["branchName"] as? String ?: "—"
        val percent = (top["contributionPercent"] as? Number)?.toDouble() ?: 0.0
        return "الأعلى مساهمة في الأهداف: $name (${AssistantCardBuilder.formatPercent(percent)}). الأهداف للشركة كلها، فنسبة كل فرع هي مساهمته في الهدف الإجمالي."
    }

    fun proposal(result: Map<String, Any?>): String {
        val shortCount = int(result, "shortCount") ?: 0
        if (shortCount == 0) return "لا توجد أصناف ناقصة الآن: كل الأصناف فوق حدّها الأدنى."
        return "عدد الأصناف الناقصة: $shortCount، واقترحتُ كمية لكل منها. راجعها ثم اضغط «تجهيز المسودة» — لن يُحفظ ولن يُرسل شيء قبل موافقتك."
    }

    fun matches(result: Map<String, Any?>): String {
        val summary = (result["summary"] as? Map<*, *>).orEmpty()
        val total = (summary["total"] as? Number)?.toInt() ?: 0
        val exact = (summary["exact"] as? Number)?.toInt() ?: 0
        val review = (summary["needReview"] as? Number)?.toInt() ?: 0
        val none = (summary["noMatch"] as? Number)?.toInt() ?: 0
        val hint = if (review > 0) " اضغط على اسم الصنف الصحيح لحفظه اسمًا بديلًا." else ""
        return "طابقتُ $total ${if (total == 1) "اسمًا" else "أسماء"}: $exact مطابق تمامًا، $review يحتاج مراجعتك، $none بلا تطابق.$hint"
    }

    private const val PREVIEW_ROWS = 8

    private fun str(map: Map<String, Any?>, key: String): String? = (map[key] as? String)?.takeIf { it.isNotBlank() }
    private fun number(map: Map<String, Any?>, key: String): Double? = (map[key] as? Number)?.toDouble()
    private fun int(map: Map<String, Any?>, key: String): Int? = number(map, key)?.toInt()
}
