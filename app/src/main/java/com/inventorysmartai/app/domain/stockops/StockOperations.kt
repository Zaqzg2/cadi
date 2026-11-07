package com.inventorysmartai.app.domain.stockops

import com.inventorysmartai.app.domain.matching.ArabicTextNormalizer
import com.inventorysmartai.app.domain.model.MovementType
import kotlin.math.abs

/**
 * عمليات المخزون التي لا تمرّ بفاتورة: التحويل بين الفروع، ومرتجع البيع (يعيد البضاعة إلى الفرع)، ومرتجع الشراء (يُخرجها منه).
 *
 * لا جدول جديد لها عمدًا: قاعدة البيانات تستعمل fallbackToDestructiveMigration، أي تغيير في مخطط الجداول يمسح بيانات المستخدم.
 * فكل عملية تُسجَّل كحركات عادية في `inventory_movements` بنفس [referenceType] ورقم تسلسلي مشترك في `referenceId`،
 * وتُجمَّع حركاتها عند العرض ([StockOperationHistory]).
 */
enum class StockOperationType(val titleAr: String, val referenceType: String, val shortAr: String) {
    TRANSFER("تحويل بين الفروع", "STOCK_TRANSFER", "تحويل"),
    SALES_RETURN("مرتجع بيع", "SALES_RETURN", "مرتجع بيع"),
    PURCHASE_RETURN("مرتجع شراء", "PURCHASE_RETURN", "مرتجع شراء");

    companion object {
        fun fromReference(referenceType: String?): StockOperationType? = entries.firstOrNull { it.referenceType == referenceType }
    }
}

data class StockOperationLine(val productId: Long, val quantity: Double)

/**
 * [branchId] هو الفرع المُرسِل في التحويل، والفرع الذي تدخل إليه البضاعة (مرتجع بيع) أو تخرج منه (مرتجع شراء).
 * [partyLabel] نص جاهز للعرض مثل «العميل: محمد» (اختياري) يُحفظ مع الملاحظات.
 */
data class StockOperationRequest(
    val type: StockOperationType,
    val branchId: Long,
    val toBranchId: Long? = null,
    val partyLabel: String? = null,
    val lines: List<StockOperationLine>,
    val notes: String? = null
)

/** تغيير مخزون واحد اشتقّه [StockOperationPlanner]: [delta] سالب = تخرج الكمية من الفرع. */
data class PlannedMovement(val productId: Long, val branchId: Long, val type: MovementType, val delta: Double)

data class StockShortage(val productId: Long, val requested: Double, val available: Double)

/** طلب غير صالح (لا أصناف، كمية صفرية، فرعان متطابقان...) — الرسالة بالعربية وتُعرض للمستخدم كما هي. */
class InvalidStockOperationException(message: String) : Exception(message)

object StockOperationPlanner {

    /** يتحقق من الطلب ويدمج تكرار الصنف الواحد في سطر واحد (يحتفظ بترتيب أول ظهور). */
    fun validated(request: StockOperationRequest): StockOperationRequest {
        if (request.lines.isEmpty()) throw InvalidStockOperationException("أضف صنفًا واحدًا على الأقل")
        if (request.lines.any { !(it.quantity > 0.0) || it.quantity.isInfinite() }) {
            throw InvalidStockOperationException("الكمية يجب أن تكون أكبر من صفر")
        }
        if (request.type == StockOperationType.TRANSFER) {
            val to = request.toBranchId ?: throw InvalidStockOperationException("اختر الفرع المستلم")
            if (to == request.branchId) throw InvalidStockOperationException("الفرع المرسل والفرع المستلم يجب أن يختلفا")
        }
        val merged = LinkedHashMap<Long, Double>()
        for (line in request.lines) merged[line.productId] = (merged[line.productId] ?: 0.0) + line.quantity
        return request.copy(lines = merged.map { (productId, quantity) -> StockOperationLine(productId, quantity) })
    }

    /** الحركات التي ستُطبَّق: التحويل يُخرج من المرسِل ويُدخل إلى المستلِم، والمرتجعات حركة واحدة لكل صنف. */
    fun movements(request: StockOperationRequest): List<PlannedMovement> {
        val valid = validated(request)
        return valid.lines.flatMap { line ->
            when (valid.type) {
                StockOperationType.TRANSFER -> {
                    val to = valid.toBranchId ?: throw InvalidStockOperationException("اختر الفرع المستلم")
                    listOf(
                        PlannedMovement(line.productId, valid.branchId, MovementType.TRANSFER_OUT, -line.quantity),
                        PlannedMovement(line.productId, to, MovementType.TRANSFER_IN, line.quantity)
                    )
                }
                StockOperationType.SALES_RETURN ->
                    listOf(PlannedMovement(line.productId, valid.branchId, MovementType.RETURN_IN, line.quantity))
                StockOperationType.PURCHASE_RETURN ->
                    listOf(PlannedMovement(line.productId, valid.branchId, MovementType.RETURN_OUT, -line.quantity))
            }
        }
    }

    /** ما يخرج من [StockOperationRequest.branchId] فيجب أن يتوفر رصيده: التحويل ومرتجع الشراء. مرتجع البيع لا يُخرج شيئًا. */
    fun outgoing(request: StockOperationRequest): List<StockOperationLine> {
        val valid = validated(request)
        return if (valid.type == StockOperationType.SALES_RETURN) emptyList() else valid.lines
    }

    /** الأصناف التي رصيدها المتاح أقل من المطلوب. الأصناف غير الموجودة في [available] رصيدها صفر. */
    fun shortages(outgoing: List<StockOperationLine>, available: Map<Long, Double>): List<StockShortage> =
        outgoing.mapNotNull { line ->
            val have = available[line.productId] ?: 0.0
            if (have + EPSILON < line.quantity) StockShortage(line.productId, line.quantity, have) else null
        }

    /** الملاحظات المحفوظة مع الحركات: الطرف (عميل/مورد) ثم ملاحظة المستخدم، أو null إن لم يوجد شيء. */
    fun notesFor(request: StockOperationRequest): String? =
        listOfNotNull(
            request.partyLabel?.trim()?.takeIf { it.isNotEmpty() },
            request.notes?.trim()?.takeIf { it.isNotEmpty() }
        ).joinToString(" — ").ifEmpty { null }

    private const val EPSILON = 1e-9
}

/** حركة مخزون مخزَّنة كما هي (نسخة من صف `inventory_movements` كي يبقى التجميع دالة صرفة قابلة للاختبار). */
data class OperationMovement(
    val productId: Long,
    val branchId: Long,
    val movementType: String,
    val quantityChange: Double,
    val referenceType: String?,
    val referenceId: Long?,
    val notes: String?,
    val createdAt: Long
)

data class StockOperationLineView(val productId: Long, val productName: String, val quantity: Double)

/** عملية مجمَّعة للعرض في السجل. [number] تسلسلي داخل نوعها (تحويل #3 ومرتجع #3 عمليتان مختلفتان). */
data class StockOperation(
    val number: Long,
    val type: StockOperationType,
    val createdAt: Long,
    val branchId: Long?,
    val branchName: String?,
    val toBranchId: Long?,
    val toBranchName: String?,
    val lines: List<StockOperationLineView>,
    val notes: String?
)

object StockOperationHistory {

    /** بحث السجل: كل كلمة من [query] يجب أن ترد في النوع أو الرقم أو الفرعين أو الملاحظات أو أسماء الأصناف (تطبيع عربي). */
    fun matches(operation: StockOperation, query: String): Boolean {
        val words = ArabicTextNormalizer.normalize(query).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return true
        val haystack = ArabicTextNormalizer.normalize(
            buildString {
                append(operation.type.titleAr).append(' ').append(operation.number)
                append(' ').append(operation.branchName.orEmpty()).append(' ').append(operation.toBranchName.orEmpty())
                append(' ').append(operation.notes.orEmpty())
                operation.lines.forEach { append(' ').append(it.productName) }
            }
        )
        return words.all { haystack.contains(it) }
    }

    /** يجمّع حركات العمليات في عمليات: الأحدث أولًا. الحركات التي ليست من هذه العمليات تُتجاهل. */
    fun group(
        movements: List<OperationMovement>,
        productName: (Long) -> String?,
        branchName: (Long) -> String?
    ): List<StockOperation> =
        movements
            .filter { StockOperationType.fromReference(it.referenceType) != null && it.referenceId != null }
            .groupBy { (StockOperationType.fromReference(it.referenceType) ?: StockOperationType.TRANSFER) to (it.referenceId ?: 0L) }
            .map { (key, rows) ->
                val (type, number) = key
                // التحويل: الأصناف والفرع المرسل من حركات الخروج، والفرع المستلم من أول حركة دخول.
                val primary = if (type == StockOperationType.TRANSFER) rows.filter { it.movementType == MovementType.TRANSFER_OUT.name } else rows
                val destination = if (type == StockOperationType.TRANSFER) rows.firstOrNull { it.movementType == MovementType.TRANSFER_IN.name } else null
                val branchId = primary.firstOrNull()?.branchId
                StockOperation(
                    number = number,
                    type = type,
                    createdAt = rows.maxOf { it.createdAt },
                    branchId = branchId,
                    branchName = branchId?.let(branchName),
                    toBranchId = destination?.branchId,
                    toBranchName = destination?.branchId?.let(branchName),
                    lines = primary.groupBy { it.productId }.map { (productId, productRows) ->
                        StockOperationLineView(productId, productName(productId) ?: "صنف #$productId", productRows.sumOf { abs(it.quantityChange) })
                    },
                    notes = rows.firstNotNullOfOrNull { it.notes }
                )
            }
            .sortedWith(compareByDescending<StockOperation> { it.createdAt }.thenByDescending { it.number })
}
