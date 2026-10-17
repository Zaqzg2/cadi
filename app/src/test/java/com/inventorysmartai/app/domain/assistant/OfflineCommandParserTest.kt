package com.inventorysmartai.app.domain.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class OfflineCommandParserTest {

    private val zone = ZoneOffset.UTC
    private val october = LocalDate.of(2026, 10, 15).atStartOfDay(zone).toInstant().toEpochMilli()
    private val january = LocalDate.of(2026, 1, 10).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun parse(text: String, now: Long = october): OfflineCommand? = OfflineCommandParser.parse(text, now, zone)

    private fun inventory(
        status: String? = null,
        below: Double? = null,
        above: Double? = null,
        inclusive: Boolean = false,
        search: String? = null,
        branch: String? = null,
        category: String? = null,
        openScreen: Boolean = false
    ) = OfflineCommand.ShowInventory(status, below, above, inclusive, search, branch, category, openScreen)

    // ------------------------------------------------------------------ control the app

    @Test
    fun `open a screen by name`() {
        assertEquals(OfflineCommand.OpenScreen("INVENTORY"), parse("افتح صفحة الأصناف"))
        assertEquals(OfflineCommand.OpenScreen("PURCHASES"), parse("انتقل إلى فواتير الشراء"))
        assertEquals(OfflineCommand.OpenScreen("REPORTS"), parse("افتح التقارير"))
        assertEquals(OfflineCommand.OpenScreen("COUNTING"), parse("افتح الجرد"))
        assertEquals(OfflineCommand.OpenScreen("STOCK"), parse("افتح المخزون"))
        assertEquals(OfflineCommand.OpenScreen("SETTINGS"), parse("روح للإعدادات"))
        assertEquals(OfflineCommand.OpenScreen("NEW_PURCHASE_REQUEST"), parse("افتح طلب شراء جديد"))
        assertEquals(OfflineCommand.OpenScreen("NEW_SALES_INVOICE"), parse("افتح فاتورة بيع جديدة"))
    }

    @Test
    fun `opening the goals screen is a trip, not a question about the goals`() {
        assertEquals(OfflineCommand.OpenScreen("GOALS"), parse("افتح الأهداف"))
    }

    @Test
    fun `a bare screen name opens it`() {
        assertEquals(OfflineCommand.OpenScreen("INVENTORY"), parse("الأصناف"))
        assertEquals(OfflineCommand.OpenScreen("SALES"), parse("المبيعات"))
        assertEquals(OfflineCommand.OpenScreen("DATA_CENTER"), parse("مركز البيانات"))
    }

    // ------------------------------------------------------------------ the item list with filters

    @Test
    fun `show the expired items - in the chat, with a button to open the list`() {
        assertEquals(inventory(status = "EXPIRED"), parse("اعرض الأصناف منتهية الصلاحية"))
        assertEquals(inventory(status = "EXPIRED"), parse("هل لدي أصناف منتهية الصلاحية"))
        assertEquals(inventory(status = "EXPIRED"), parse("اعرض المنتهية"))
    }

    @Test
    fun `filter by a quantity limit - take me to the list`() {
        assertEquals(inventory(below = 10.0, openScreen = true), parse("فلتر الأصناف التي رصيدها أقل من 10"))
    }

    @Test
    fun `the status words`() {
        assertEquals(inventory(status = "LOW"), parse("ما الأصناف المنخفضة؟"))
        assertEquals(inventory(status = "LOW"), parse("اعرض النواقص"))
        assertEquals(inventory(status = "NEAR_EXPIRY"), parse("ما الأصناف القريبة من الانتهاء؟"))
        assertEquals(inventory(status = "ZERO"), parse("ما الأصناف التي مخزونها صفر؟"))
        assertEquals(inventory(status = "AVAILABLE"), parse("وريني الأصناف المتوفرة"))
    }

    @Test
    fun `running low is about stock running out, not about expiry`() {
        assertEquals(inventory(status = "LOW", branch = "الاول"), parse("اعرض الأصناف التي أوشكت على النفاد في الفرع الأول"))
    }

    @Test
    fun `a branch or a category is passed on as the person said it`() {
        assertEquals(inventory(status = "EXPIRED", branch = "الثاني"), parse("كم صنف منتهي الصلاحية في الفرع الثاني"))
        assertEquals(inventory(status = "LOW", branch = "الرئيسي", openScreen = true), parse("فلتر الأصناف المنخفضة في الفرع الرئيسي"))
        assertEquals(inventory(status = "LOW", category = "المنظفات"), parse("اعرض الأصناف الناقصة في تصنيف المنظفات"))
    }

    @Test
    fun `quantity limits are strict unless the person says or-less or or-more`() {
        assertEquals(inventory(above = 20.0), parse("اعرض الأصناف التي رصيدها اكثر من 20"))
        assertEquals(inventory(below = 10.0, inclusive = true), parse("الأصناف التي رصيدها 10 أو أقل"))
        assertEquals(inventory(above = 10.0, inclusive = true), parse("اعرض الأصناف التي رصيدها عشرة أو اكثر"))
        assertEquals(inventory(below = 8.0, inclusive = true), parse("اعرض الأصناف التي رصيدها لا يزيد عن 8"))
        assertEquals(inventory(above = 3.0, inclusive = true), parse("اعرض الأصناف على الأقل 3"))
    }

    @Test
    fun `between two numbers`() {
        assertEquals(inventory(below = 10.0, above = 5.0), parse("اعرض الأصناف التي رصيدها بين 5 و 10"))
    }

    @Test
    fun `search by name`() {
        assertEquals(inventory(search = "صابون"), parse("ابحث عن صابون"))
    }

    // ------------------------------------------------------------------ reports

    @Test
    fun `which goals were not met`() {
        assertEquals(OfflineCommand.Goals(month = null, onlyUnmet = true), parse("ما الأصناف التي لم تحقق هدف الشهر؟"))
    }

    @Test
    fun `goals for another month`() {
        assertEquals(OfflineCommand.Goals("2026-09", true), parse("ما الأصناف التي لم تحقق هدف الشهر الماضي"))
        assertEquals(OfflineCommand.Goals("2026-09", false), parse("الأهداف لشهر 2026-09"))
        assertEquals(OfflineCommand.Goals("2026-09", false), parse("ما حالة الأهداف في سبتمبر"))
    }

    @Test
    fun `last month reaches back over the new year`() {
        assertEquals(OfflineCommand.Goals("2025-12", true), parse("ما الأصناف التي لم تحقق هدف الشهر الماضي", january))
    }

    @Test
    fun `inbound against sales`() {
        assertEquals(OfflineCommand.Flows(month = null, byBranch = false), parse("قارن الوارد بالمبيعات والأهداف"))
        assertEquals(OfflineCommand.Flows(month = null, byBranch = true), parse("قارن الوارد بالمبيعات لكل فرع"))
    }

    @Test
    fun `rank the branches`() {
        assertEquals(OfflineCommand.RankBranches(null), parse("رتب الفروع حسب نسبة تحقيق الهدف"))
        assertEquals(OfflineCommand.RankBranches(null), parse("ما هو أفضل فرع في تحقيق الهدف"))
    }

    // ------------------------------------------------------------------ purchase proposal

    @Test
    fun `a purchase proposal from the shortages`() {
        assertEquals(OfflineCommand.SuggestPurchase(null), parse("جهّز أمر شراء للنواقص"))
    }

    @Test
    fun `the multiplier is read, even behind a prefix`() {
        assertEquals(OfflineCommand.SuggestPurchase(3.0), parse("جهّز أمر شراء للنواقص بثلاثة أضعاف"))
        assertEquals(OfflineCommand.SuggestPurchase(4.0), parse("جهز أمر شراء للنواقص 4 أضعاف"))
        assertEquals(OfflineCommand.SuggestPurchase(2.0), parse("اطلب النواقص ضعف الحد"))
    }

    @Test
    fun `save the proposal as a draft`() {
        assertEquals(OfflineCommand.DraftFromSuggestion, parse("جهز المسودة"))
    }

    // ------------------------------------------------------------------ name matching

    @Test
    fun `names after the word match are split by commas and new lines`() {
        assertEquals(
            OfflineCommand.MatchNames(listOf("صابون سائل 500 مل", "منظف ارضيات", "عطر جو")),
            parse("طابق: صابون سائل 500 مل، منظف ارضيات\nعطر جو")
        )
    }

    @Test
    fun `an introduction and list bullets are not part of the names`() {
        assertEquals(
            OfflineCommand.MatchNames(listOf("صابون سايل 500 مل", "مبيض ملابس")),
            parse("طابق الأسماء التالية:\n- صابون سايل 500 مل\n1) مبيض ملابس")
        )
    }

    @Test
    fun `match with no names explains how`() {
        assertEquals(OfflineCommand.MatchNames(emptyList()), parse("طابق"))
    }

    // ------------------------------------------------------------------ help, and what is NOT a command

    @Test
    fun `help`() {
        assertEquals(OfflineCommand.Help, parse("ماذا تستطيع أن تفعل؟"))
        assertEquals(OfflineCommand.Help, parse("مساعدة"))
    }

    @Test
    fun `free-form requests are left for the language model`() {
        listOf(
            "كم سعر الدولار اليوم؟",
            "اكتب لي رسالة شكر للمورد",
            "حلل لي مبيعات الشهر الماضي بالتفصيل",
            "ما رأيك في المخزون؟",
            "السلام عليكم",
            "اشرح لي طريقة الجرد",
            ""
        ).forEach { text -> assertNull("should not be recognised: $text", parse(text)) }
    }

    @Test
    fun `every quick command is a sentence the parser understands`() {
        AssistantQuickCommands.all.forEach { command ->
            assertNotNull("${command.label}: ${command.text}", parse(command.text))
        }
    }

    @Test
    fun `quick commands are not repeated`() {
        assertEquals(AssistantQuickCommands.all.size, AssistantQuickCommands.all.map { it.label }.toSet().size)
        assertEquals(AssistantQuickCommands.all.size, AssistantQuickCommands.all.map { it.text }.toSet().size)
        assertTrue(AssistantQuickCommands.all.none { it.label.isBlank() || it.text.isBlank() })
    }

    @Test
    fun `the way a command is phrased decides whether the screen opens`() {
        val show = parse("اعرض الأصناف منتهية الصلاحية") as OfflineCommand.ShowInventory
        val go = parse("فلتر الأصناف التي رصيدها أقل من 10") as OfflineCommand.ShowInventory
        assertFalse(show.openScreen)
        assertTrue(go.openScreen)
    }
}
