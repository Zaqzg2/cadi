package com.inventorysmartai.app.data.assistant.files

import com.inventorysmartai.app.data.ai.provider.AiJson
import com.inventorysmartai.app.domain.assistant.AttachmentKind
import com.inventorysmartai.app.domain.assistant.GeneratedFileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Answers the assistant's file tools (listAttachments, readAttachment, queryTable, fillForm, calculate,
 * calculateTieredCommission, createSpreadsheet, buildDashboard). The real work — reading cells, grouping and adding up,
 * evaluating arithmetic, editing the .xlsx, drawing the dashboard — is in the plain-Java engine of this package, which has its
 * own unit tests; this class only finds the attachment, hands over the arguments, saves any file that comes out, and turns the
 * result into the short JSON the model reads. Every failure becomes a small `{error, message}` the model can explain.
 */
@Singleton
class FileToolExecutor @Inject constructor(private val store: AttachmentStore) {

    suspend fun execute(name: String, argumentsJson: String): String {
        val args: Map<String, Any?> = AiJson.parseObject(argumentsJson).orEmpty()
        return try {
            AiJson.toJson(dispatch(name, args))
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            AiJson.toJson(mapOf("error" to "invalid_argument", "message" to (e.message ?: "وسيط غير صالح")))
        } catch (e: Exception) {
            AiJson.toJson(mapOf("error" to "tool_failed", "message" to "تعذّر تنفيذ الأداة $name"))
        }
    }

    private suspend fun dispatch(name: String, args: Map<String, Any?>): Map<String, Any?> = when (name) {
        "listAttachments" -> listAttachments()
        "readAttachment" -> readAttachment(args)
        "queryTable" -> queryTable(args)
        "fillForm" -> fillForm(args)
        "createSpreadsheet" -> createSpreadsheet(args)
        "buildDashboard" -> buildDashboard(args)
        "calculate" -> FileToolLogic.calculate(args)
        "calculateTieredCommission" -> CommissionCalculator.calculate(args)
        else -> mapOf("error" to "unknown_tool", "message" to "أداة غير معروفة: $name")
    }

    // ------------------------------------------------------------------ reading

    private fun listAttachments(): Map<String, Any?> {
        val conversationId = store.activeConversationId
        val entries = if (conversationId == null) emptyList() else store.forConversation(conversationId)
        val items = entries.map { entry ->
            val item = linkedMapOf<String, Any?>(
                "id" to entry.info.id,
                "name" to entry.info.name,
                "kind" to entry.info.kind.name,
                "summary" to entry.info.summary
            )
            entry.workbook?.let { item["sheets"] = FileToolLogic.describeWorkbook(it)["sheets"] }
            item
        }
        return mapOf("attachments" to items)
    }

    private suspend fun readAttachment(args: Map<String, Any?>): Map<String, Any?> {
        val entry = store.find(args["attachmentId"] as? String) ?: return notFound()
        store.ensureRead(entry)?.let { return readFailure(it) }
        val book = entry.workbook
        val text = entry.text
        val asksForCells = args["sheet"] != null || args["find"] != null || args["fromRow"] != null
        return when {
            book != null && (entry.info.kind == AttachmentKind.SPREADSHEET || text == null || asksForCells) -> FileToolLogic.readWorkbook(book, args)
            text != null -> FileToolLogic.readText(text, book, args)
            else -> mapOf("error" to "empty", "message" to "لا يوجد محتوى مقروء في هذا الملف")
        }
    }

    private suspend fun queryTable(args: Map<String, Any?>): Map<String, Any?> {
        val entry = store.find(args["attachmentId"] as? String) ?: return notFound()
        store.ensureRead(entry)?.let { return readFailure(it) }
        val book = entry.workbook
            ?: return mapOf("error" to "no_table", "message" to "هذا الملف لا يحتوي جدولًا يمكن الحساب عليه. اقرأه بـ readAttachment ثم استخدم calculate للحسابات.")
        return FileToolLogic.queryTable(book, args)
    }

    // ------------------------------------------------------------------ writing

    private suspend fun fillForm(args: Map<String, Any?>): Map<String, Any?> {
        val entry = store.find(args["attachmentId"] as? String) ?: return notFound()
        if (entry.info.kind != AttachmentKind.SPREADSHEET || entry.mimeType != FileKinds.XLSX_MIME) {
            return mapOf(
                "error" to "unsupported_form",
                "message" to "تعبئة النماذج تدعم ملفات Excel (.xlsx) فقط. لهذا الملف استخدم createSpreadsheet لإنشاء ملف جديد بالبيانات."
            )
        }
        val edits = FileToolLogic.parseEdits(args)
        val original = store.bytesOf(entry)
        val report = withContext(Dispatchers.Default) { XlsxTemplateFiller.fill(original, edits) }
        if (report.applied == 0) {
            return mapOf(
                "error" to "nothing_filled",
                "message" to "لم تُكتب أي خلية. راجع الأسباب في skipped وصحّح العناوين (اقرأ النموذج بـ readAttachment).",
                "skipped" to report.skipped
            )
        }
        val requested = (args["outputName"] as? String).orEmpty().ifBlank { entry.info.name.substringBeforeLast('.') + " (معبأ)" }
        val file = store.saveOutput(FileNames.safe(requested, "نموذج معبأ", "xlsx"), GeneratedFileType.SPREADSHEET, FileKinds.XLSX_MIME, report.bytes)
        return linkedMapOf(
            "status" to "filled",
            "file" to file.name,
            "cellsWritten" to report.applied,
            "redirected" to report.redirected,
            "skipped" to report.skipped,
            "note" to SHOWN_NOTE
        )
    }

    private suspend fun createSpreadsheet(args: Map<String, Any?>): Map<String, Any?> {
        val sheets = FileToolLogic.parseSheets(args)
        val rtl = args["rtl"] as? Boolean ?: true
        val bytes = withContext(Dispatchers.Default) { XlsxWriter.write(sheets, rtl) }
        val name = FileNames.safe((args["title"] as? String).orEmpty(), "جدول بيانات", "xlsx")
        val file = store.saveOutput(name, GeneratedFileType.SPREADSHEET, FileKinds.XLSX_MIME, bytes)
        return linkedMapOf(
            "status" to "created",
            "file" to file.name,
            "sheets" to sheets.size,
            "rows" to sheets.sumOf { it.rows.size },
            "note" to SHOWN_NOTE
        )
    }

    private suspend fun buildDashboard(args: Map<String, Any?>): Map<String, Any?> {
        // Photos/PDF named as a data source must be read (OCR) before the plain engine runs.
        for (id in FileToolLogic.referencedAttachmentIds(args)) {
            val entry = store.find(id) ?: continue
            store.ensureRead(entry)?.let { return readFailure(it) }
        }
        val generatedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        val html = withContext(Dispatchers.Default) {
            FileToolLogic.dashboardHtml(args, { id -> store.find(id)?.workbook }, generatedAt)
        }
        val name = FileNames.safe((args["title"] as? String).orEmpty(), "لوحة بيانات", "html")
        val file = store.saveOutput(name, GeneratedFileType.DASHBOARD, "text/html", html.toByteArray(Charsets.UTF_8))
        return linkedMapOf("status" to "created", "file" to file.name, "note" to SHOWN_NOTE)
    }

    // ------------------------------------------------------------------ errors

    private fun notFound(): Map<String, Any?> = mapOf(
        "error" to "attachment_not_found",
        "message" to "لم أجد الملف المطلوب. استخدم listAttachments لمعرفة المعرّفات الصحيحة."
    )

    private fun readFailure(message: String): Map<String, Any?> = mapOf("error" to "read_failed", "message" to message)

    private companion object {
        const val SHOWN_NOTE = "الملف ظاهر للمستخدم تلقائيًا تحت ردّك مع زرّي فتح ومشاركة؛ اذكر ما أنشأته باختصار ولا تلصق محتواه ولا تذكر مسارات."
    }
}
