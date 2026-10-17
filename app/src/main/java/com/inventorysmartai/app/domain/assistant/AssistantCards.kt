package com.inventorysmartai.app.domain.assistant

/**
 * Colour is meaning in this app (red = a problem, green = fine...), so a card says WHAT something is and the screen decides the
 * actual colour once, from the theme. A card never carries a colour value.
 */
enum class Tone { NEUTRAL, GOOD, WARNING, DANGER, INFO }

/** What tapping a card button does. */
sealed interface CardAction {
    /** Open a screen (a navigation route), e.g. the inventory list with the same filters. */
    data class OpenRoute(val route: String) : CardAction

    /** Send this text as if the person had typed it — a follow-up or a quick command. */
    data class SendText(val text: String) : CardAction

    /** Save these lines as a DRAFT purchase request. Changes data, so the person is asked to confirm first. */
    data class DraftPurchase(val branchId: Long?, val lines: List<PurchaseLine>) : CardAction

    /** Remember [alias] as another name of the product. Changes data, so the person is asked to confirm first. */
    data class SaveAlias(val productId: Long, val productName: String, val alias: String) : CardAction
}

data class PurchaseLine(val productId: Long, val name: String, val quantity: Double)

data class CardButton(val label: String, val action: CardAction, val primary: Boolean = false)

/** One of the small summary boxes at the top of a card ("منخفض — 28 صنف"). */
data class Tile(val label: String, val value: String, val tone: Tone)

data class ItemRow(val name: String, val quantity: String, val statusLabel: String, val tone: Tone)

data class GoalRowCard(val name: String, val percent: Double, val detail: String, val tone: Tone)

data class RankRow(val rank: Int, val name: String, val valueText: String, val fraction: Float, val tone: Tone)

/** One group of bars: what came in, what was sold, and (when the product has one) the goal's target. */
data class FlowBar(val name: String, val inbound: Double, val sold: Double, val goalTarget: Double?)

data class PlanRow(val name: String, val current: String, val suggested: String?, val note: String?)

/** One written name and what the matcher thinks it is; [choices] are the buttons that confirm a candidate. */
data class MatchRowCard(
    val input: String,
    val confidenceLabel: String,
    val tone: Tone,
    val matchedName: String?,
    val detail: String?,
    val choices: List<CardButton>
)

/**
 * The structured part of an assistant answer: a table, progress bars, a comparison, a purchase proposal... The text of the answer
 * stays a short sentence; the numbers live here, drawn as the interactive cards of the design.
 */
sealed interface AssistantCard {
    val title: String

    data class ItemList(
        override val title: String,
        val subtitle: String?,
        val rows: List<ItemRow>,
        val totalCount: Int,
        val viewAll: CardButton?
    ) : AssistantCard

    data class Goals(
        override val title: String,
        val subtitle: String?,
        val tiles: List<Tile>,
        val rows: List<GoalRowCard>
    ) : AssistantCard

    data class Ranking(
        override val title: String,
        val subtitle: String?,
        val rows: List<RankRow>
    ) : AssistantCard

    data class Flows(
        override val title: String,
        val subtitle: String?,
        val tiles: List<Tile>,
        val rows: List<FlowBar>
    ) : AssistantCard

    data class PurchasePlan(
        override val title: String,
        val subtitle: String?,
        val tiles: List<Tile>,
        val rows: List<PlanRow>,
        val hiddenCount: Int,
        val buttons: List<CardButton>
    ) : AssistantCard

    data class NameMatches(
        override val title: String,
        val subtitle: String?,
        val rows: List<MatchRowCard>
    ) : AssistantCard

    /** A question with answers to tap: an ambiguous branch name, or "what can you do". */
    data class Choices(
        override val title: String,
        val subtitle: String?,
        val buttons: List<CardButton>
    ) : AssistantCard
}
