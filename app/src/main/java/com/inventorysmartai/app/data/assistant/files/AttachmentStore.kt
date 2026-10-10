package com.inventorysmartai.app.data.assistant.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.inventorysmartai.app.data.remote.BackendFailure
import com.inventorysmartai.app.domain.assistant.AttachmentInfo
import com.inventorysmartai.app.domain.assistant.AttachmentKind
import com.inventorysmartai.app.domain.assistant.GeneratedFile
import com.inventorysmartai.app.domain.assistant.GeneratedFileType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Everything the assistant knows about files, per conversation: the files the person attached (copied into the app's cache
 * the moment they are picked, so a later read never depends on the picker's temporary permission), what has been read from
 * them so far, and the files the assistant itself created.
 *
 * Reading is lazy where it costs something: spreadsheets and Word files are parsed on attach (so a corrupt file is refused
 * up front and the chip can say "ورقة واحدة · 20 صف"); photos and PDFs are sent to the backend for text only when a tool first
 * needs them. Files stay in the cache directory, which Android may clear — a conversation is short-lived anyway.
 */
@Singleton
class AttachmentStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val textReader: AttachmentTextReader
) {
    /** One attached file and whatever has been read from it. */
    class Entry(val info: AttachmentInfo, val mimeType: String, val file: File) {
        /** Sheets: an .xlsx/.csv, or the tables found in OCR/Word text. Null until read, or when the file has none. */
        @Volatile var workbook: WorkbookData? = null

        /** Plain text of a Word/text file, or the OCR text of a photo/PDF. Null until read. */
        @Volatile var text: String? = null

        /** Why reading failed (shown to the model and the person), so a broken OCR is not retried on every tool call. */
        @Volatile var readError: String? = null
    }

    private val entries = ConcurrentHashMap<String, Entry>()
    private val byConversation = ConcurrentHashMap<String, CopyOnWriteArrayList<String>>()
    private val counter = AtomicInteger(0)
    private val turnOutputs = CopyOnWriteArrayList<GeneratedFile>()

    /** The conversation the running turn belongs to; the file tools use it to find "the attachments". */
    @Volatile var activeConversationId: String? = null
        private set

    private val attachmentsDir: File get() = File(context.cacheDir, "assistant_attachments").apply { mkdirs() }
    private val outputsDir: File get() = File(context.cacheDir, "assistant_outputs").apply { mkdirs() }

    // ------------------------------------------------------------------ turns

    fun beginTurn(conversationId: String) {
        activeConversationId = conversationId
        turnOutputs.clear()
    }

    /** Continues the same turn after a confirmation: the files created before the question stay waiting for the final answer. */
    fun resumeTurn(conversationId: String) {
        activeConversationId = conversationId
    }

    /** The files created since [beginTurn]; hands them over exactly once. */
    fun takeTurnOutputs(): List<GeneratedFile> {
        val files = turnOutputs.toList()
        turnOutputs.clear()
        return files
    }

    // ------------------------------------------------------------------ attachments

    fun hasAttachments(conversationId: String): Boolean = byConversation[conversationId]?.isNotEmpty() == true

    fun forConversation(conversationId: String): List<Entry> =
        byConversation[conversationId].orEmpty().mapNotNull { entries[it] }

    /**
     * Finds an attachment of the running conversation by its id, or by its file name (models sometimes pass the name). A blank
     * reference means "the" attachment when there is exactly one.
     */
    fun find(reference: String?): Entry? {
        val conversationId = activeConversationId ?: return null
        val all = forConversation(conversationId)
        val ref = reference?.trim().orEmpty()
        if (ref.isEmpty()) return all.singleOrNull()
        return all.firstOrNull { it.info.id == ref } ?: all.firstOrNull { it.info.name == ref }
    }

    /** What the model is told about the files attached to THIS message (ids, names and kinds), appended after the question. */
    fun promptNote(conversationId: String, attachmentIds: List<String>): String {
        val lines = attachmentIds.mapNotNull { id -> entries[id]?.takeIf { it in forConversation(conversationId) } }.map { entry ->
            val kind = when (entry.info.kind) {
                AttachmentKind.SPREADSHEET -> "جدول بيانات"
                AttachmentKind.TEXT_DOCUMENT -> "مستند نصي"
                AttachmentKind.IMAGE -> "صورة — تُقرأ بالقراءة الضوئية عند الطلب"
                AttachmentKind.PDF -> "ملف PDF — يُقرأ بالقراءة الضوئية عند الطلب"
            }
            "- ${entry.info.id}: ${entry.info.name} — $kind (${entry.info.summary})"
        }
        return if (lines.isEmpty()) "" else "[ملفات مرفقة]\n" + lines.joinToString("\n")
    }

    /** Reads the picked file, validates and parses it, and registers it. Every failure carries an Arabic message. */
    suspend fun add(conversationId: String, reference: String): Result<AttachmentInfo> = withContext(Dispatchers.IO) {
        try {
            if (forConversation(conversationId).size >= MAX_ATTACHMENTS_PER_CONVERSATION) {
                throw IllegalArgumentException("الحد الأقصى $MAX_ATTACHMENTS_PER_CONVERSATION ملفات في المحادثة الواحدة")
            }
            val uri = Uri.parse(reference)
            val (displayName, declaredSize) = describe(uri)
            if (declaredSize > MAX_FILE_BYTES) throw IllegalArgumentException("حجم الملف أكبر من الحد المسموح (${MAX_FILE_BYTES / (1024 * 1024)} ميغابايت)")
            val bytes = readBytes(uri)
            val detected = FileKinds.detect(displayName, context.contentResolver.getType(uri), bytes)

            val id = "f${counter.incrementAndGet()}"
            val detectedKind: FileKinds.Kind = detected.kind
            val kind = AttachmentKind.valueOf(detectedKind.name)
            var workbook: WorkbookData? = null
            var text: String? = null
            val summary: String = when (detectedKind) {
                FileKinds.Kind.SPREADSHEET -> {
                    val book = if (detected.extension == "csv") WorkbookData.fromCsv(bytes, null) else WorkbookData.fromXlsx(bytes, AssistantSaxRunner)
                    if (book.isEmpty() || book.sheets().all { it.isEmpty }) throw IllegalArgumentException("الملف لا يحتوي أي بيانات")
                    workbook = book
                    FileToolLogic.workbookSummary(book)
                }
                FileKinds.Kind.TEXT_DOCUMENT -> {
                    val content = if (detected.extension == "docx") TextTables.docxText(bytes) else TextTables.decode(bytes)
                    if (content.isBlank()) throw IllegalArgumentException("الملف لا يحتوي أي نص")
                    text = content
                    workbook = WorkbookData.fromMarkdown(content).takeIf { !it.isEmpty }
                    "${content.length} حرف"
                }
                FileKinds.Kind.IMAGE -> "صورة · ${formatSize(bytes.size)}"
                FileKinds.Kind.PDF -> "PDF · ${formatSize(bytes.size)}"
            }

            val storedName = FileNames.safe(displayName, "file", "")
            val stored = File(attachmentsDir, "${id}_${UUID.randomUUID().toString().take(8)}_$storedName")
            stored.writeBytes(bytes)
            val info = AttachmentInfo(id, displayName, kind, bytes.size.toLong(), summary)
            val entry = Entry(info, detected.mimeType, stored)
            entry.workbook = workbook
            entry.text = text
            entries[id] = entry
            byConversation.getOrPut(conversationId) { CopyOnWriteArrayList() }.add(id)
            Result.success(info)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("تعذّرت قراءة الملف. تأكد أنه غير تالف ثم أعد المحاولة", e))
        }
    }

    fun remove(conversationId: String, attachmentId: String) {
        byConversation[conversationId]?.remove(attachmentId)
        entries.remove(attachmentId)?.file?.delete()
    }

    /**
     * Makes sure a photo/PDF has been read (OCR) — spreadsheets and Word files already were on attach. Returns null on success,
     * or the Arabic reason it could not be read. The reason is remembered, so a broken backend is not hammered by every tool call.
     */
    suspend fun ensureRead(entry: Entry): String? {
        if (entry.text != null || entry.workbook != null) return null
        entry.readError?.let { return it }
        if (entry.info.kind != AttachmentKind.IMAGE && entry.info.kind != AttachmentKind.PDF) return "لا يوجد محتوى مقروء في هذا الملف"
        val bytes = withContext(Dispatchers.IO) { entry.file.readBytes() }
        val sessionId = activeConversationId ?: "assistant"
        val result = textReader.read(bytes, entry.mimeType, sessionId)
        val text = result.getOrNull()
        if (text == null) {
            val message = result.exceptionOrNull()?.let { (it as? BackendFailure)?.messageAr ?: it.message }
                ?: "تعذّرت قراءة الملف"
            entry.readError = message
            return message
        }
        if (text.isBlank()) {
            entry.readError = "لم يُعثر على أي نص مقروء في الملف"
            return entry.readError
        }
        entry.text = text
        entry.workbook = WorkbookData.fromMarkdown(text).takeIf { !it.isEmpty }
        return null
    }

    // ------------------------------------------------------------------ files the assistant creates

    /** Saves a generated file under the cache's shareable folder and remembers it for the running turn's answer. */
    fun saveOutput(name: String, type: GeneratedFileType, mimeType: String, bytes: ByteArray): GeneratedFile {
        val folder = File(outputsDir, UUID.randomUUID().toString().take(8)).apply { mkdirs() }
        val file = File(folder, name)
        file.writeBytes(bytes)
        val generated = GeneratedFile(id = "o${counter.incrementAndGet()}", name = name, type = type, mimeType = mimeType, path = file.absolutePath)
        turnOutputs.add(generated)
        return generated
    }

    /** The original bytes of an attached file (the form filler edits a copy of them). */
    suspend fun bytesOf(entry: Entry): ByteArray = withContext(Dispatchers.IO) { entry.file.readBytes() }

    // ------------------------------------------------------------------ helpers

    private fun describe(uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "ملف"
        var size = -1L
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        return name to size
    }

    private fun readBytes(uri: Uri): ByteArray {
        val input = context.contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("تعذّر فتح الملف")
        input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_FILE_BYTES) throw IllegalArgumentException("حجم الملف أكبر من الحد المسموح (${MAX_FILE_BYTES / (1024 * 1024)} ميغابايت)")
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        }
    }

    private fun formatSize(bytes: Int): String =
        if (bytes >= 1024 * 1024) String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0)) else "${maxOf(1, bytes / 1024)} KB"

    private companion object {
        const val MAX_FILE_BYTES = 10L * 1024 * 1024
        const val MAX_ATTACHMENTS_PER_CONVERSATION = 6
    }
}
