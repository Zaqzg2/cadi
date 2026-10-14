package com.inventorysmartai.app.domain.assistant

import com.inventorysmartai.app.domain.model.InventoryStatus
import com.inventorysmartai.app.domain.model.ProductStockSummary
import kotlin.math.ceil

/** One product that is short of stock, with how much to buy to bring it back to a comfortable level. */
data class ShortageLine(
    val productId: Long,
    val productName: String,
    val currentQuantity: Double,
    val minStock: Double,
    val reorderPoint: Double,
    /** The level the suggestion aims for; null when the product has no minimum / reorder point to aim at. */
    val targetQuantity: Double?,
    /** How much to order; null when it cannot be worked out (no thresholds) — the person has to decide. */
    val suggestedQuantity: Double?,
    val status: InventoryStatus,
    /** An Arabic remark worth showing (stock includes expired or near-expiry batches, no thresholds set...). */
    val note: String? = null
)

/**
 * Works out which products are short and how much to order — the numbers the assistant must never invent or add up itself.
 *
 * The rule is deliberately simple and is stated back to the person with the result, because it is a business choice, not a fact:
 *  - a product is SHORT when its stock is at or below its trigger level, where trigger = the larger of its minimum stock and its
 *    reorder point (the app already calls "at or below the minimum" low stock), or when its stock is zero;
 *  - the order aims for [targetFactor] x the trigger level (default 2: back to twice the minimum), so a purchase does not leave the
 *    product low again the very next day: suggested = ceil(target − current);
 *  - a product with NO thresholds set and zero stock is listed with no suggested quantity — the planner has nothing to aim at.
 *
 * Stock is the total over all branches, the same number the Inventory screen and the LOW/ZERO statuses use.
 */
object PurchasePlanner {

    const val DEFAULT_TARGET_FACTOR = 2.0
    const val MIN_TARGET_FACTOR = 1.0
    const val MAX_TARGET_FACTOR = 10.0

    /** One sentence describing the rule, handed to the model so it states the assumption to the person. */
    fun ruleDescription(targetFactor: Double): String =
        "الصنف ناقص إذا كان رصيده أقل من أو يساوي الحد الأدنى (أو نقطة إعادة الطلب أيهما أكبر) أو صفرًا؛ " +
            "والكمية المقترحة = (${formatFactor(targetFactor)} × ذلك الحد) − الرصيد الحالي، مقرّبة للأعلى."

    fun shortages(
        summaries: List<ProductStockSummary>,
        targetFactor: Double = DEFAULT_TARGET_FACTOR,
        limit: Int = Int.MAX_VALUE
    ): List<ShortageLine> {
        val factor = targetFactor.coerceIn(MIN_TARGET_FACTOR, MAX_TARGET_FACTOR)
        val lines = summaries.mapNotNull { summary -> lineFor(summary, factor) }
        // the most urgent first: no stock at all, then the lowest stock relative to what it should be
        return lines
            .sortedWith(compareBy<ShortageLine> { if (it.currentQuantity <= 0.0) 0 else 1 }.thenBy { urgency(it) }.thenBy { it.productName })
            .take(limit.coerceAtLeast(0))
    }

    private fun lineFor(summary: ProductStockSummary, factor: Double): ShortageLine? {
        val product = summary.product
        val quantity = summary.totalQuantity
        val trigger = maxOf(product.minStock, product.reorderPoint)
        val isShort = quantity <= 0.0 || (trigger > 0.0 && quantity <= trigger)
        if (!isShort) return null

        val target = if (trigger > 0.0) trigger * factor else null
        val suggested = target?.let { ceil(it - quantity).coerceAtLeast(0.0) }
        val note = when {
            summary.status == InventoryStatus.EXPIRED -> "يحتوي دفعة منتهية الصلاحية — الرصيد الفعلي الصالح قد يكون أقل"
            summary.status == InventoryStatus.NEAR_EXPIRY -> "يحتوي دفعة قريبة من الانتهاء"
            trigger <= 0.0 -> "لم يُحدَّد له حد أدنى ولا نقطة إعادة طلب، فحدّد الكمية بنفسك"
            else -> null
        }
        return ShortageLine(
            productId = product.id,
            productName = product.name,
            currentQuantity = quantity,
            minStock = product.minStock,
            reorderPoint = product.reorderPoint,
            targetQuantity = target,
            suggestedQuantity = suggested,
            status = summary.status,
            note = note
        )
    }

    /** Lower = more urgent: stock as a share of the trigger level (products with no trigger sort last among the non-zero ones). */
    private fun urgency(line: ShortageLine): Double {
        val trigger = maxOf(line.minStock, line.reorderPoint)
        return if (trigger > 0.0) line.currentQuantity / trigger else Double.MAX_VALUE
    }

    private fun formatFactor(factor: Double): String =
        if (factor % 1.0 == 0.0) factor.toLong().toString() else factor.toString()
}
