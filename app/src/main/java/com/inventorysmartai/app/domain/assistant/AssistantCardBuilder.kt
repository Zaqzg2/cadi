package com.inventorysmartai.app.domain.assistant

/**
 * Turns the result of one of the assistant's tools (the JSON the tool returned, already parsed into maps) into the cards shown
 * under the answer. Pure: maps in, cards out — so it is unit-tested, and the SAME builder serves both paths: the offline engine
 * (which has no model to write a table) and the online loop (where the model writes one sentence and the card carries the numbers).
 *
 * A result that carries an `error`, or nothing worth drawing, gives no card; the caller then has only the text.
 */
object AssistantCardBuilder {

    private const val MAX_ITEM_ROWS = 8
    private const val MAX_GOAL_ROWS = 8
    private const val MAX_FLOW_ROWS = 6
    private const val MAX_PLAN_ROWS = 8
    private const val MAX_MATCH_CHOICES = 3
    private const val MAX_BRANCH_BUTTONS = 3

    fun fromToolResult(toolName: String, result: Map<String, Any?>): List<AssistantCard> {
        if (result.containsKey("error")) return emptyList()
        return when (toolName) {
            "showInventory" -> listOfNotNull(inventory(result))
            "analyzeGoals" -> listOfNotNull(goals(result))
            "rankBranchesByGoals" -> listOfNotNull(ranking(result))
            "compareFlows" -> listOfNotNull(flows(result))
            "suggestPurchaseOrder" -> listOfNotNull(purchasePlan(result))
            "matchProductNames" -> listOfNotNull(matches(result))
            else -> emptyList()
        }
    }

    // ------------------------------------------------------------------ inventory list

    private fun inventory(result: Map<String, Any?>): AssistantCard? {
        val shown = maps(result, "shown")
        val total = int(result, "matchingCount") ?: shown.size
        val applied = obj(result, "appliedFilters")

        val subtitle = buildList<String> {
            str(applied, "status")?.let { add("الحالة: ${statusLabel(it)}") }
            str(applied, "quantity")?.let { add(it) }
            str(applied, "branchName")?.let { add("الفرع: $it") }
            str(applied, "categoryName")?.let { add("التصنيف: $it") }
            str(applied, "search")?.let { add("بحث: $it") }
        }.joinToString(" · ").ifEmpty { null }

        val rows = shown.take(MAX_ITEM_ROWS).map { row ->
            val status = str(row, "status")
            ItemRow(
                name = str(row, "name") ?: "—",
                quantity = formatNumber(number(row, "quantity") ?: 0.0),
                statusLabel = statusLabel(status),
                tone = statusTone(status)
            )
        }
        val route = str(result, "route")
        val viewAll = if (route != null && total > 0) CardButton("عرض الكل ($total)", CardAction.OpenRoute(route), primary = true) else null
        val title = if (total == 0) "لا توجد أصناف مطابقة" else "الأصناف المطابقة"
        return AssistantCard.ItemList(title = title, subtitle = subtitle, rows = rows, totalCount = total, viewAll = viewAll)
    }

    // ------------------------------------------------------------------ goals

    private fun goals(result: Map<String, Any?>): AssistantCard? {
        val rows = maps(result, "goals")
        val total = int(result, "goalsInPeriod") ?: rows.size
        if (total == 0) return null
        val met = int(result, "metCount") ?: 0
        val unmet = int(result, "unmetCount") ?: 0
        val tiles = listOf(
            Tile("محققة", met.toString(), Tone.GOOD),
            Tile("غير محققة", unmet.toString(), Tone.DANGER),
            Tile("إجمالي الأهداف", total.toString(), Tone.INFO)
        )
        val goalRows = rows.take(MAX_GOAL_ROWS).map { row ->
            val percent = number(row, "percent") ?: 0.0
            val target = number(row, "target") ?: 0.0
            val achieved = number(row, "achieved") ?: 0.0
            GoalRowCard(
                name = str(row, "productName") ?: "—",
                percent = percent,
                detail = "${formatNumber(achieved)} من ${formatNumber(target)}",
                tone = percentTone(percent)
            )
        }
        return AssistantCard.Goals(
            title = "حالة الأهداف",
            subtitle = str(result, "period")?.let { "الفترة: $it" },
            tiles = tiles,
            rows = goalRows
        )
    }

    // ------------------------------------------------------------------ branches

    private fun ranking(result: Map<String, Any?>): AssistantCard? {
        val rows = maps(result, "branches")
        if (rows.isEmpty()) return null
        val top = rows.maxOf { number(it, "contributionPercent") ?: 0.0 }
        val ranked = rows.map { row ->
            val percent = number(row, "contributionPercent") ?: 0.0
            val sold = number(row, "soldQuantity") ?: 0.0
            RankRow(
                rank = int(row, "rank") ?: 0,
                name = str(row, "branchName") ?: "—",
                valueText = "${formatPercent(percent)} · ${formatNumber(sold)} وحدة",
                fraction = if (top > 0.0) (percent / top).toFloat().coerceIn(0f, 1f) else 0f,
                tone = if ((int(row, "rank") ?: 0) == 1) Tone.GOOD else Tone.INFO
            )
        }
        return AssistantCard.Ranking(
            title = "ترتيب الفروع",
            subtitle = "نسبة مساهمة كل فرع في أهداف الشركة (الأهداف للشركة كلها لا لكل فرع)",
            rows = ranked
        )
    }

    // ------------------------------------------------------------------ inbound vs sales

    private fun flows(result: Map<String, Any?>): AssistantCard? {
        val rows = maps(result, "rows")
        if (rows.isEmpty()) return null
        val byBranch = str(result, "groupBy") == "BRANCH"
        val bars = rows.take(MAX_FLOW_ROWS).map { row ->
            FlowBar(
                name = str(row, if (byBranch) "branchName" else "productName") ?: "—",
                inbound = number(row, "inbound") ?: 0.0,
                sold = number(row, "sold") ?: 0.0,
                goalTarget = number(row, "goalTarget")
            )
        }
        val tiles = buildList<Tile> {
            add(Tile("الوارد", formatNumber(number(result, "totalInbound") ?: 0.0), Tone.INFO))
            add(Tile("المبيعات", formatNumber(number(result, "totalSold") ?: 0.0), Tone.GOOD))
            number(result, "goalTargetTotal")?.takeIf { it > 0.0 }?.let { add(Tile("مستهدف الأهداف", formatNumber(it), Tone.WARNING)) }
        }
        return AssistantCard.Flows(
            title = if (byBranch) "الوارد والمبيعات لكل فرع" else "الوارد مقابل المبيعات",
            subtitle = str(result, "period")?.let { "الفترة: $it" },
            tiles = tiles,
            rows = bars
        )
    }

    // ------------------------------------------------------------------ purchase proposal

    private fun purchasePlan(result: Map<String, Any?>): AssistantCard? {
        val items = maps(result, "items")
        val shortCount = int(result, "shortCount") ?: items.size
        if (shortCount == 0 || items.isEmpty()) return null

        val lines = items.mapNotNull { item ->
            val productId = long(item, "productId") ?: return@mapNotNull null
            val quantity = number(item, "suggestedQuantity") ?: return@mapNotNull null
            if (quantity <= 0.0) return@mapNotNull null
            PurchaseLine(productId = productId, name = str(item, "name") ?: "—", quantity = quantity)
        }
        val rows = items.take(MAX_PLAN_ROWS).map { item ->
            val suggested = number(item, "suggestedQuantity")
            PlanRow(
                name = str(item, "name") ?: "—",
                current = formatNumber(number(item, "currentQuantity") ?: 0.0),
                suggested = suggested?.let { formatNumber(it) },
                note = str(item, "note")
            )
        }
        val tiles = listOf(
            Tile("أصناف ناقصة", shortCount.toString(), Tone.DANGER),
            Tile("إجمالي الكمية المقترحة", formatNumber(lines.sumOf { it.quantity }), Tone.INFO)
        )
        val branches = maps(result, "branches")
        val buttons = buildList<CardButton> {
            if (lines.isNotEmpty()) {
                when {
                    branches.size == 1 -> add(CardButton("تجهيز المسودة للمراجعة", CardAction.DraftPurchase(long(branches[0], "id"), lines), primary = true))
                    branches.isEmpty() -> add(CardButton("تجهيز المسودة للمراجعة", CardAction.DraftPurchase(null, lines), primary = true))
                    else -> branches.take(MAX_BRANCH_BUTTONS).forEachIndexed { index, branch ->
                        val id = long(branch, "id")
                        add(CardButton("مسودة لفرع ${str(branch, "name") ?: "#$id"}", CardAction.DraftPurchase(id, lines), primary = index == 0))
                    }
                }
            }
        }
        return AssistantCard.PurchasePlan(
            title = "اقتراح أمر شراء",
            subtitle = str(result, "rule"),
            tiles = tiles,
            rows = rows,
            hiddenCount = (items.size - rows.size).coerceAtLeast(0),
            buttons = buttons
        )
    }

    // ------------------------------------------------------------------ name matching

    private fun matches(result: Map<String, Any?>): AssistantCard? {
        val results = maps(result, "results")
        if (results.isEmpty()) return null
        val rows = results.map { row ->
            val input = str(row, "input") ?: "—"
            val confidence = str(row, "confidence")
            val best = obj(row, "match")
            val alternatives = maps(row, "alternatives")
            val bestName = str(best, "productName")

            val choices = buildList<CardButton> {
                if (confidence != "EXACT" && confidence != "NONE") {
                    val candidates = buildList<Map<String, Any?>> {
                        if (best.isNotEmpty()) add(best)
                        addAll(alternatives)
                    }.take(MAX_MATCH_CHOICES)
                    candidates.forEachIndexed { index, candidate ->
                        val productId = long(candidate, "productId") ?: return@forEachIndexed
                        val name = str(candidate, "productName") ?: return@forEachIndexed
                        add(CardButton(name, CardAction.SaveAlias(productId, name, input), primary = index == 0))
                    }
                }
            }
            MatchRowCard(
                input = input,
                confidenceLabel = confidenceLabel(confidence),
                tone = confidenceTone(confidence),
                matchedName = if (confidence == "NONE") null else bestName,
                detail = (row["reasons"] as? List<*>)?.mapNotNull { it as? String }?.joinToString("، ")?.ifEmpty { null },
                choices = choices
            )
        }
        val summary = obj(result, "summary")
        val exact = int(summary, "exact") ?: 0
        val review = int(summary, "needReview") ?: 0
        val none = int(summary, "noMatch") ?: 0
        return AssistantCard.NameMatches(
            title = "نتيجة مطابقة الأسماء",
            subtitle = "$exact مطابق تمامًا · $review يحتاج مراجعتك · $none بلا تطابق",
            rows = rows
        )
    }

    // ------------------------------------------------------------------ labels and tones

    fun statusLabel(status: String?): String = when (status) {
        "AVAILABLE" -> "متوفر"
        "LOW" -> "منخفض"
        "ZERO" -> "صفر"
        "NEAR_EXPIRY" -> "قريب الانتهاء"
        "EXPIRED" -> "منتهي"
        else -> status ?: "—"
    }

    fun statusTone(status: String?): Tone = when (status) {
        "AVAILABLE" -> Tone.GOOD
        "LOW" -> Tone.DANGER
        "ZERO" -> Tone.NEUTRAL
        "NEAR_EXPIRY" -> Tone.WARNING
        "EXPIRED" -> Tone.DANGER
        else -> Tone.NEUTRAL
    }

    /** 100% or more is met (green); 70% to 99% is close (amber); below that is behind (red). */
    fun percentTone(percent: Double): Tone = when {
        percent >= 100.0 -> Tone.GOOD
        percent >= 70.0 -> Tone.WARNING
        else -> Tone.DANGER
    }

    private fun confidenceLabel(confidence: String?): String = when (confidence) {
        "EXACT" -> "مطابق تمامًا"
        "HIGH" -> "مطابقة قوية — راجعها"
        "MEDIUM" -> "مطابقة محتملة"
        "LOW" -> "مطابقة ضعيفة"
        "AMBIGUOUS" -> "أكثر من احتمال"
        else -> "لا يوجد تطابق"
    }

    private fun confidenceTone(confidence: String?): Tone = when (confidence) {
        "EXACT" -> Tone.GOOD
        "HIGH" -> Tone.INFO
        "MEDIUM", "LOW", "AMBIGUOUS" -> Tone.WARNING
        else -> Tone.DANGER
    }

    // ------------------------------------------------------------------ number and text formatting

    /** "10" instead of "10.0", one decimal otherwise — Western digits, the same as the rest of the app. */
    fun formatNumber(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "—"
        if (value % 1.0 == 0.0 && value >= Long.MIN_VALUE.toDouble() && value <= Long.MAX_VALUE.toDouble()) return value.toLong().toString()
        return (Math.round(value * 10.0) / 10.0).toString()
    }

    fun formatPercent(value: Double): String = formatNumber(value) + "%"

    // ------------------------------------------------------------------ reading the parsed JSON

    private fun str(map: Map<String, Any?>, key: String): String? = (map[key] as? String)?.takeIf { it.isNotBlank() }

    private fun number(map: Map<String, Any?>, key: String): Double? = (map[key] as? Number)?.toDouble()

    private fun int(map: Map<String, Any?>, key: String): Int? = number(map, key)?.toInt()

    private fun long(map: Map<String, Any?>, key: String): Long? = number(map, key)?.toLong()

    @Suppress("UNCHECKED_CAST")
    private fun obj(map: Map<String, Any?>, key: String): Map<String, Any?> = (map[key] as? Map<String, Any?>).orEmpty()

    @Suppress("UNCHECKED_CAST")
    private fun maps(map: Map<String, Any?>, key: String): List<Map<String, Any?>> =
        (map[key] as? List<*>)?.mapNotNull { it as? Map<String, Any?> }.orEmpty()
}
