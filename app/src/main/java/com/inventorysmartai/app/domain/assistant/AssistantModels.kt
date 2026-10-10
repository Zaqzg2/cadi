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
    val usedLocalData: Boolean = false,
    /** Files the person attached to this message (user messages only) — shown as small chips under the bubble. */
    val attachments: List<AttachmentInfo> = emptyList(),
    /** Files the assistant created while answering (assistant messages only) — shown as cards with open/share buttons. */
    val files: List<GeneratedFile> = emptyList()
)

enum class ChatRole { USER, ASSISTANT }

/** What kind of file the person attached. Mirrors the file engine's own kind (data/assistant/files/FileKinds.java), which
 *  lives in the data layer, so the domain keeps its own copy of the four names. */
enum class AttachmentKind { SPREADSHEET, TEXT_DOCUMENT, IMAGE, PDF }

/** A file attached to a chat message: shown as a chip, and handed to the model by [id] (it reads it with the file tools). */
data class AttachmentInfo(
    val id: String,
    val name: String,
    val kind: AttachmentKind,
    val sizeBytes: Long,
    /** One short Arabic line for the chip, e.g. "ورقة واحدة · 20 صف". */
    val summary: String
)

enum class GeneratedFileType { SPREADSHEET, DASHBOARD }

/** A file the assistant created for the person — a filled form, an exported sheet, a dashboard. It lives in the app's cache
 *  and is opened or shared through the FileProvider; [path] is never shown or sent to the model. */
data class GeneratedFile(
    val id: String,
    val name: String,
    val type: GeneratedFileType,
    val mimeType: String,
    val path: String
)

/** What the app should do next after a turn — either show the final text, or block on an
 *  explicit confirmation before a write/send action runs (spec: "ACTION CONFIRMATION"). */
sealed class AssistantStepResult {
    data class Final(val text: String, val usedLocalData: Boolean, val files: List<GeneratedFile> = emptyList()) : AssistantStepResult()
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
 *  report. Never includes secrets/tokens (spec: "do not expose secrets... to the model prompts"). */
data class AssistantContext(
    val selectedProductId: Long? = null,
    val selectedProductName: String? = null,
    val selectedInvoiceId: Long? = null,
    val selectedPurchaseRequestId: Long? = null,
    val selectedReportType: String? = null
) {
    /** Rendered once per message rather than kept as structured fields — the model only ever needs
     *  this as plain conversational grounding, and nothing branches logic on it. */
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
