package com.inventorysmartai.app.data.importing

import com.inventorysmartai.app.domain.importing.OpenedFile
import com.inventorysmartai.app.domain.model.ImportSourceType

/** Step 2 (FileDetector), spec section 2's error-handling list. Deliberately sniffs the file's
 *  actual bytes (its "magic number") rather than trusting the extension or the MIME type SAF
 *  reports — a `.csv` a user renamed from `.txt`, or a content provider that returns a generic
 *  `application/octet-stream` for a perfectly good CSV, are both real and both must still work;
 *  conversely a `.xlsx`-named file that isn't really a zip must not be handed to the Excel
 *  parser and crash there instead of failing cleanly here. */
sealed interface FileDetectionResult {
    data class Recognized(val sourceType: ImportSourceType) : FileDetectionResult
    data object Empty : FileDetectionResult
    data class Unsupported(val reasonAr: String) : FileDetectionResult
    data class Unreadable(val reasonAr: String) : FileDetectionResult
}

object FileDetector {

    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04) // "PK\x03\x04" — .xlsx is a zip
    private val OLE_MAGIC = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte()) // legacy .xls/.doc container

    fun detect(file: OpenedFile): FileDetectionResult {
        if (file.sizeBytes == 0L) return FileDetectionResult.Empty

        val name = file.displayName.lowercase()
        val extension = name.substringAfterLast('.', missingDelimiterValue = "")

        val header = try {
            readHeaderBytes(file, 8)
        } catch (e: Exception) {
            return FileDetectionResult.Unreadable("تعذّرت قراءة الملف: ${e.message ?: e.javaClass.simpleName}")
        }

        if (header.isEmpty()) return FileDetectionResult.Empty

        val isZip = startsWith(header, ZIP_MAGIC)
        val isOle = startsWith(header, OLE_MAGIC)

        return when {
            isZip && extension != "csv" ->
                FileDetectionResult.Recognized(ImportSourceType.EXCEL)

            isOle || extension == "xls" ->
                FileDetectionResult.Unsupported(
                    "صيغة Excel القديمة (.xls) غير مدعومة حاليًا — يرجى حفظ الملف بصيغة xlsx أو تصديره كملف CSV ثم إعادة المحاولة"
                )

            extension == "csv" || file.mimeType?.contains("csv") == true ->
                FileDetectionResult.Recognized(ImportSourceType.CSV)

            isZip ->
                FileDetectionResult.Unsupported("هذا الملف مضغوط بصيغة غير مدعومة (ليس xlsx)")

            looksLikePlainText(header) ->
                // Last resort: an unrecognized extension/MIME but content that reads as plain
                // text is treated as CSV — matches "delimiter detection" already being lenient
                // about what counts as a CSV file.
                FileDetectionResult.Recognized(ImportSourceType.CSV)

            else ->
                FileDetectionResult.Unsupported("صيغة الملف \".$extension\" غير مدعومة — الصيغ المدعومة حاليًا: xlsx وCSV")
        }
    }

    private fun readHeaderBytes(file: OpenedFile, maxBytes: Int): ByteArray =
        file.inputStream().use { stream ->
            val buffer = ByteArray(maxBytes)
            var total = 0
            while (total < buffer.size) {
                val read = stream.read(buffer, total, buffer.size - total)
                if (read <= 0) break
                total += read
            }
            buffer.copyOf(total)
        }

    private fun startsWith(bytes: ByteArray, magic: ByteArray): Boolean =
        bytes.size >= magic.size && magic.indices.all { bytes[it] == magic[it] }

    /** No NUL bytes in the sample is a cheap, effective enough heuristic for "this is text, not
     *  an arbitrary binary format" — real CSV/TSV exports never contain NUL bytes. */
    private fun looksLikePlainText(bytes: ByteArray): Boolean = bytes.none { it == 0.toByte() }
}
