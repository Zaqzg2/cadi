package com.inventorysmartai.app.data.assistant.smart

import com.inventorysmartai.app.data.ai.provider.AiJson
import com.inventorysmartai.app.data.assistant.ToolArgs
import com.inventorysmartai.app.domain.assistant.AssistantUiBridge
import com.inventorysmartai.app.domain.assistant.PurchasePlanner
import com.inventorysmartai.app.domain.assistant.ShortageLine
import com.inventorysmartai.app.domain.assistant.UiCommand
import com.inventorysmartai.app.domain.model.PurchaseRequest
import com.inventorysmartai.app.domain.model.PurchaseRequestItem
import com.inventorysmartai.app.domain.model.PurchaseStatus
import com.inventorysmartai.app.domain.repository.CatalogRepository
import com.inventorysmartai.app.domain.repository.PartyRepository
import com.inventorysmartai.app.domain.repository.ProductRepository
import com.inventorysmartai.app.domain.repository.PurchaseRepository
import com.inventorysmartai.app.navigation.Destination
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * "تجهيز أمر شراء بناءً على النواقص، مع مراجعتك قبل اعتماده" — in two steps, with the person in the loop:
 *
 *  1. `suggestPurchaseOrder` (read-only) finds what is short and how much to buy, using [PurchasePlanner] — exact arithmetic, and the
 *     rule it applied is returned so the assistant can state it;
 *  2. `draftPurchaseOrder` (a WRITE, so the app asks the person first) saves the agreed lines as a **DRAFT** purchase request and
 *     opens it on the normal purchase screen. The assistant never sends or approves it: "إرسال الطلب" there is the person's button.
 */
@Singleton
class PurchasePlanningToolExecutor @Inject constructor(
    private val productRepository: ProductRepository,
    private val catalogRepository: CatalogRepository,
    private val partyRepository: PartyRepository,
    private val purchaseRepository: PurchaseRepository,
    private val bridge: AssistantUiBridge
) {

    /** The exact Arabic sentence shown in the confirmation dialog (names the branch and a few of the products). */
    suspend fun describeForConfirmation(name: String, argumentsJson: String): String {
        if (name != "draftPurchaseOrder") return "تنفيذ العملية: $name"
        return try {
            val args = AiJson.parseObject(argumentsJson).orEmpty()
            val items = ToolArgs.list(args, "items").orEmpty().mapNotNull { ToolArgs.obj(it) }
            val branches = catalogRepository.observeBranches().first().filter { it.isActive }
            val requested = ToolArgs.long(args, "branchId")
            val branchName = (if (requested != null) branches.firstOrNull { it.id == requested } else branches.singleOrNull())?.name
            val productNames = productRepository.observeProducts().first().associate { it.id to it.name }
            val sample = items.mapNotNull { item -> ToolArgs.long(item, "productId")?.let { productNames[it] } }.distinct().take(SAMPLE_NAMES)
            buildString {
                append("تجهيز مسودة طلب شراء")
                append(if (branchName != null) " لفرع «$branchName»" else " (الفرع غير محدد بعد)")
                append(" تتضمن ${items.size} صنف/أصناف")
                if (sample.isNotEmpty()) append(" (مثل: ${sample.joinToString("، ")})")
                append(". ستُحفظ كمسودة وتُفتح لمراجعتها وتعديلها، ولن تُرسل إلا بموافقتك.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "تجهيز مسودة طلب شراء جديدة لمراجعتها قبل الإرسال"
        }
    }

    suspend fun execute(name: String, argumentsJson: String): String {
        val args: Map<String, Any?> = AiJson.parseObject(argumentsJson).orEmpty()
        return try {
            AiJson.toJson(
                when (name) {
                    "suggestPurchaseOrder" -> suggestPurchaseOrder(args)
                    "draftPurchaseOrder" -> draftPurchaseOrder(args)
                    else -> mapOf("error" to "unknown_tool", "message" to "أداة غير معروفة: $name")
                }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AiJson.toJson(mapOf("error" to "tool_failed", "message" to "تعذّر تنفيذ الأداة $name"))
        }
    }

    // ------------------------------------------------------------------ suggestPurchaseOrder

    private suspend fun suggestPurchaseOrder(args: Map<String, Any?>): Map<String, Any?> {
        val factor = (ToolArgs.double(args, "targetFactor") ?: PurchasePlanner.DEFAULT_TARGET_FACTOR)
            .coerceIn(PurchasePlanner.MIN_TARGET_FACTOR, PurchasePlanner.MAX_TARGET_FACTOR)
        val limit = (ToolArgs.int(args, "limit") ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
        val categoryId = ToolArgs.long(args, "categoryId")

        val summaries = productRepository.observeProductsWithStock().first()
            .filter { categoryId == null || it.product.categoryId == categoryId }
        val lines = PurchasePlanner.shortages(summaries, factor)
        val branches = catalogRepository.observeBranches().first().filter { it.isActive }
        val suppliers = partyRepository.observeSuppliers().first()

        return mapOf(
            "shortCount" to lines.size,
            "targetFactor" to factor,
            "rule" to PurchasePlanner.ruleDescription(factor),
            "items" to lines.take(limit).map { it.toJson() },
            "truncated" to (lines.size > limit),
            "branches" to branches.map { mapOf("id" to it.id, "name" to it.name) },
            "suppliers" to suppliers.take(MAX_SUPPLIERS).map { mapOf("id" to it.id, "name" to it.name) },
            "nextStep" to "اعرض القائمة والقاعدة المستخدمة على المستخدم، وعدّل الكميات إن طلب، ثم استدعِ draftPurchaseOrder بالكميات النهائية (يحفظ مسودة تُراجَع قبل الإرسال)"
        )
    }

    private fun ShortageLine.toJson(): Map<String, Any?> = mapOf(
        "productId" to productId,
        "name" to productName,
        "currentQuantity" to currentQuantity,
        "minStock" to minStock,
        "reorderPoint" to reorderPoint,
        "targetQuantity" to targetQuantity,
        "suggestedQuantity" to suggestedQuantity,
        "needsManualQuantity" to (suggestedQuantity == null),
        "status" to status.name,
        "note" to note
    )

    // ------------------------------------------------------------------ draftPurchaseOrder

    private suspend fun draftPurchaseOrder(args: Map<String, Any?>): Map<String, Any?> {
        val rawItems = ToolArgs.list(args, "items") ?: return ToolArgs.missing("items")
        val wanted = LinkedHashMap<Long, Double>()
        for (raw in rawItems) {
            val item = ToolArgs.obj(raw) ?: continue
            val productId = ToolArgs.long(item, "productId") ?: return ToolArgs.invalid("each item needs a productId")
            val quantity = ToolArgs.double(item, "quantity") ?: return ToolArgs.invalid("each item needs a quantity (productId=$productId)")
            if (quantity <= 0.0) return ToolArgs.invalid("quantity must be greater than zero (productId=$productId)")
            wanted[productId] = (wanted[productId] ?: 0.0) + quantity
        }
        if (wanted.isEmpty()) return ToolArgs.missing("items")

        val branches = catalogRepository.observeBranches().first().filter { it.isActive }
        val requestedBranch = ToolArgs.long(args, "branchId")
        val branch = (if (requestedBranch != null) branches.firstOrNull { it.id == requestedBranch } else branches.singleOrNull())
            ?: return mapOf(
                "error" to "branch_required",
                "message" to "حدّد الفرع الذي سيستلم الطلب",
                "branches" to branches.map { mapOf("id" to it.id, "name" to it.name) }
            )

        val supplierId = ToolArgs.long(args, "supplierId")
        if (supplierId != null) {
            val suppliers = partyRepository.observeSuppliers().first()
            if (suppliers.none { it.id == supplierId }) {
                return mapOf(
                    "error" to "unknown_supplier",
                    "message" to "لا يوجد مورد بهذا الرقم",
                    "suppliers" to suppliers.take(MAX_SUPPLIERS).map { mapOf("id" to it.id, "name" to it.name) }
                )
            }
        }

        val stock = productRepository.observeProductsWithStock().first().associateBy { it.product.id }
        val unknown = wanted.keys.filter { it !in stock }
        if (unknown.isNotEmpty()) {
            return mapOf("error" to "unknown_product", "message" to "أصناف غير موجودة في الكتالوج", "productIds" to unknown)
        }

        val items = wanted.map { (productId, quantity) ->
            PurchaseRequestItem(
                productId = productId,
                currentStockSnapshot = stock.getValue(productId).totalQuantity,
                requestedQuantity = quantity
            )
        }
        val notes = listOfNotNull(AI_NOTE, ToolArgs.string(args, "notes")).joinToString("\n")
        val request = PurchaseRequest(
            requestNumber = "PR-AI-${System.currentTimeMillis()}",
            supplierId = supplierId,
            branchId = branch.id,
            requestDate = System.currentTimeMillis(),
            status = PurchaseStatus.DRAFT, // never SUBMITTED: sending it is the person's decision, on the purchase screen
            notes = notes,
            items = items
        )
        val id = purchaseRepository.saveRequest(request)
        val opened = bridge.post(UiCommand.Navigate(Destination.PurchaseDetail.createRoute(id)))

        return mapOf(
            "created" to true,
            "purchaseRequestId" to id,
            "requestNumber" to request.requestNumber,
            "status" to PurchaseStatus.DRAFT.name,
            "branch" to branch.name,
            "itemCount" to items.size,
            "reviewScreenOpened" to opened,
            "message" to "حُفظت مسودة طلب الشراء وفُتحت للمراجعة. لم تُرسل بعد: يراجعها المستخدم ويعدّل الكميات ثم يضغط «إرسال الطلب» بنفسه."
        )
    }

    private companion object {
        const val DEFAULT_LIMIT = 40
        const val MAX_LIMIT = 200
        const val MAX_SUPPLIERS = 20
        const val SAMPLE_NAMES = 3
        const val AI_NOTE = "مسودة جهّزها المساعد الذكي من النواقص — راجعها قبل الإرسال"
    }
}
