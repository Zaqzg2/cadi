package com.inventorysmartai.app.data.assistant.smart

import com.inventorysmartai.app.data.ai.provider.AiJson
import com.inventorysmartai.app.data.assistant.ToolArgs
import com.inventorysmartai.app.data.local.database.dao.InventoryMovementDao
import com.inventorysmartai.app.domain.assistant.AssistantAnalytics
import com.inventorysmartai.app.domain.assistant.GoalProgress
import com.inventorysmartai.app.domain.assistant.InboundLine
import com.inventorysmartai.app.domain.assistant.TimeWindow
import com.inventorysmartai.app.domain.model.MovementType
import com.inventorysmartai.app.domain.repository.CatalogRepository
import com.inventorysmartai.app.domain.repository.GoalRepository
import com.inventorysmartai.app.domain.repository.ProductRepository
import com.inventorysmartai.app.domain.repository.SalesRepository
import com.inventorysmartai.app.domain.usecase.CalculateGoalAchievementUseCase
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * The smart report analyst's tools. Each one answers a whole question with exact numbers computed here (SQL-backed repositories +
 * [AssistantAnalytics]) so the model only has to explain and rank — it never adds anything up itself:
 *
 *  - `analyzeGoals`          "ما الأصناف التي لم تحقق هدف الشهر؟"      goals vs what was sold, worst first;
 *  - `compareFlows`          "قارن الوارد بالمبيعات والأهداف"           stock received vs sold vs the goal targets;
 *  - `rankBranchesByGoals`   "رتب الفروع حسب نسبة تحقيق الهدف"        each branch's contribution to the goals.
 *
 * Read-only: nothing here writes, so nothing asks for confirmation. Failures become a small `{error, message}`.
 */
@Singleton
class InsightToolExecutor @Inject constructor(
    private val goalRepository: GoalRepository,
    private val salesRepository: SalesRepository,
    private val productRepository: ProductRepository,
    private val catalogRepository: CatalogRepository,
    private val calculateGoalAchievement: CalculateGoalAchievementUseCase,
    private val movementDao: InventoryMovementDao
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
        "analyzeGoals" -> analyzeGoals(args)
        "compareFlows" -> compareFlows(args)
        "rankBranchesByGoals" -> rankBranchesByGoals(args)
        else -> mapOf("error" to "unknown_tool", "message" to "أداة غير معروفة: $name")
    }

    // ------------------------------------------------------------------ analyzeGoals

    private suspend fun analyzeGoals(args: Map<String, Any?>): Map<String, Any?> {
        val window = resolveWindow(args) ?: return badWindow()
        val onlyUnmet = ToolArgs.bool(args, "onlyUnmet") ?: false
        val limit = (ToolArgs.int(args, "limit") ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)

        val progress = loadGoalProgress(window)
        val ranked = AssistantAnalytics.rankGoals(progress, onlyUnmet)
        return mapOf(
            "period" to window.label,
            "basis" to "الإنجاز = كمية مبيعات الصنف المسجّلة داخل فترة هدفه نفسها (كما في شاشة الأهداف)",
            "goalsInPeriod" to progress.size,
            "metCount" to progress.count { it.met },
            "unmetCount" to progress.count { !it.met },
            "goals" to ranked.take(limit).map { it.toJson() },
            "truncated" to (ranked.size > limit)
        )
    }

    // ------------------------------------------------------------------ compareFlows

    private suspend fun compareFlows(args: Map<String, Any?>): Map<String, Any?> {
        val window = resolveWindow(args) ?: return badWindow()
        val groupBy = (ToolArgs.string(args, "groupBy") ?: "PRODUCT").uppercase()
        if (groupBy != "PRODUCT" && groupBy != "BRANCH") return ToolArgs.invalid("groupBy must be PRODUCT or BRANCH")
        val limit = (ToolArgs.int(args, "limit") ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
        val branchId = ToolArgs.long(args, "branchId")

        val invoices = salesRepository.observeInvoices().first()
        val inboundLines = movementDao.getByTypeBetween(MovementType.PURCHASE_IN.name, window.from, window.to)
            .map { InboundLine(productId = it.productId, branchId = it.branchId, quantity = it.quantityChange) }

        val basis = "الوارد = كميات الاستلام (حركات PURCHASE_IN) والمبيعات = فواتير البيع المؤكدة فقط، داخل الفترة"

        if (groupBy == "BRANCH") {
            val sold = AssistantAnalytics.soldByBranch(invoices, window)
            val inbound = AssistantAnalytics.inboundByBranch(inboundLines)
            val branchNames = catalogRepository.observeBranches().first().associate { it.id to it.name }
            val ids = LinkedHashSet<Long>().apply { addAll(sold.keys); addAll(inbound.keys) }
            val rows = ids.map { id ->
                val soldQty = sold[id] ?: 0.0
                val inboundQty = inbound[id] ?: 0.0
                mapOf(
                    "branchId" to id,
                    "branchName" to (branchNames[id] ?: "فرع #$id"),
                    "inbound" to inboundQty,
                    "sold" to soldQty,
                    "net" to inboundQty - soldQty
                )
            }.sortedByDescending { it["sold"] as Double }
            return mapOf(
                "period" to window.label,
                "basis" to basis,
                "groupBy" to "BRANCH",
                "totalInbound" to inbound.values.sum(),
                "totalSold" to sold.values.sum(),
                "rows" to rows.take(limit),
                "truncated" to (rows.size > limit)
            )
        }

        val sold = AssistantAnalytics.soldByProduct(invoices, window, branchId)
        val inbound = AssistantAnalytics.inboundByProduct(inboundLines, branchId)
        val goals = loadGoalProgress(window)
        val targets = AssistantAnalytics.targetsByProduct(goals)
        val rows = AssistantAnalytics.flowRows(inbound, sold, targets, productNames())
        return mapOf(
            "period" to window.label,
            "basis" to basis,
            "groupBy" to "PRODUCT",
            "branchId" to branchId,
            "goalTargetNote" to "أهداف التطبيق للشركة كلها، فلا تُقارَن مباشرة بمبيعات فرع واحد",
            "totalInbound" to inbound.values.sum(),
            "totalSold" to sold.values.sum(),
            "goalTargetTotal" to goals.sumOf { it.target },
            "rows" to rows.take(limit).map { row ->
                mapOf(
                    "productId" to row.productId,
                    "productName" to row.productName,
                    "inbound" to row.inbound,
                    "sold" to row.sold,
                    "net" to row.net,
                    "goalTarget" to row.goalTarget
                )
            },
            "truncated" to (rows.size > limit)
        )
    }

    // ------------------------------------------------------------------ rankBranchesByGoals

    private suspend fun rankBranchesByGoals(args: Map<String, Any?>): Map<String, Any?> {
        val window = resolveWindow(args) ?: return badWindow()
        val progress = loadGoalProgress(window)
        if (progress.isEmpty()) {
            return mapOf(
                "period" to window.label,
                "goalsInPeriod" to 0,
                "branches" to emptyList<Any?>(),
                "message" to "لا توجد أهداف ذات كمية مستهدفة في هذه الفترة"
            )
        }
        val branchNames = catalogRepository.observeBranches().first().associate { it.id to it.name }
        val invoices = salesRepository.observeInvoices().first()
        val rows = AssistantAnalytics.branchContributions(progress, invoices, branchNames)
        return mapOf(
            "period" to window.label,
            "basis" to "الأهداف في التطبيق للشركة كلها وليست لكل فرع؛ فنسبة الفرع هنا = ما باعه الفرع من أصناف الأهداف (داخل فترة كل هدف) ÷ إجمالي مستهدف تلك الأهداف، أي مساهمته في الهدف",
            "goalsInPeriod" to progress.size,
            "totalTarget" to progress.sumOf { it.target },
            "branches" to rows.mapIndexed { index, row ->
                mapOf(
                    "rank" to index + 1,
                    "branchId" to row.branchId,
                    "branchName" to row.branchName,
                    "soldQuantity" to row.quantity,
                    "contributionPercent" to AssistantAnalytics.round1(row.contributionPercent),
                    "shareOfSoldPercent" to AssistantAnalytics.round1(row.shareOfSoldPercent)
                )
            }
        )
    }

    // ------------------------------------------------------------------ shared

    /** Goals whose own period overlaps [window], with what each one achieved (the same calculation the Goals screen uses). */
    private suspend fun loadGoalProgress(window: TimeWindow): List<GoalProgress> {
        val goals = goalRepository.observeGoals().first()
            .filter { AssistantAnalytics.overlaps(it.periodStart, it.periodEnd, window) }
        if (goals.isEmpty()) return emptyList()
        val names = productNames()
        return goals.mapNotNull { goal ->
            val achievement = calculateGoalAchievement(goal)
            if (achievement.totalTarget <= 0.0) return@mapNotNull null // a goal with no target quantity cannot be met or missed
            GoalProgress(
                goalId = goal.id,
                productId = goal.productId,
                productName = goal.productName ?: names[goal.productId] ?: "صنف #${goal.productId}",
                target = achievement.totalTarget,
                achieved = achievement.achievedQuantity,
                periodStart = goal.periodStart,
                periodEnd = goal.periodEnd
            )
        }
    }

    private suspend fun productNames(): Map<Long, String> =
        productRepository.observeProducts().first().associate { it.id to it.name }

    /** `fromDate`/`toDate` (epoch millis) win; otherwise `month` ("2026-10"); otherwise the current month. Null = invalid input. */
    private fun resolveWindow(args: Map<String, Any?>): TimeWindow? {
        val from = ToolArgs.long(args, "fromDate")
        val to = ToolArgs.long(args, "toDate")
        return if (from != null || to != null) {
            AssistantAnalytics.explicitWindow(from, to)
        } else {
            AssistantAnalytics.monthWindow(ToolArgs.string(args, "month"), System.currentTimeMillis())
        }
    }

    private fun badWindow(): Map<String, Any?> =
        ToolArgs.invalid("month must look like 2026-10, and toDate must not be before fromDate")

    private fun GoalProgress.toJson(): Map<String, Any?> = mapOf(
        "goalId" to goalId,
        "productId" to productId,
        "productName" to productName,
        "target" to target,
        "achieved" to achieved,
        "remaining" to remaining,
        "percent" to AssistantAnalytics.round1(percent),
        "met" to met
    )

    private companion object {
        const val DEFAULT_LIMIT = 20
        const val MAX_LIMIT = 100
    }
}
