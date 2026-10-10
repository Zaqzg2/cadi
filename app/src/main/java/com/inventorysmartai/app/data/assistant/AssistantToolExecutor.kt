package com.inventorysmartai.app.data.assistant

import com.inventorysmartai.app.data.ai.provider.AiJson
import com.inventorysmartai.app.data.assistant.files.FileToolExecutor
import com.inventorysmartai.app.data.remote.BackendFailure
import com.inventorysmartai.app.domain.assistant.LocalToolExecutor
import com.inventorysmartai.app.domain.repository.GoogleWorkspaceRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the assistant actually runs: [DefaultLocalToolExecutor] for everything that touches Room, plus the four Google
 * Workspace tools, which are forwarded to the backend through [GoogleWorkspaceRepository] — the SAME calls the Reports
 * screen's buttons make. All four are write/send actions, so [requiresConfirmation] is true for each and the repository
 * loop asks the person (with the Arabic text from [describeForConfirmation]) before [execute] is ever called.
 *
 * A failed action never throws into the conversation: the model gets a small JSON error it can explain, e.g.
 * `{"error":"google_not_linked", ...}`.
 */
@Singleton
class AssistantToolExecutor @Inject constructor(
    private val local: DefaultLocalToolExecutor,
    private val workspace: GoogleWorkspaceRepository,
    private val files: FileToolExecutor
) : LocalToolExecutor {

    override fun requiresConfirmation(name: String): Boolean =
        name in AssistantToolCatalog.WORKSPACE_TOOL_NAMES || local.requiresConfirmation(name)

    override suspend fun describeForConfirmation(name: String, argumentsJson: String): String =
        if (name in AssistantToolCatalog.WORKSPACE_TOOL_NAMES) {
            WorkspaceToolSupport.describe(name, AiJson.parseObject(argumentsJson).orEmpty())
        } else {
            local.describeForConfirmation(name, argumentsJson)
        }

    override suspend fun execute(name: String, argumentsJson: String): String {
        if (name in AssistantToolCatalog.FILE_TOOL_NAMES) return files.execute(name, argumentsJson)
        if (name !in AssistantToolCatalog.WORKSPACE_TOOL_NAMES) return local.execute(name, argumentsJson)
        val args = AiJson.parseObject(argumentsJson).orEmpty()
        return when (name) {
            "saveReportToDrive" -> {
                val title = WorkspaceToolSupport.text(args, "title")
                val report = WorkspaceToolSupport.text(args, "reportMarkdown")
                if (title.isBlank() || report.isBlank()) return WorkspaceToolSupport.missing("title, reportMarkdown")
                workspace.saveReportToDrive(title, report).fold(
                    onSuccess = { WorkspaceToolSupport.success("saved_to_drive", "fileId" to it.fileId, "link" to it.webViewLink) },
                    onFailure = { WorkspaceToolSupport.failure(it) }
                )
            }
            "createGoogleDoc" -> {
                val title = WorkspaceToolSupport.text(args, "title")
                val report = WorkspaceToolSupport.text(args, "reportMarkdown")
                if (title.isBlank() || report.isBlank()) return WorkspaceToolSupport.missing("title, reportMarkdown")
                workspace.createGoogleDoc(title, report).fold(
                    onSuccess = { WorkspaceToolSupport.success("doc_created", "fileId" to it.fileId, "link" to it.webViewLink) },
                    onFailure = { WorkspaceToolSupport.failure(it) }
                )
            }
            "createCalendarEvent" -> {
                val title = WorkspaceToolSupport.text(args, "title")
                val start = WorkspaceToolSupport.text(args, "startIso")
                val end = WorkspaceToolSupport.text(args, "endIso")
                if (title.isBlank() || start.isBlank() || end.isBlank()) return WorkspaceToolSupport.missing("title, startIso, endIso")
                workspace.createCalendarEvent(title, start, end, WorkspaceToolSupport.text(args, "description").ifBlank { null }).fold(
                    onSuccess = { WorkspaceToolSupport.success("event_created", "eventId" to it.eventId, "link" to it.htmlLink) },
                    onFailure = { WorkspaceToolSupport.failure(it) }
                )
            }
            "sendEmail" -> {
                val to = WorkspaceToolSupport.text(args, "to")
                val subject = WorkspaceToolSupport.text(args, "subject")
                val body = WorkspaceToolSupport.text(args, "body")
                if (to.isBlank() || subject.isBlank() || body.isBlank()) return WorkspaceToolSupport.missing("to, subject, body")
                if (!WorkspaceToolSupport.isSafeEmailHeader(to) || !WorkspaceToolSupport.isSafeEmailHeader(subject) || !to.contains('@')) {
                    return WorkspaceToolSupport.invalid("to / subject must be single-line and to must be an email address")
                }
                workspace.sendEmail(to, subject, body, WorkspaceToolSupport.text(args, "attachmentDriveFileId").ifBlank { null }).fold(
                    onSuccess = { WorkspaceToolSupport.success("email_sent") },
                    onFailure = { WorkspaceToolSupport.failure(it) }
                )
            }
            else -> WorkspaceToolSupport.invalid("unknown tool $name")
        }
    }
}

/** Pure helpers (plain JVM, unit-tested): the confirmation text and the small JSON the model receives back. */
internal object WorkspaceToolSupport {

    fun text(args: Map<String, Any?>, key: String): String = (args[key] as? String)?.trim().orEmpty()

    /** The exact Arabic sentence shown in the confirmation dialog. */
    fun describe(name: String, args: Map<String, Any?>): String = when (name) {
        "saveReportToDrive" -> "حفظ التقرير \"${text(args, "title")}\" كملف في Google Drive (مجلد التقارير)"
        "createGoogleDoc" -> "إنشاء مستند Google Docs بعنوان \"${text(args, "title")}\" في Drive"
        "createCalendarEvent" ->
            "إضافة موعد \"${text(args, "title")}\" إلى تقويم Google من ${text(args, "startIso")} إلى ${text(args, "endIso")}"
        "sendEmail" -> "إرسال بريد إلكتروني من حسابك في Gmail إلى ${text(args, "to")} بعنوان \"${text(args, "subject")}\""
        else -> "تنفيذ العملية: $name"
    }

    /** A To/Subject value must be one line, otherwise it could smuggle extra mail headers (header injection). */
    fun isSafeEmailHeader(value: String): Boolean = value.none { it == '\r' || it == '\n' }

    fun success(status: String, vararg extra: Pair<String, String?>): String =
        AiJson.toJson(buildMap<String, Any?> {
            put("status", status)
            extra.forEach { (key, value) -> if (value != null) put(key, value) }
        })

    fun missing(fields: String): String =
        AiJson.toJson(mapOf("error" to "missing_argument", "message" to "Required arguments missing: $fields"))

    fun invalid(message: String): String = AiJson.toJson(mapOf("error" to "invalid_argument", "message" to message))

    fun failure(error: Throwable): String {
        val code = (error as? BackendFailure.Structured)?.code
        val key = when {
            code == "GOOGLE_NOT_LINKED" || code == "GOOGLE_AUTH_REFRESH_FAILED" -> "google_not_linked"
            code == "GOOGLE_NOT_CONFIGURED" -> "google_not_configured"
            error is BackendFailure.NetworkUnavailable -> "backend_unreachable"
            else -> "tool_failed"
        }
        val message = (error as? BackendFailure)?.messageAr ?: "حدث خطأ غير متوقع"
        return AiJson.toJson(mapOf("error" to key, "message" to message))
    }
}
