package com.inventorysmartai.app.domain.assistant

import com.inventorysmartai.app.domain.model.InvoiceStatus
import com.inventorysmartai.app.domain.model.SalesInvoice
import java.time.YearMonth
import java.time.ZoneId

/** A closed period in epoch millis ([from] .. [to], both included) with a short label for the answer. */
data class TimeWindow(val from: Long, val to: Long, val label: String) {
    fun contains(millis: Long): Boolean = millis in from..to
}

/** One goal with what was actually sold, as the "ما الأصناف التي لم تحقق الهدف" answer needs it. */
data class GoalProgress(
    val goalId: Long,
    val productId: Long,
    val productName: String,
    val target: Double,
    val achieved: Double,
    val periodStart: Long,
    val periodEnd: Long
) {
    val remaining: Double get() = (target - achieved).coerceAtLeast(0.0)

    /** Not capped at 100: a product that sold 130% of its target says 130. */
    val percent: Double get() = if (target > 0.0) achieved / target * 100.0 else 0.0
    val met: Boolean get() = target > 0.0 && achieved >= target
}

/** A stock line that came IN (a purchase receipt), reduced to what the comparison needs. */
data class InboundLine(val productId: Long, val branchId: Long, val quantity: Double)

data class BranchContribution(
    val branchId: Long,
    val branchName: String,
    val quantity: Double,
    /** This branch's sold quantity of the goal products ÷ the TOTAL target of those goals (the goals are company-wide). */
    val contributionPercent: Double,
    /** This branch's part of everything sold of the goal products. */
    val shareOfSoldPercent: Double
)

data class FlowRow(
    val productId: Long,
    val productName: String,
    val inbound: Double,
    val sold: Double,
    /** The company-wide target of the goals overlapping the period; null when the product has no goal in it. */
    val goalTarget: Double?
) {
    /** Positive = more came in than went out (stock built up). */
    val net: Double get() = inbound - sold
}

/**
 * The arithmetic behind the assistant's report tools, kept pure (plain lists in, plain numbers out) so it is unit-tested and so
 * the model never has to add anything up. Sales are counted from CONFIRMED invoices only — a draft or cancelled invoice moved no stock.
 */
object AssistantAnalytics {

    /**
     * The calendar month named by [month] ("2026-10"), or the month containing [now] when it is null or blank.
     * Returns null when [month] is given but is not a valid "YYYY-MM".
     */
    fun monthWindow(month: String?, now: Long, zone: ZoneId = ZoneId.systemDefault()): TimeWindow? {
        val text = month?.trim().orEmpty()
        val yearMonth = if (text.isEmpty()) {
            YearMonth.from(java.time.Instant.ofEpochMilli(now).atZone(zone))
        } else {
            try {
                YearMonth.parse(text)
            } catch (e: java.time.format.DateTimeParseException) {
                return null
            }
        }
        val from = yearMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val nextMonth = yearMonth.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return TimeWindow(from = from, to = nextMonth - 1, label = yearMonth.toString())
    }

    /** Two explicit dates (either may be missing: open-ended), or null when both are missing. */
    fun explicitWindow(from: Long?, to: Long?): TimeWindow? {
        if (from == null && to == null) return null
        val start = from ?: 0L
        val end = to ?: Long.MAX_VALUE
        if (end < start) return null
        return TimeWindow(start, end, "${from ?: "…"} → ${to ?: "…"}")
    }

    /** True when the goal's own period overlaps [window] at least by a moment. */
    fun overlaps(periodStart: Long, periodEnd: Long, window: TimeWindow): Boolean =
        periodStart <= window.to && periodEnd >= window.from

    /** Goals ranked worst first (lowest percent), optionally only the ones not met. */
    fun rankGoals(goals: List<GoalProgress>, onlyUnmet: Boolean): List<GoalProgress> =
        goals.filter { !onlyUnmet || !it.met }
            .sortedWith(compareBy<GoalProgress> { it.percent }.thenBy { it.productName })

    /** Confirmed sales per product inside [window], optionally for one branch. */
    fun soldByProduct(invoices: List<SalesInvoice>, window: TimeWindow, branchId: Long? = null): Map<Long, Double> {
        val result = LinkedHashMap<Long, Double>()
        for (invoice in invoices) {
            if (!countsAsSale(invoice, window, branchId)) continue
            for (item in invoice.items) result[item.productId] = (result[item.productId] ?: 0.0) + item.quantity
        }
        return result
    }

    /** Confirmed sales quantity per branch inside [window]. */
    fun soldByBranch(invoices: List<SalesInvoice>, window: TimeWindow): Map<Long, Double> {
        val result = LinkedHashMap<Long, Double>()
        for (invoice in invoices) {
            if (!countsAsSale(invoice, window, null)) continue
            result[invoice.branchId] = (result[invoice.branchId] ?: 0.0) + invoice.items.sumOf { it.quantity }
        }
        return result
    }

    fun inboundByProduct(lines: List<InboundLine>, branchId: Long? = null): Map<Long, Double> {
        val result = LinkedHashMap<Long, Double>()
        for (line in lines) {
            if (branchId != null && line.branchId != branchId) continue
            result[line.productId] = (result[line.productId] ?: 0.0) + line.quantity
        }
        return result
    }

    fun inboundByBranch(lines: List<InboundLine>): Map<Long, Double> {
        val result = LinkedHashMap<Long, Double>()
        for (line in lines) result[line.branchId] = (result[line.branchId] ?: 0.0) + line.quantity
        return result
    }

    /** The company-wide target per product, summed over the goals given (the caller passes only those overlapping the period). */
    fun targetsByProduct(goals: List<GoalProgress>): Map<Long, Double> {
        val result = LinkedHashMap<Long, Double>()
        for (goal in goals) result[goal.productId] = (result[goal.productId] ?: 0.0) + goal.target
        return result
    }

    /**
     * One row per product that came in, was sold, or has a goal in the period — most sold first. [names] gives the display name;
     * a product missing from it is shown as "صنف #id".
     */
    fun flowRows(
        inbound: Map<Long, Double>,
        sold: Map<Long, Double>,
        targets: Map<Long, Double>,
        names: Map<Long, String>
    ): List<FlowRow> {
        val ids = LinkedHashSet<Long>().apply { addAll(sold.keys); addAll(inbound.keys); addAll(targets.keys) }
        return ids.map { id ->
            FlowRow(
                productId = id,
                productName = names[id] ?: "صنف #$id",
                inbound = inbound[id] ?: 0.0,
                sold = sold[id] ?: 0.0,
                goalTarget = targets[id]
            )
        }.sortedWith(compareByDescending<FlowRow> { it.sold }.thenByDescending { it.inbound }.thenBy { it.productName })
    }

    /**
     * Ranks branches by what they sold of the products that have goals. The goals in this app belong to the whole company, not to
     * a branch, so a branch's "achievement" can only be its CONTRIBUTION: its sold quantity of each goal product over that goal's own
     * period, summed, divided by the total target of those goals. Every branch in [branchNames] is listed, even with zero.
     */
    fun branchContributions(
        goals: List<GoalProgress>,
        invoices: List<SalesInvoice>,
        branchNames: Map<Long, String>
    ): List<BranchContribution> {
        val perBranch = LinkedHashMap<Long, Double>()
        branchNames.keys.forEach { perBranch[it] = 0.0 }
        for (goal in goals) {
            val window = TimeWindow(goal.periodStart, goal.periodEnd, "")
            for (invoice in invoices) {
                if (!countsAsSale(invoice, window, null)) continue
                val quantity = invoice.items.filter { it.productId == goal.productId }.sumOf { it.quantity }
                if (quantity != 0.0) perBranch[invoice.branchId] = (perBranch[invoice.branchId] ?: 0.0) + quantity
            }
        }
        val totalTarget = goals.sumOf { it.target }
        val totalSold = perBranch.values.sum()
        return perBranch.map { (branchId, quantity) ->
            BranchContribution(
                branchId = branchId,
                branchName = branchNames[branchId] ?: "فرع #$branchId",
                quantity = quantity,
                contributionPercent = if (totalTarget > 0.0) quantity / totalTarget * 100.0 else 0.0,
                shareOfSoldPercent = if (totalSold > 0.0) quantity / totalSold * 100.0 else 0.0
            )
        }.sortedWith(compareByDescending<BranchContribution> { it.quantity }.thenBy { it.branchName })
    }

    fun round1(value: Double): Double = Math.round(value * 10.0) / 10.0
    fun round2(value: Double): Double = Math.round(value * 100.0) / 100.0

    private fun countsAsSale(invoice: SalesInvoice, window: TimeWindow, branchId: Long?): Boolean =
        invoice.status == InvoiceStatus.CONFIRMED &&
            window.contains(invoice.invoiceDate) &&
            (branchId == null || invoice.branchId == branchId)
}
