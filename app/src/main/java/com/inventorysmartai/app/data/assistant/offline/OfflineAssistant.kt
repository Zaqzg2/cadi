package com.inventorysmartai.app.data.assistant.offline

import com.inventorysmartai.app.data.ai.provider.AiJson
import com.inventorysmartai.app.data.assistant.control.AppControlToolExecutor
import com.inventorysmartai.app.data.assistant.smart.InsightToolExecutor
import com.inventorysmartai.app.data.assistant.smart.ProductMatchingToolExecutor
import com.inventorysmartai.app.data.assistant.smart.PurchasePlanningToolExecutor
import com.inventorysmartai.app.domain.assistant.AssistantCard
import com.inventorysmartai.app.domain.assistant.AssistantCardBuilder
import com.inventorysmartai.app.domain.assistant.AssistantQuickCommands
import com.inventorysmartai.app.domain.assistant.AssistantStepResult
import com.inventorysmartai.app.domain.assistant.CardAction
import com.inventorysmartai.app.domain.assistant.CardButton
import com.inventorysmartai.app.domain.assistant.OfflineCommand
import com.inventorysmartai.app.domain.assistant.OfflineCommandParser
import com.inventorysmartai.app.domain.assistant.OfflineReplyText
import com.inventorysmartai.app.domain.assistant.ToolExecutionSite
import com.inventorysmartai.app.domain.inventory.InventoryListFilter
import com.inventorysmartai.app.domain.model.InventoryStatusFilter
import com.inventorysmartai.app.navigation.Destination
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * The assistant that needs no internet. A sentence it recognises ([OfflineCommandParser]) is carried out right here, on the phone,
 * by the SAME tool executors the language model would have called — so an offline answer and an online one come from identical code
 * and identical numbers — and is answered with a short sentence plus interactive cards ([AssistantCardBuilder]).
 *
 * It is tried FIRST for every message. That is the whole point of "works without internet": a command never waits for a model, never
 * costs tokens, and never leaves the phone. Anything it does not recognise returns null and goes on to the model (when there is one).
 *
 * The two things that change data (a draft purchase request, a saved alias) keep the app's confirmation rule: they only ever come back
 * as [AssistantStepResult.ConfirmationRequired], and run when the person answers the dialog ([confirm]).
 */
@Singleton
class OfflineAssistant @Inject constructor(
    private val control: AppControlToolExecutor,
    private val insights: InsightToolExecutor,
    private val planning: PurchasePlanningToolExecutor,
    private val matching: ProductMatchingToolExecutor
) {

    private class Pending(val toolName: String, val argumentsJson: String, val action: CardAction)

    /** The one action waiting for the confirmation dialog, per conversation. */
    private val pending = ConcurrentHashMap<String, Pending>()

    /** The buttons of the last purchase proposal, so "جهّز المسودة" can follow "جهّز أمر شراء للنواقص". */
    private val lastProposal = ConcurrentHashMap<String, List<CardButton>>()

    /** The answer to [text] when it is a command this engine understands; null when it is not (the model gets it instead). */
    suspend fun tryHandle(conversationId: String, text: String): AssistantStepResult? {
        val command = OfflineCommandParser.parse(text) ?: return null
        return try {
            dispatch(conversationId, command)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure()
        }
    }

    fun hasPending(conversationId: String): Boolean = pending.containsKey(conversationId)

    fun clearPending(conversationId: String) {
        pending.remove(conversationId)
    }

    /** What to say when a message needs the language model and there is none (no connection, or no key set): the list of what DOES work. */
    fun offlineHelp(intro: String): AssistantStepResult.Final = reply(
        text = intro,
        cards = listOf(
            AssistantCard.Choices(
                title = "جرّب أحد هذه الأوامر",
                subtitle = null,
                buttons = AssistantQuickCommands.all.map { CardButton(it.label, CardAction.SendText(it.text)) }
            )
        ),
        usedLocalData = false
    )

    // ------------------------------------------------------------------ the commands

    private suspend fun dispatch(conversationId: String, command: OfflineCommand): AssistantStepResult = when (command) {
        is OfflineCommand.OpenScreen -> openScreen(command)
        is OfflineCommand.ShowInventory -> showInventory(command)
        is OfflineCommand.Goals -> goals(command)
        is OfflineCommand.Flows -> flows(command)
        is OfflineCommand.RankBranches -> ranking(command)
        is OfflineCommand.SuggestPurchase -> suggestPurchase(conversationId, command)
        is OfflineCommand.DraftFromSuggestion -> draftFromSuggestion(conversationId)
        is OfflineCommand.MatchNames -> matchNames(command)
        is OfflineCommand.Help -> offlineHelp("أنفّذ هذه الأوامر مباشرة على بيانات التطبيق، وتعمل بدون إنترنت:")
    }

    private suspend fun openScreen(command: OfflineCommand.OpenScreen): AssistantStepResult {
        val result = callControl("openScreen", mapOf("screen" to command.screenKey))
        val label = result["label"] as? String
        if (result["opened"] != true || label == null) return failure("تعذّر فتح هذه الشاشة.")
        return reply("فتحتُ «$label».")
    }

    private suspend fun showInventory(command: OfflineCommand.ShowInventory): AssistantStepResult {
        val args = buildMap<String, Any?> {
            command.status?.let { put("status", it) }
            command.below?.let { put("quantityBelow", it) }
            command.above?.let { put("quantityAbove", it) }
            if (command.inclusive) put("inclusive", true)
            command.search?.let { put("search", it) }
            command.branch?.let { put("branch", it) }
            command.category?.let { put("category", it) }
            put("open", command.openScreen)
        }
        val result = callControl("showInventory", args)
        val error = result["error"] as? String
        if (error != null) {
            // an unknown or ambiguous branch / category: let the person tap the right one
            return if (error.endsWith("branch") || error.endsWith("category")) inventoryChoices(command, result, error) else failure()
        }
        return reply(
            text = OfflineReplyText.inventory(result, opened = command.openScreen),
            cards = AssistantCardBuilder.fromToolResult("showInventory", result)
        )
    }

    private fun inventoryChoices(command: OfflineCommand.ShowInventory, result: Map<String, Any?>, error: String): AssistantStepResult {
        val isBranch = error.endsWith("branch")
        val noun = if (isBranch) "الفرع" else "التصنيف"
        val options = (result["options"] as? List<*>).orEmpty()
            .mapNotNull { it as? Map<*, *> }
            .mapNotNull { option ->
                val id = (option["id"] as? Number)?.toLong() ?: return@mapNotNull null
                val name = option["name"] as? String ?: return@mapNotNull null
                id to name
            }
            .take(MAX_CHOICES)
        if (options.isEmpty()) return failure("لم أجد $noun المطلوب.")

        val base = InventoryListFilter(
            searchQuery = command.search.orEmpty(),
            status = InventoryStatusFilter.entries.firstOrNull { it.name == command.status } ?: InventoryStatusFilter.ALL,
            quantityBelow = command.below,
            quantityAbove = command.above,
            inclusiveBounds = command.inclusive
        )
        val buttons = options.map { (id, name) ->
            val filter = if (isBranch) base.copy(branchId = id) else base.copy(categoryId = id)
            CardButton(name, CardAction.OpenRoute(Destination.InventoryFiltered.createRoute(filter)))
        }
        val text = if (error.startsWith("ambiguous")) "يوجد أكثر من $noun بهذا الاسم — أيّها تقصد؟" else "لم أجد $noun بهذا الاسم. اختر من القائمة:"
        return reply(text, listOf(AssistantCard.Choices(title = "اختر $noun", subtitle = null, buttons = buttons)), usedLocalData = false)
    }

    private suspend fun goals(command: OfflineCommand.Goals): AssistantStepResult {
        val args = buildMap<String, Any?> {
            command.month?.let { put("month", it) }
            if (command.onlyUnmet) put("onlyUnmet", true)
            put("limit", GOAL_ROWS)
        }
        val result = callInsight("analyzeGoals", args)
        if (result.containsKey("error")) return failure()
        return reply(OfflineReplyText.goalsAnalysis(result, command.onlyUnmet), AssistantCardBuilder.fromToolResult("analyzeGoals", result))
    }

    private suspend fun flows(command: OfflineCommand.Flows): AssistantStepResult {
        val args = buildMap<String, Any?> {
            command.month?.let { put("month", it) }
            put("groupBy", if (command.byBranch) "BRANCH" else "PRODUCT")
            put("limit", FLOW_ROWS)
        }
        val result = callInsight("compareFlows", args)
        if (result.containsKey("error")) return failure()
        return reply(OfflineReplyText.flows(result), AssistantCardBuilder.fromToolResult("compareFlows", result))
    }

    private suspend fun ranking(command: OfflineCommand.RankBranches): AssistantStepResult {
        val args = buildMap<String, Any?> { command.month?.let { put("month", it) } }
        val result = callInsight("rankBranchesByGoals", args)
        if (result.containsKey("error")) return failure()
        return reply(OfflineReplyText.ranking(result), AssistantCardBuilder.fromToolResult("rankBranchesByGoals", result))
    }

    private suspend fun suggestPurchase(conversationId: String, command: OfflineCommand.SuggestPurchase): AssistantStepResult {
        val args = buildMap<String, Any?> {
            command.targetFactor?.let { put("targetFactor", it) }
            put("limit", PLAN_LINES)
        }
        val result = callPlanning("suggestPurchaseOrder", args)
        if (result.containsKey("error")) return failure()
        val cards = AssistantCardBuilder.fromToolResult("suggestPurchaseOrder", result)
        val buttons = cards.filterIsInstance<AssistantCard.PurchasePlan>().firstOrNull()?.buttons.orEmpty()
        if (buttons.isEmpty()) lastProposal.remove(conversationId) else lastProposal[conversationId] = buttons
        return reply(OfflineReplyText.proposal(result), cards)
    }

    private suspend fun draftFromSuggestion(conversationId: String): AssistantStepResult {
        val buttons = lastProposal[conversationId].orEmpty()
        return when {
            buttons.isEmpty() -> reply("لا يوجد اقتراح حاليًا. اكتب أولًا: «جهّز أمر شراء للنواقص».", usedLocalData = false)
            buttons.size == 1 -> requestAction(conversationId, buttons[0].action)
            else -> reply("اختر الفرع الذي سيستلم الطلب:", listOf(AssistantCard.Choices("اختر الفرع", null, buttons)), usedLocalData = false)
        }
    }

    private suspend fun matchNames(command: OfflineCommand.MatchNames): AssistantStepResult {
        if (command.names.isEmpty()) return reply(MATCH_HELP, usedLocalData = false)
        val result = callMatching("matchProductNames", mapOf("items" to command.names.map { mapOf("name" to it) }))
        if (result.containsKey("error")) return failure()
        return reply(OfflineReplyText.matches(result), AssistantCardBuilder.fromToolResult("matchProductNames", result))
    }

    // ------------------------------------------------------------------ actions that change data (always confirmed first)

    /** A card button that writes: stores it and asks for the same confirmation dialog the model's write tools use. */
    suspend fun requestAction(conversationId: String, action: CardAction): AssistantStepResult = when (action) {
        is CardAction.DraftPurchase -> {
            if (action.lines.isEmpty()) {
                failure("لا توجد كميات لحفظها في المسودة.")
            } else {
                val json = AiJson.toJson(
                    mapOf(
                        "branchId" to action.branchId,
                        "items" to action.lines.map { mapOf("productId" to it.productId, "quantity" to it.quantity) }
                    )
                )
                askToConfirm(conversationId, "draftPurchaseOrder", json, action, planning.describeForConfirmation("draftPurchaseOrder", json))
            }
        }
        is CardAction.SaveAlias -> {
            val json = AiJson.toJson(mapOf("productId" to action.productId, "alias" to action.alias))
            askToConfirm(conversationId, "addProductAlias", json, action, matching.describeForConfirmation("addProductAlias", json))
        }
        // opening a screen and sending text never change data; the screen handles them without coming here
        is CardAction.OpenRoute, is CardAction.SendText -> failure()
    }

    private fun askToConfirm(conversationId: String, tool: String, json: String, action: CardAction, description: String): AssistantStepResult {
        pending[conversationId] = Pending(tool, json, action)
        return AssistantStepResult.ConfirmationRequired(
            callId = UUID.randomUUID().toString(),
            toolName = tool,
            site = ToolExecutionSite.LOCAL,
            actionDescriptionAr = description
        )
    }

    /** The person answered the dialog for the action stored by [requestAction]. */
    suspend fun confirm(conversationId: String, approved: Boolean): AssistantStepResult {
        val action = pending.remove(conversationId) ?: return AssistantStepResult.Error("لا يوجد إجراء بانتظار التأكيد.")
        if (!approved) return reply("تم إلغاء الإجراء — لم يتغيّر شيء.", usedLocalData = false)
        return try {
            when (action.toolName) {
                "draftPurchaseOrder" -> afterDraft(action)
                "addProductAlias" -> afterAlias(action)
                else -> failure()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure()
        }
    }

    private suspend fun afterDraft(action: Pending): AssistantStepResult {
        val result = AiJson.parseObject(planning.execute("draftPurchaseOrder", action.argumentsJson)).orEmpty()
        if (result["created"] == true) {
            val number = result["requestNumber"] as? String ?: ""
            val branch = result["branch"] as? String ?: ""
            val count = (result["itemCount"] as? Number)?.toInt() ?: 0
            return reply("حُفظت مسودة طلب الشراء $number لفرع «$branch» وفيها ${OfflineReplyText.items(count)}، وفُتحت للمراجعة. لن تُرسل حتى تضغط «إرسال الطلب» بنفسك.")
        }
        val original = action.action as? CardAction.DraftPurchase
        if (result["error"] == "branch_required" && original != null) {
            val buttons = (result["branches"] as? List<*>).orEmpty()
                .mapNotNull { it as? Map<*, *> }
                .take(MAX_CHOICES)
                .mapNotNull { branch ->
                    val id = (branch["id"] as? Number)?.toLong() ?: return@mapNotNull null
                    val name = branch["name"] as? String ?: return@mapNotNull null
                    CardButton("لفرع $name", CardAction.DraftPurchase(id, original.lines))
                }
            if (buttons.isNotEmpty()) {
                return reply("حدّد الفرع الذي سيستلم الطلب:", listOf(AssistantCard.Choices("اختر الفرع", null, buttons)), usedLocalData = false)
            }
        }
        return failure()
    }

    private suspend fun afterAlias(action: Pending): AssistantStepResult {
        val result = AiJson.parseObject(matching.execute("addProductAlias", action.argumentsJson)).orEmpty()
        if (result["saved"] == true) {
            val alias = result["alias"] as? String ?: ""
            val product = result["productName"] as? String ?: ""
            return reply("حفظتُ «$alias» اسمًا بديلًا للصنف «$product» — سأتعرّف عليه تلقائيًا في المرات القادمة.")
        }
        return failure((result["message"] as? String)?.takeIf { result["reason"] != null })
    }

    // ------------------------------------------------------------------ plumbing

    private suspend fun callControl(tool: String, args: Map<String, Any?>): Map<String, Any?> =
        AiJson.parseObject(control.execute(tool, AiJson.toJson(args))).orEmpty()

    private suspend fun callInsight(tool: String, args: Map<String, Any?>): Map<String, Any?> =
        AiJson.parseObject(insights.execute(tool, AiJson.toJson(args))).orEmpty()

    private suspend fun callPlanning(tool: String, args: Map<String, Any?>): Map<String, Any?> =
        AiJson.parseObject(planning.execute(tool, AiJson.toJson(args))).orEmpty()

    private suspend fun callMatching(tool: String, args: Map<String, Any?>): Map<String, Any?> =
        AiJson.parseObject(matching.execute(tool, AiJson.toJson(args))).orEmpty()

    private fun reply(text: String, cards: List<AssistantCard> = emptyList(), usedLocalData: Boolean = true): AssistantStepResult.Final =
        AssistantStepResult.Final(text = text, usedLocalData = usedLocalData, cards = cards)

    private fun failure(message: String? = null): AssistantStepResult.Final =
        reply(message ?: "تعذّر تنفيذ الأمر الآن. حاول مرة أخرى.", usedLocalData = false)

    private companion object {
        const val MAX_CHOICES = 6
        const val GOAL_ROWS = 8
        const val FLOW_ROWS = 6
        const val PLAN_LINES = 100
        const val MATCH_HELP = "اكتب الأسماء بعد كلمة «طابق» — اسم في كل سطر أو افصل بينها بفاصلة.\nمثال: طابق: صابون سائل 500 مل، منظف أرضيات"
    }
}
