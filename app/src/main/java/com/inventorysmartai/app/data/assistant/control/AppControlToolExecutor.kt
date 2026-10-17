package com.inventorysmartai.app.data.assistant.control

import com.inventorysmartai.app.data.ai.provider.AiJson
import com.inventorysmartai.app.data.assistant.ToolArgs
import com.inventorysmartai.app.domain.assistant.AssistantUiBridge
import com.inventorysmartai.app.domain.assistant.UiCommand
import com.inventorysmartai.app.domain.inventory.InventoryListFilter
import com.inventorysmartai.app.domain.matching.ArabicTextNormalizer
import com.inventorysmartai.app.domain.model.InventoryStatusFilter
import com.inventorysmartai.app.domain.model.SortOrder
import com.inventorysmartai.app.domain.repository.CatalogRepository
import com.inventorysmartai.app.domain.repository.CountingRepository
import com.inventorysmartai.app.domain.repository.ProductRepository
import com.inventorysmartai.app.domain.repository.PurchaseRepository
import com.inventorysmartai.app.domain.repository.SalesRepository
import com.inventorysmartai.app.navigation.AssistantScreen
import com.inventorysmartai.app.navigation.Destination
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * The "control the app by voice or text" tools — the Kotlin counterpart of what Alibaba's Page Agent does on a web page, but
 * through named, validated actions instead of clicking whatever is on screen:
 *
 *  - `openScreen`     open one of the app's screens by name ("افتح صفحة الأصناف", "انتقل إلى المشتريات");
 *  - `showInventory`  open the item list with filters already applied ("اعرض الأصناف منتهية الصلاحية",
 *                     "فلتر الأصناف التي رصيدها أقل من 10") — and report what matched, so the answer in the chat is right too;
 *  - `openRecord`     open one product / purchase request / sales invoice / count (e.g. the draft order the assistant just made).
 *
 * None of them changes any data, so none asks for confirmation: they only post a navigation command to [AssistantUiBridge],
 * which the app shell turns into real navigation. Every failure is a small `{error, message}` the model can explain.
 */
@Singleton
class AppControlToolExecutor @Inject constructor(
    private val bridge: AssistantUiBridge,
    private val productRepository: ProductRepository,
    private val catalogRepository: CatalogRepository,
    private val purchaseRepository: PurchaseRepository,
    private val salesRepository: SalesRepository,
    private val countingRepository: CountingRepository
) {

    suspend fun execute(name: String, argumentsJson: String): String {
        val args: Map<String, Any?> = AiJson.parseObject(argumentsJson).orEmpty()
        return try {
            AiJson.toJson(dispatch(name, args))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AiJson.toJson(mapOf("error" to "tool_failed", "message" to "تعذّر تنفيذ الأداة $name"))
        }
    }

    private suspend fun dispatch(name: String, args: Map<String, Any?>): Map<String, Any?> = when (name) {
        "openScreen" -> openScreen(args)
        "showInventory" -> showInventory(args)
        "openRecord" -> openRecord(args)
        else -> mapOf("error" to "unknown_tool", "message" to "أداة غير معروفة: $name")
    }

    // ------------------------------------------------------------------ openScreen

    private fun openScreen(args: Map<String, Any?>): Map<String, Any?> {
        val wanted = ToolArgs.string(args, "screen") ?: return ToolArgs.missing("screen")
        val screen = AssistantScreen.fromKey(wanted) ?: return mapOf(
            "error" to "unknown_screen",
            "message" to "لا توجد شاشة بهذا الاسم: $wanted",
            "validScreens" to AssistantScreen.keys
        )
        val posted = bridge.post(UiCommand.Navigate(screen.route))
        return mapOf("opened" to posted, "screen" to screen.name, "label" to screen.labelAr)
    }

    // ------------------------------------------------------------------ showInventory

    private suspend fun showInventory(args: Map<String, Any?>): Map<String, Any?> {
        val status = parseStatus(ToolArgs.string(args, "status"))
            ?: return ToolArgs.invalid("status must be one of ${InventoryStatusFilter.entries.joinToString { it.name }}")
        val sort = parseSort(ToolArgs.string(args, "sortBy"))
            ?: return ToolArgs.invalid("sortBy must be one of ${SortOrder.entries.joinToString { it.name }}")

        // a branch / category may be given by id or by the name the person said ("الفرع الأول")
        val branches = catalogRepository.observeBranches().first()
        val (branchId, branchError) = resolveByIdOrName(
            id = ToolArgs.long(args, "branchId"),
            name = ToolArgs.string(args, "branch"),
            items = branches,
            idOf = { it.id },
            nameOf = { it.name },
            kind = "branch",
            kindAr = "فرع"
        )
        if (branchError != null) return branchError
        val categories = catalogRepository.observeCategories().first()
        val (categoryId, categoryError) = resolveByIdOrName(
            id = ToolArgs.long(args, "categoryId"),
            name = ToolArgs.string(args, "category"),
            items = categories,
            idOf = { it.id },
            nameOf = { it.name },
            kind = "category",
            kindAr = "تصنيف"
        )
        if (categoryError != null) return categoryError

        val filter = InventoryListFilter(
            searchQuery = ToolArgs.string(args, "search").orEmpty(),
            status = status,
            branchId = branchId,
            categoryId = categoryId,
            quantityBelow = ToolArgs.double(args, "quantityBelow"),
            quantityAbove = ToolArgs.double(args, "quantityAbove"),
            inclusiveBounds = ToolArgs.bool(args, "inclusive") ?: false,
            sortOrder = sort
        )

        // The same rules the screen will apply, so the number said in the chat is the number the person sees on the list.
        val all = productRepository.observeProductsWithStock().first()
        val matching = filter.filterAndSort(all)
        val route = Destination.InventoryFiltered.createRoute(filter)
        // open=false only reports what matches (the chat draws it as a card with an "عرض الكل" button); the default opens the list.
        val posted = if (ToolArgs.bool(args, "open") ?: true) bridge.post(UiCommand.Navigate(route)) else false

        return mapOf(
            "opened" to posted,
            "route" to route,
            "screen" to "الأصناف",
            "appliedFilters" to appliedFilters(
                filter,
                branchName = branches.firstOrNull { it.id == branchId }?.name,
                categoryName = categories.firstOrNull { it.id == categoryId }?.name
            ),
            "matchingCount" to matching.size,
            "totalProducts" to all.size,
            "shown" to matching.take(PREVIEW_ROWS).map { summary ->
                mapOf(
                    "productId" to summary.product.id,
                    "name" to summary.product.name,
                    "quantity" to filter.quantityOf(summary),
                    "status" to summary.status.name
                )
            },
            "note" to if (matching.size > PREVIEW_ROWS) "المعروض هنا أول $PREVIEW_ROWS فقط؛ القائمة الكاملة ظاهرة في شاشة الأصناف" else null
        )
    }

    private fun appliedFilters(filter: InventoryListFilter, branchName: String?, categoryName: String?): Map<String, Any?> = buildMap {
        if (filter.status != InventoryStatusFilter.ALL) put("status", filter.status.name)
        filter.quantityLabelAr()?.let { put("quantity", it) }
        if (filter.searchQuery.isNotBlank()) put("search", filter.searchQuery)
        filter.branchId?.let { put("branchId", it) }
        branchName?.let { put("branchName", it) }
        filter.categoryId?.let { put("categoryId", it) }
        categoryName?.let { put("categoryName", it) }
        if (filter.sortOrder != SortOrder.NAME_ASC) put("sortBy", filter.sortOrder.name)
    }

    /** null = not a valid value (blank means "no status filter"). */
    private fun parseStatus(text: String?): InventoryStatusFilter? =
        if (text == null) InventoryStatusFilter.ALL else InventoryStatusFilter.entries.firstOrNull { it.name.equals(text, ignoreCase = true) }

    private fun parseSort(text: String?): SortOrder? =
        if (text == null) SortOrder.NAME_ASC else SortOrder.entries.firstOrNull { it.name.equals(text, ignoreCase = true) }

    /**
     * Finds the one branch/category the person meant. Returns (id, null) when it was found — or when none was asked for (id null) —
     * and (null, error) when it is unknown or ambiguous; the error lists the options so the model can ask or retry with an id.
     */
    private fun <T> resolveByIdOrName(
        id: Long?,
        name: String?,
        items: List<T>,
        idOf: (T) -> Long,
        nameOf: (T) -> String,
        kind: String,
        kindAr: String
    ): Pair<Long?, Map<String, Any?>?> {
        if (id == null && name == null) return Pair(null, null)
        val found = if (id != null) items.filter { idOf(it) == id } else matchByName(name.orEmpty(), items, nameOf)
        if (found.size == 1) return Pair(idOf(found[0]), null)
        val unknown = found.isEmpty()
        val error: Map<String, Any?> = mapOf(
            "error" to (if (unknown) "unknown_$kind" else "ambiguous_$kind"),
            "message" to (if (unknown) "لا يوجد $kindAr بهذا الاسم أو الرقم" else "أكثر من $kindAr يطابق هذا الاسم"),
            "options" to items.map { mapOf("id" to idOf(it), "name" to nameOf(it)) }
        )
        return Pair(null, error)
    }

    /** An exact match after Arabic normalisation wins; otherwise every name that contains what was said. */
    private fun <T> matchByName(wanted: String, items: List<T>, nameOf: (T) -> String): List<T> {
        val query = ArabicTextNormalizer.normalize(wanted)
        if (query.isEmpty()) return emptyList()
        val exact = items.filter { ArabicTextNormalizer.normalize(nameOf(it)) == query }
        if (exact.isNotEmpty()) return exact
        return items.filter { ArabicTextNormalizer.normalize(nameOf(it)).contains(query) }
    }

    // ------------------------------------------------------------------ openRecord

    private suspend fun openRecord(args: Map<String, Any?>): Map<String, Any?> {
        val type = ToolArgs.string(args, "type")?.uppercase() ?: return ToolArgs.missing("type")
        val id = ToolArgs.long(args, "id") ?: return ToolArgs.missing("id")
        val route: String? = when (type) {
            "PRODUCT" -> productRepository.observeProductDetail(id).first()?.let { Destination.ProductDetail.createRoute(id) }
            "PURCHASE_REQUEST" -> purchaseRepository.observeRequest(id).first()?.let { Destination.PurchaseDetail.createRoute(id) }
            "SALES_INVOICE" -> salesRepository.observeInvoice(id).first()?.let { Destination.SalesDetail.createRoute(id) }
            "COUNT" -> countingRepository.observeCount(id).first()?.let { Destination.CountingDetail.createRoute(id) }
            else -> return ToolArgs.invalid("type must be one of PRODUCT, PURCHASE_REQUEST, SALES_INVOICE, COUNT")
        }
        if (route == null) return mapOf("opened" to false, "error" to "not_found", "message" to "لم يُعثر على السجل رقم $id")
        val posted = bridge.post(UiCommand.Navigate(route))
        return mapOf("opened" to posted, "type" to type, "id" to id)
    }

    private companion object {
        const val PREVIEW_ROWS = 8
    }
}
