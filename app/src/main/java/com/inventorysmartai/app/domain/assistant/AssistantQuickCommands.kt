package com.inventorysmartai.app.domain.assistant

/** One command the assistant carries out on its own — a short [label] for a chip and the full sentence ([text]) it sends. */
data class QuickCommand(val label: String, val text: String)

/**
 * The commands that work with NO internet (see [OfflineCommandParser]). One list feeds the chips above the input box, the cards on the
 * empty chat, and the "what can you do offline" answer, so they can never disagree. Every [QuickCommand.text] is a sentence the
 * parser recognises (a test checks it).
 */
object AssistantQuickCommands {
    val all: List<QuickCommand> = listOf(
        QuickCommand("افتح الأصناف", "افتح صفحة الأصناف"),
        QuickCommand("رصيد أقل من 10", "فلتر الأصناف التي رصيدها أقل من 10"),
        QuickCommand("منتهية الصلاحية", "اعرض الأصناف منتهية الصلاحية"),
        QuickCommand("الأصناف المنخفضة", "ما الأصناف المنخفضة؟"),
        QuickCommand("أهداف لم تتحقق", "ما الأصناف التي لم تحقق هدف الشهر؟"),
        QuickCommand("الوارد والمبيعات", "قارن الوارد بالمبيعات والأهداف"),
        QuickCommand("ترتيب الفروع", "رتب الفروع حسب نسبة تحقيق الهدف"),
        QuickCommand("أمر شراء للنواقص", "جهّز أمر شراء للنواقص"),
        QuickCommand("مطابقة الأسماء", "طابق")
    )
}
