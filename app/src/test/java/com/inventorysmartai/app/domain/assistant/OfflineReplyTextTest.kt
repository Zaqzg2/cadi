package com.inventorysmartai.app.domain.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineReplyTextTest {

    @Test
    fun `counts agree with the noun`() {
        assertEquals("صنف واحد", OfflineReplyText.items(1))
        assertEquals("صنفان", OfflineReplyText.items(2))
        assertEquals("5 أصناف", OfflineReplyText.items(5))
        assertEquals("10 أصناف", OfflineReplyText.items(10))
        assertEquals("11 صنفًا", OfflineReplyText.items(11))
        assertEquals("هدف واحد", OfflineReplyText.goals(1))
        assertEquals("هدفان", OfflineReplyText.goals(2))
        assertEquals("4 أهداف", OfflineReplyText.goals(4))
        assertEquals("12 هدفًا", OfflineReplyText.goals(12))
    }

    @Test
    fun `inventory answers`() {
        assertEquals("لا توجد أصناف مطابقة لهذا الشرط.", OfflineReplyText.inventory(mapOf("matchingCount" to 0.0), opened = false))
        assertEquals("فتحتُ قائمة الأصناف بعد تطبيق الفلتر: 7 أصناف.", OfflineReplyText.inventory(mapOf("matchingCount" to 7.0), opened = true))
        assertEquals("وجدتُ 3 أصناف.", OfflineReplyText.inventory(mapOf("matchingCount" to 3.0), opened = false))
        val many = OfflineReplyText.inventory(mapOf("matchingCount" to 20.0), opened = false)
        assertTrue(many, many.startsWith("وجدتُ 20 صنفًا") && many.contains("عرض الكل"))
    }

    @Test
    fun `goal answers`() {
        val result = mapOf("period" to "2026-10", "goalsInPeriod" to 4.0, "metCount" to 1.0, "unmetCount" to 3.0)
        assertEquals("لم يحقق 3 من أصل 4 أهداف في 2026-10 — الأبعد عن هدفه أولًا.", OfflineReplyText.goalsAnalysis(result, onlyUnmet = true))
        assertEquals("في 2026-10: حقق 1 من أصل 4 أهداف.", OfflineReplyText.goalsAnalysis(result, onlyUnmet = false))
        val allMet = mapOf("period" to "2026-10", "goalsInPeriod" to 2.0, "metCount" to 2.0, "unmetCount" to 0.0)
        assertEquals("كل الأهداف (هدفان) محققة في 2026-10.", OfflineReplyText.goalsAnalysis(allMet, onlyUnmet = true))
        val none = mapOf("period" to "2026-10", "goalsInPeriod" to 0.0)
        assertEquals("لا توجد أهداف ذات كمية مستهدفة في 2026-10.", OfflineReplyText.goalsAnalysis(none, onlyUnmet = false))
    }

    @Test
    fun `inbound against sales answers`() {
        assertEquals(
            "في 2026-10: الوارد 50 مقابل مبيعات 20. تراكم 30 في المخزون.",
            OfflineReplyText.flows(mapOf("period" to "2026-10", "totalInbound" to 50.0, "totalSold" to 20.0))
        )
        assertEquals(
            "في 2026-10: الوارد 5 مقابل مبيعات 20. خرج 15 أكثر مما دخل.",
            OfflineReplyText.flows(mapOf("period" to "2026-10", "totalInbound" to 5.0, "totalSold" to 20.0))
        )
        assertEquals(
            "لا توجد حركة وارد ولا مبيعات مؤكدة في 2026-10.",
            OfflineReplyText.flows(mapOf("period" to "2026-10", "totalInbound" to 0.0, "totalSold" to 0.0))
        )
    }

    @Test
    fun `branch ranking answers`() {
        val ranked = mapOf("branches" to listOf(mapOf("branchName" to "الأول", "contributionPercent" to 40.0)))
        val text = OfflineReplyText.ranking(ranked)
        assertTrue(text, text.startsWith("الأعلى مساهمة في الأهداف: الأول (40%)"))
        assertTrue(text.contains("للشركة كلها"))
        assertEquals("لا توجد أهداف ذات كمية مستهدفة في هذه الفترة", OfflineReplyText.ranking(mapOf("branches" to emptyList<Any>(), "message" to "لا توجد أهداف ذات كمية مستهدفة في هذه الفترة")))
    }

    @Test
    fun `purchase proposal answers`() {
        assertEquals("لا توجد أصناف ناقصة الآن: كل الأصناف فوق حدّها الأدنى.", OfflineReplyText.proposal(mapOf("shortCount" to 0.0)))
        val text = OfflineReplyText.proposal(mapOf("shortCount" to 5.0))
        assertTrue(text, text.contains("عدد الأصناف الناقصة: 5") && text.contains("قبل موافقتك"))
    }

    @Test
    fun `name matching answers`() {
        val summary = mapOf("total" to 3.0, "exact" to 1.0, "needReview" to 1.0, "noMatch" to 1.0)
        assertEquals(
            "طابقتُ 3 أسماء: 1 مطابق تمامًا، 1 يحتاج مراجعتك، 1 بلا تطابق. اضغط على اسم الصنف الصحيح لحفظه اسمًا بديلًا.",
            OfflineReplyText.matches(mapOf("summary" to summary))
        )
        val one = mapOf("total" to 1.0, "exact" to 1.0, "needReview" to 0.0, "noMatch" to 0.0)
        assertEquals("طابقتُ 1 اسمًا: 1 مطابق تمامًا، 0 يحتاج مراجعتك، 0 بلا تطابق.", OfflineReplyText.matches(mapOf("summary" to one)))
    }
}
