package com.inventorysmartai.app.domain.assistant

/** Mirrors the backend's `ToolExecutionSite` (gemini/ToolCatalog.kt) — kept as a small independent
 *  enum here rather than a shared module, since the app and the backend are deliberately separate
 *  Gradle projects with no compiled-code dependency between them (see backend/README.md). */
enum class ToolExecutionSite { LOCAL, BACKEND }

data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val text: String,
    val timestamp: Long,
    /** Set on an assistant message that reported tool results — shown as a small "المصدر:
     *  بيانات التطبيق" reference chip (spec: "AI source/reference display") rather than left
     *  implicit, so the person can tell a real-data answer from plain conversation. */
    val usedLocalData: Boolean = false
)

enum class ChatRole { USER, ASSISTANT }

/** What the app should do next after a turn — either show the final text, or block on an
 *  explicit confirmation before a write/send action runs (spec: "ACTION CONFIRMATION"). */
sealed class AssistantStepResult {
    data class Final(val text: String, val usedLocalData: Boolean) : AssistantStepResult()
    data class ConfirmationRequired(
        val callId: String,
        val toolName: String,
        val site: ToolExecutionSite,
        /** Human-readable Arabic summary of exactly what will happen — shown verbatim in the
         *  confirmation dialog (e.g. "إنشاء طلب شراء لفرع الرياض يتضمن 3 أصناف"). */
        val actionDescriptionAr: String
    ) : AssistantStepResult()
    data class Error(val messageAr: String) : AssistantStepResult()
}

/** What's currently selected in the app, forwarded as free-text context on the next message —
 *  spec's "AI CHAT CONTEXT": current conversation, selected product/invoice/purchase request/
 *  report. Never includes secrets/tokens (spec: "do not expose secrets... to Gemini prompts"). */
data class AssistantContext(
    val selectedProductId: Long? = null,
    val selectedProductName: String? = null,
    val selectedInvoiceId: Long? = null,
    val selectedPurchaseRequestId: Long? = null,
    val selectedReportType: String? = null
) {
    /** Rendered once per message rather than kept as structured fields on the wire — the backend
     *  only ever needs to hand this to Gemini as plain conversational grounding, never to branch
     *  logic on it (see backend AssistantOrchestrator.kt: `context` is treated as opaque text). */
    fun toPromptText(): String? {
        val parts = buildList {
            selectedProductId?.let { add("المنتج المحدد حاليًا: ${selectedProductName ?: "#$it"} (id=$it)") }
            selectedInvoiceId?.let { add("الفاتورة المحددة حاليًا: id=$it") }
            selectedPurchaseRequestId?.let { add("طلب الشراء المحدد حاليًا: id=$it") }
            selectedReportType?.let { add("نوع التقرير المعروض حاليًا: $it") }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }
}
