package com.inventorysmartai.app.data.backup

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * قراءة وكتابة أرشيف النسخة (zip) بلا أي اعتماد على أندرويد، فيُختبر على JVM.
 * الترتيب: البيان أولًا ثم القاعدة ثم ملف WAL إن وُجد. عند الاستخراج لا يُكتب إلا ما نختار اسمه نحن (لا مسارات من داخل الأرشيف)،
 * فلا ثغرة zip-slip، ويُرفض أي عنصر يتجاوز [MAX_ENTRY_BYTES] (أرشيف مضخَّم عمدًا).
 */
object BackupArchive {
    const val MAX_ENTRY_BYTES: Long = 4L * 1024 * 1024 * 1024
    private const val MAX_MANIFEST_BYTES = 64 * 1024

    /** يغلق [out] عند الانتهاء. [wal] اختياري: يُضمَّن إن كان موجودًا وغير فارغ (التغييرات التي لم تُدمج بعد في ملف القاعدة). */
    fun write(out: OutputStream, manifest: BackupManifest, database: File, wal: File?) {
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(BackupManifest.ENTRY_MANIFEST))
            zip.write(manifest.serialize().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            addFile(zip, BackupManifest.ENTRY_DATABASE, database)
            if (wal != null && wal.isFile && wal.length() > 0L) addFile(zip, BackupManifest.ENTRY_WAL, wal)
        }
    }

    /** ما في الأرشيف دون استخراج القاعدة: البيان وأسماء العناصر (للمعاينة والتحقق قبل أي كتابة). */
    data class Inspection(val manifest: BackupManifest?, val entryNames: Set<String>) {
        val hasDatabase: Boolean get() = BackupManifest.ENTRY_DATABASE in entryNames
    }

    fun inspect(input: InputStream): Inspection {
        var manifest: BackupManifest? = null
        val names = LinkedHashSet<String>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                names += entry.name
                if (entry.name == BackupManifest.ENTRY_MANIFEST) {
                    manifest = BackupManifest.parse(readLimited(zip, MAX_MANIFEST_BYTES).toString(Charsets.UTF_8))
                }
                zip.closeEntry()
            }
        }
        return Inspection(manifest, names)
    }

    data class Extracted(val manifest: BackupManifest?, val database: File?, val wal: File?)

    /** يستخرج البيان والقاعدة (وWAL) إلى [targetDir] بأسماء ثابتة. أي عنصر آخر يُتجاهل. */
    fun extract(input: InputStream, targetDir: File): Extracted {
        targetDir.mkdirs()
        var manifest: BackupManifest? = null
        var database: File? = null
        var wal: File? = null
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when (entry.name) {
                    BackupManifest.ENTRY_MANIFEST ->
                        manifest = BackupManifest.parse(readLimited(zip, MAX_MANIFEST_BYTES).toString(Charsets.UTF_8))
                    BackupManifest.ENTRY_DATABASE -> database = copyLimited(zip, File(targetDir, BackupManifest.ENTRY_DATABASE))
                    BackupManifest.ENTRY_WAL -> wal = copyLimited(zip, File(targetDir, BackupManifest.ENTRY_WAL))
                    else -> Unit
                }
                zip.closeEntry()
            }
        }
        return Extracted(manifest, database, wal)
    }

    private fun addFile(zip: ZipOutputStream, name: String, file: File) {
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun readLimited(input: InputStream, max: Int): ByteArray {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(4096)
        while (true) {
            val read = input.read(chunk)
            if (read < 0) break
            buffer.write(chunk, 0, read)
            if (buffer.size() > max) throw IOException("عنصر البيان أكبر من المعقول")
        }
        return buffer.toByteArray()
    }

    private fun copyLimited(input: InputStream, target: File): File {
        var total = 0L
        target.outputStream().use { out ->
            val chunk = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(chunk)
                if (read < 0) break
                total += read
                if (total > MAX_ENTRY_BYTES) {
                    out.close()
                    target.delete()
                    throw IOException("حجم العنصر أكبر من المسموح")
                }
                out.write(chunk, 0, read)
            }
        }
        return target
    }
}
