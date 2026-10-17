package com.inventorysmartai.app.domain.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantCardBuilderTest {

    private fun build(tool: String, result: Map<String, Any?>) = AssistantCardBuilder.fromToolResult(tool, result)

    // ------------------------------------------------------------------ inventory list

    @Test
    fun `an inventory result becomes a table with a button to open the whole list`() {
        val result = mapOf(
            "matchingCount" to 12.0,
            "route" to "inventory_view?status=LOW&below=10",
            "appliedFilters" to mapOf("status" to "LOW", "quantity" to "الرصيد أقل من 10", "branchName" to "الفرع الأول"),
            "shown" to listOf(
                mapOf("productId" to 1.0, "name" to "صابون سائل", "quantity" to 3.0, "status" to "LOW"),
                mapOf("productId" to 2.0, "name" to "منظف زجاج", "quantity" to 12.5, "status" to "AVAILABLE")
            )
        )
        val card = build("showInventory", result).single() as AssistantCard.ItemList
        assertEquals("الأصناف المطابقة", card.title)
        assertEquals("الحالة: منخفض · الرصيد أقل من 10 · الفرع: الفرع الأول", card.subtitle)
        assertEquals(12, card.totalCount)
        assertEquals(2, card.rows.size)
        assertEquals(ItemRow("صابون سائل", "3", "منخفض", Tone.DANGER), card.rows[0])
        assertEquals(ItemRow("منظف زجاج", "12.5", "متوفر", Tone.GOOD), card.rows[1])
        val viewAll = card.viewAll!!
        assertEquals("عرض الكل (12)", viewAll.label)
        assertTrue(viewAll.primary)
        assertEquals(CardAction.OpenRoute("inventory_view?status=LOW&below=10"), viewAll.action)
    }

    @Test
    fun `no matches gives an empty table and no button`() {
        val card = build("showInventory", mapOf("matchingCount" to 0.0, "route" to "inventory_view", "shown" to emptyList<Any>())).single() as AssistantCard.ItemList
        assertEquals("لا توجد أصناف مطابقة", card.title)
        assertTrue(card.rows.isEmpty())
        assertNull(card.viewAll)
    }

    @Test
    fun `a long list is cut to eight rows but still says how many there are`() {
        val shown = (1..20).map { mapOf("productId" to it.toDouble(), "name" to "صنف $it", "quantity" to 1.0, "status" to "LOW") }
        val card = build("showInventory", mapOf("matchingCount" to 20.0, "route" to "inventory_view", "shown" to shown)).single() as AssistantCard.ItemList
        assertEquals(8, card.rows.size)
        assertEquals(20, card.totalCount)
        assertEquals("عرض الكل (20)", card.viewAll!!.label)
    }

    // ------------------------------------------------------------------ goals

    @Test
    fun `goals are coloured by how close they are`() {
        val result = mapOf(
            "period" to "2026-10",
            "goalsInPeriod" to 3.0,
            "metCount" to 1.0,
            "unmetCount" to 2.0,
            "goals" to listOf(
                mapOf("productName" to "أ", "target" to 100.0, "achieved" to 64.0, "percent" to 64.0),
                mapOf("productName" to "ب", "target" to 100.0, "achieved" to 85.0, "percent" to 85.0),
                mapOf("productName" to "ج", "target" to 100.0, "achieved" to 120.0, "percent" to 120.0)
            )
        )
        val card = build("analyzeGoals", result).single() as AssistantCard.Goals
        assertEquals("الفترة: 2026-10", card.subtitle)
        assertEquals(listOf("1", "2", "3"), card.tiles.map { it.value })
        assertEquals(listOf(Tone.DANGER, Tone.WARNING, Tone.GOOD), card.rows.map { it.tone })
        assertEquals("64 من 100", card.rows[0].detail)
    }

    @Test
    fun `no goals gives no card`() {
        assertTrue(build("analyzeGoals", mapOf("goalsInPeriod" to 0.0, "goals" to emptyList<Any>())).isEmpty())
    }

    // ------------------------------------------------------------------ branches

    @Test
    fun `branches are ranked and their bars are relative to the best`() {
        val result = mapOf(
            "branches" to listOf(
                mapOf("rank" to 1.0, "branchName" to "الأول", "soldQuantity" to 120.0, "contributionPercent" to 40.0),
                mapOf("rank" to 2.0, "branchName" to "الثاني", "soldQuantity" to 60.0, "contributionPercent" to 20.0),
                mapOf("rank" to 3.0, "branchName" to "الثالث", "soldQuantity" to 0.0, "contributionPercent" to 0.0)
            )
        )
        val card = build("rankBranchesByGoals", result).single() as AssistantCard.Ranking
        assertEquals(listOf(1f, 0.5f, 0f), card.rows.map { it.fraction })
        assertEquals("40% · 120 وحدة", card.rows[0].valueText)
        assertEquals(Tone.GOOD, card.rows[0].tone)
        assertEquals(Tone.INFO, card.rows[1].tone)
    }

    @Test
    fun `no branches gives no card`() {
        assertTrue(build("rankBranchesByGoals", mapOf("branches" to emptyList<Any>())).isEmpty())
    }

    // ------------------------------------------------------------------ inbound vs sales

    @Test
    fun `inbound against sales per product`() {
        val result = mapOf(
            "period" to "2026-10",
            "groupBy" to "PRODUCT",
            "totalInbound" to 50.0,
            "totalSold" to 20.0,
            "goalTargetTotal" to 100.0,
            "rows" to listOf(
                mapOf("productName" to "أ", "inbound" to 50.0, "sold" to 20.0, "goalTarget" to 100.0),
                mapOf("productName" to "ب", "inbound" to 0.0, "sold" to 40.0)
            )
        )
        val card = build("compareFlows", result).single() as AssistantCard.Flows
        assertEquals("الوارد مقابل المبيعات", card.title)
        assertEquals(listOf("الوارد", "المبيعات", "مستهدف الأهداف"), card.tiles.map { it.label })
        assertEquals(FlowBar("أ", 50.0, 20.0, 100.0), card.rows[0])
        assertNull(card.rows[1].goalTarget)
    }

    @Test
    fun `inbound against sales per branch uses the branch name`() {
        val result = mapOf(
            "groupBy" to "BRANCH",
            "totalInbound" to 10.0,
            "totalSold" to 4.0,
            "rows" to listOf(mapOf("branchName" to "الأول", "inbound" to 10.0, "sold" to 4.0))
        )
        val card = build("compareFlows", result).single() as AssistantCard.Flows
        assertEquals("الوارد والمبيعات لكل فرع", card.title)
        assertEquals("الأول", card.rows[0].name)
        // no goal target in the totals: no third tile
        assertEquals(2, card.tiles.size)
    }

    // ------------------------------------------------------------------ purchase proposal

    private fun proposal(branches: List<Map<String, Any?>>, itemCount: Int = 2): Map<String, Any?> {
        val items = (1..itemCount).map { index ->
            if (index == 2) {
                mapOf("productId" to 2.0, "name" to "ب", "currentQuantity" to 0.0, "needsManualQuantity" to true)
            } else {
                mapOf("productId" to index.toDouble(), "name" to "صنف $index", "currentQuantity" to 2.0, "suggestedQuantity" to 18.0)
            }
        }
        return mapOf("shortCount" to itemCount.toDouble(), "rule" to "القاعدة", "items" to items, "branches" to branches)
    }

    @Test
    fun `a purchase proposal with one branch has one button that drafts it`() {
        val card = build("suggestPurchaseOrder", proposal(listOf(mapOf("id" to 1.0, "name" to "الرئيسي")))).single() as AssistantCard.PurchasePlan
        assertEquals("القاعدة", card.subtitle)
        assertEquals(listOf("2", "18"), card.tiles.map { it.value })
        assertEquals("18", card.rows[0].suggested)
        assertNull(card.rows[1].suggested) // no thresholds: the person decides
        val button = card.buttons.single()
        assertEquals("تجهيز المسودة للمراجعة", button.label)
        assertTrue(button.primary)
        // only the line that has a quantity goes into the draft
        assertEquals(CardAction.DraftPurchase(1L, listOf(PurchaseLine(1L, "صنف 1", 18.0))), button.action)
    }

    @Test
    fun `with several branches there is a button for each`() {
        val branches = listOf(mapOf("id" to 1.0, "name" to "الأول"), mapOf("id" to 2.0, "name" to "الثاني"))
        val card = build("suggestPurchaseOrder", proposal(branches)).single() as AssistantCard.PurchasePlan
        assertEquals(listOf("مسودة لفرع الأول", "مسودة لفرع الثاني"), card.buttons.map { it.label })
        assertEquals(listOf(true, false), card.buttons.map { it.primary })
        assertEquals(2L, (card.buttons[1].action as CardAction.DraftPurchase).branchId)
    }

    @Test
    fun `with no branch known the draft asks for one later`() {
        val card = build("suggestPurchaseOrder", proposal(emptyList())).single() as AssistantCard.PurchasePlan
        assertNull((card.buttons.single().action as CardAction.DraftPurchase).branchId)
    }

    @Test
    fun `rows beyond eight are counted, not drawn`() {
        val items = (1..10).map { mapOf("productId" to it.toDouble(), "name" to "صنف $it", "currentQuantity" to 1.0, "suggestedQuantity" to 5.0) }
        val result = mapOf("shortCount" to 10.0, "items" to items, "branches" to emptyList<Any>())
        val card = build("suggestPurchaseOrder", result).single() as AssistantCard.PurchasePlan
        assertEquals(8, card.rows.size)
        assertEquals(2, card.hiddenCount)
        // ...but all ten go into the draft
        assertEquals(10, (card.buttons.single().action as CardAction.DraftPurchase).lines.size)
    }

    @Test
    fun `nothing short gives no card`() {
        assertTrue(build("suggestPurchaseOrder", mapOf("shortCount" to 0.0, "items" to emptyList<Any>())).isEmpty())
    }

    // ------------------------------------------------------------------ name matching

    @Test
    fun `an uncertain match offers the candidates as buttons that save the alias`() {
        val result = mapOf(
            "summary" to mapOf("total" to 3.0, "exact" to 1.0, "needReview" to 1.0, "noMatch" to 1.0),
            "results" to listOf(
                mapOf(
                    "input" to "صابون سايل",
                    "confidence" to "HIGH",
                    "match" to mapOf("productId" to 1.0, "productName" to "صابون سائل", "score" to 0.93),
                    "alternatives" to listOf(mapOf("productId" to 2.0, "productName" to "صابون ليمون")),
                    "reasons" to emptyList<Any>()
                ),
                mapOf("input" to "xyz", "confidence" to "NONE", "alternatives" to emptyList<Any>()),
                mapOf(
                    "input" to "عطر جو",
                    "confidence" to "EXACT",
                    "match" to mapOf("productId" to 6.0, "productName" to "عطر جو", "score" to 1.0),
                    "alternatives" to emptyList<Any>()
                )
            )
        )
        val card = build("matchProductNames", result).single() as AssistantCard.NameMatches
        assertEquals("1 مطابق تمامًا · 1 يحتاج مراجعتك · 1 بلا تطابق", card.subtitle)
        assertEquals(3, card.rows.size)

        val uncertain = card.rows[0]
        assertEquals(Tone.INFO, uncertain.tone)
        assertEquals("صابون سائل", uncertain.matchedName)
        assertEquals(listOf("صابون سائل", "صابون ليمون"), uncertain.choices.map { it.label })
        assertEquals(CardAction.SaveAlias(1L, "صابون سائل", "صابون سايل"), uncertain.choices[0].action)
        assertTrue(uncertain.choices[0].primary)
        assertFalse(uncertain.choices[1].primary)

        val none = card.rows[1]
        assertEquals(Tone.DANGER, none.tone)
        assertNull(none.matchedName)
        assertTrue(none.choices.isEmpty())

        val exact = card.rows[2]
        assertEquals(Tone.GOOD, exact.tone)
        assertEquals("عطر جو", exact.matchedName)
        assertTrue("an exact match needs nothing from the person", exact.choices.isEmpty())
    }

    // ------------------------------------------------------------------ the rest

    @Test
    fun `an error or an unknown tool gives no card`() {
        assertTrue(build("showInventory", mapOf("error" to "unknown_branch", "message" to "x")).isEmpty())
        assertTrue(build("getLowStock", mapOf("items" to emptyList<Any>())).isEmpty())
        assertTrue(build("openScreen", mapOf("opened" to true)).isEmpty())
    }

    @Test
    fun `status words and tones`() {
        assertEquals("متوفر", AssistantCardBuilder.statusLabel("AVAILABLE"))
        assertEquals("منخفض", AssistantCardBuilder.statusLabel("LOW"))
        assertEquals("صفر", AssistantCardBuilder.statusLabel("ZERO"))
        assertEquals("قريب الانتهاء", AssistantCardBuilder.statusLabel("NEAR_EXPIRY"))
        assertEquals("منتهي", AssistantCardBuilder.statusLabel("EXPIRED"))
        assertEquals(Tone.DANGER, AssistantCardBuilder.statusTone("LOW"))
        assertEquals(Tone.WARNING, AssistantCardBuilder.statusTone("NEAR_EXPIRY"))
        assertEquals(Tone.GOOD, AssistantCardBuilder.statusTone("AVAILABLE"))
        assertNotNull(AssistantCardBuilder.statusLabel(null))
    }

    @Test
    fun `a goal is green from one hundred, amber from seventy, red below`() {
        assertEquals(Tone.GOOD, AssistantCardBuilder.percentTone(100.0))
        assertEquals(Tone.WARNING, AssistantCardBuilder.percentTone(99.9))
        assertEquals(Tone.WARNING, AssistantCardBuilder.percentTone(70.0))
        assertEquals(Tone.DANGER, AssistantCardBuilder.percentTone(69.9))
    }

    @Test
    fun `numbers are written without a trailing zero`() {
        assertEquals("10", AssistantCardBuilder.formatNumber(10.0))
        assertEquals("2.5", AssistantCardBuilder.formatNumber(2.5))
        assertEquals("33.3", AssistantCardBuilder.formatNumber(33.333))
        assertEquals("64%", AssistantCardBuilder.formatPercent(64.0))
        assertEquals("—", AssistantCardBuilder.formatNumber(Double.NaN))
    }
}
