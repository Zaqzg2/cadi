package com.inventorysmartai.app.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.DocumentsContract
import androidx.sqlite.db.SupportSQLiteDatabase
import com.inventorysmartai.app.BuildConfig
import com.inventorysmartai.app.data.local.database.InventorySmartDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed interface InspectResult {
    data class Valid(val manifest: BackupManifest) : InspectResult
    data class Rejected(val reason: String) : InspectResult
}

sealed interface StageResult {
    data class Staged(val manifest: BackupManifest) : StageResult
    data class Rejected(val reason: String) : StageResult
}

/** نسخة أمان أنشأها التطبيق تلقائيًا قبل استبدال بياناتك بنسخة أخرى. */
data class SafetyBackup(val fileName: String, val createdAt: Long, val sizeBytes: Long)

/**
 * النسخ الاحتياطي والاستعادة لقاعدة بيانات التطبيق (مصدر الحقيقة الوحيد لكل بيانات المخزون).
 *
 * - النسخ: أرشيف zip فيه بيان + ملف القاعدة. أثناء النسخ يُمسك قفل الكتابة (معاملة فورية) فلا يتغيّر الملف في أثناء نسخه.
 * - الاستعادة على مرحلتين: [stageRestore] يتحقق من النسخة بعمق (البيان، إصدار المخطط، سلامة القاعدة) ويجهّزها، ثم تُستبدل القاعدة عند
 *   بدء العملية التالية قبل أن يفتحها Room ([PendingRestore]). قبل التجهيز تُحفظ نسخة أمان من بياناتك الحالية.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: InventorySmartDatabase,
    private val settings: BackupSettings
) {
    private val dbFile: File get() = context.getDatabasePath(InventorySmartDatabase.DATABASE_NAME)
    private val safetyDir: File get() = File(context.filesDir, SAFETY_DIR)

    /** يكتب نسخة كاملة إلى [output] (ويغلقه). */
    suspend fun writeBackup(output: OutputStream): BackupManifest = withContext(Dispatchers.IO) {
        val db = database.openHelper.writableDatabase
        // دمج سجل WAL في ملف القاعدة إن أمكن (إن منعه قارئ نشط يُشحن ملف WAL داخل الأرشيف فلا يضيع شيء).
        try {
            db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        } catch (e: Exception) {
            // غير حرج
        }
        db.beginTransaction()
        try {
            // هنا قفل الكتابة ممسوك: لا يستطيع أحد تغيير الملف أو دمج WAL فيه أثناء نسخه.
            val manifest = BackupManifest(
                dbVersion = db.version,
                appVersion = BuildConfig.VERSION_NAME,
                createdAt = System.currentTimeMillis(),
                productCount = count(db, "products"),
                invoiceCount = count(db, "sales_invoices")
            )
            BackupArchive.write(output, manifest, dbFile, File(dbFile.path + "-wal"))
            manifest
        } finally {
            db.endTransaction()
        }
    }

    /** يقرأ بيان النسخة ويتحقق منها دون كتابة أي شيء: للمعاينة قبل أن يؤكد المستخدم الاستعادة. */
    suspend fun inspect(input: InputStream): InspectResult = withContext(Dispatchers.IO) {
        val info = try {
            BackupArchive.inspect(input)
        } catch (e: IOException) {
            return@withContext InspectResult.Rejected("تعذّر قراءة الملف: ${e.message}")
        }
        val check = BackupRules.check(info.manifest, info.hasDatabase, currentSchemaVersion())
        val manifest = info.manifest
        when {
            check is RestoreCheck.Rejected -> InspectResult.Rejected(check.reason)
            manifest == null -> InspectResult.Rejected("هذا الملف ليس نسخة احتياطية من هذا التطبيق.")
            else -> InspectResult.Valid(manifest)
        }
    }

    /** يجهّز الاستعادة (لا يمسّ بياناتك الحالية بعد): استخراج + تحقق عميق + نسخة أمان من الحالي. */
    suspend fun stageRestore(input: InputStream): StageResult = withContext(Dispatchers.IO) {
        val pending = File(context.filesDir, PendingRestore.DIR_NAME)
        pending.deleteRecursively()
        val extracted = try {
            BackupArchive.extract(input, pending)
        } catch (e: IOException) {
            pending.deleteRecursively()
            return@withContext StageResult.Rejected("تعذّر قراءة الملف: ${e.message}")
        }

        val version = currentSchemaVersion()
        val manifest = extracted.manifest
        val stagedDb = extracted.database
        val check = BackupRules.check(manifest, stagedDb != null, version)
        if (check is RestoreCheck.Rejected) {
            pending.deleteRecursively()
            return@withContext StageResult.Rejected(check.reason)
        }
        if (manifest == null || stagedDb == null) {
            pending.deleteRecursively()
            return@withContext StageResult.Rejected("النسخة غير صالحة.")
        }

        val problem = validateDatabase(stagedDb, version)
        if (problem != null) {
            pending.deleteRecursively()
            return@withContext StageResult.Rejected(problem)
        }
        // بعد التحقق دُمج WAL في الملف نفسه؛ لا نحتاج الملفات الجانبية.
        File(stagedDb.path + "-wal").delete()
        File(stagedDb.path + "-shm").delete()

        // آخر خطوة قبل أن يصبح الاستبدال حتميًا: نسخة أمان من البيانات الحالية.
        try {
            writeSafetyBackup()
        } catch (e: Exception) {
            pending.deleteRecursively()
            return@withContext StageResult.Rejected("تعذّر حفظ نسخة أمان من بياناتك الحالية، فأُلغيت الاستعادة: ${e.message}")
        }
        StageResult.Staged(manifest)
    }

    // ------------------------------------------------------------------ safety copies

    private suspend fun writeSafetyBackup() {
        safetyDir.mkdirs()
        val file = File(safetyDir, "$SAFETY_PREFIX${stamp()}.zip")
        file.outputStream().use { writeBackup(it) }
        safetyDir.listFiles()
            ?.filter { it.name.startsWith(SAFETY_PREFIX) }
            ?.sortedByDescending { it.name }
            ?.drop(KEEP_SAFETY_BACKUPS)
            ?.forEach { it.delete() }
    }

    suspend fun listSafetyBackups(): List<SafetyBackup> = withContext(Dispatchers.IO) {
        safetyDir.listFiles()
            ?.filter { it.isFile && it.name.startsWith(SAFETY_PREFIX) }
            ?.sortedByDescending { it.name }
            ?.map { SafetyBackup(it.name, it.lastModified(), it.length()) }
            .orEmpty()
    }

    /** مدخل نسخة أمان محفوظة باسمها (للمعاينة أو الاستعادة)، أو null إن لم تعد موجودة. */
    fun openSafetyBackup(fileName: String): InputStream? {
        // اسم يُقبل كما أنشأناه فقط: لا فواصل مسارات.
        if (!fileName.startsWith(SAFETY_PREFIX) || fileName.contains('/') || fileName.contains('\\')) return null
        val file = File(safetyDir, fileName)
        return if (file.isFile) file.inputStream() else null
    }

    // ------------------------------------------------------------------ sharing + automatic folder backup

    /** نسخة في ذاكرة التخزين المؤقت لمشاركتها (واتساب، Drive، بريد...). تُحذف النسخ المؤقتة القديمة أولًا. */
    suspend fun createShareFile(): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "$AUTO_PREFIX${stamp()}.zip")
        file.outputStream().use { writeBackup(it) }
        settings.setLastBackupAt(System.currentTimeMillis())
        file
    }

    /** يكتب نسخة في المجلد المختار (شجرة SAF) ويُبقي أحدث [keep] نسخة تلقائية فقط. */
    suspend fun writeBackupToFolder(treeUri: Uri, keep: Int = KEEP_AUTO_BACKUPS) = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        val document = DocumentsContract.createDocument(resolver, parent, "application/zip", "$AUTO_PREFIX${stamp()}.zip")
            ?: throw IOException("تعذّر إنشاء الملف في المجلد المختار. اختر المجلد من جديد.")
        val stream = resolver.openOutputStream(document) ?: throw IOException("تعذّر فتح الملف للكتابة في المجلد المختار.")
        stream.use { writeBackup(it) }
        settings.setLastBackupAt(System.currentTimeMillis())
        pruneOldBackups(treeUri, keep)
    }

    private fun pruneOldBackups(treeUri: Uri, keep: Int) {
        val resolver = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        val found = mutableListOf<Pair<String, String>>() // اسم العرض إلى معرّف المستند
        try {
            resolver.query(
                children,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: continue
                    if (name.startsWith(AUTO_PREFIX) && name.endsWith(".zip")) found += name to id
                }
            }
        } catch (e: Exception) {
            return // التنظيف اختياري: فشله لا يُفشل النسخة التي كُتبت للتو
        }
        found.sortedByDescending { it.first }.drop(keep).forEach { (_, id) ->
            try {
                DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(treeUri, id))
            } catch (e: Exception) {
                // يُترك للمرة القادمة
            }
        }
    }

    // ------------------------------------------------------------------ internals

    private fun currentSchemaVersion(): Int = database.openHelper.readableDatabase.version

    private fun count(db: SupportSQLiteDatabase, table: String): Int =
        try {
            db.query("SELECT COUNT(*) FROM $table").use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
        } catch (e: Exception) {
            0
        }

    /** يفتح القاعدة المجهَّزة بالإطار نفسه ويفحصها: الإصدار، سلامة الملف، ووجود جدول الأصناف. null = سليمة، وإلا سبب الرفض. */
    private fun validateDatabase(file: File, expectedVersion: Int): String? {
        val db = try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE)
        } catch (e: Exception) {
            return "تعذّر فتح قاعدة البيانات داخل النسخة: ${e.message}"
        }
        try {
            if (db.version != expectedVersion) {
                return "إصدار قاعدة البيانات داخل النسخة (${db.version}) لا يطابق إصدار التطبيق الحالي ($expectedVersion)."
            }
            val integrity = db.rawQuery("PRAGMA integrity_check", null).use { c -> if (c.moveToFirst()) c.getString(0) else null }
            if (integrity != "ok") return "فحص سلامة قاعدة البيانات فشل: النسخة تالفة."
            db.rawQuery("SELECT COUNT(*) FROM products", null).use { it.moveToFirst() }
            return null
        } catch (e: Exception) {
            return "النسخة لا تحتوي بنية قاعدة البيانات المتوقعة: ${e.message}"
        } finally {
            db.close()
        }
    }

    private fun stamp(): String = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(Date())

    private companion object {
        const val SAFETY_DIR = "safety_backups"
        const val SAFETY_PREFIX = "safety-"
        const val SHARE_DIR = "backups"
        const val AUTO_PREFIX = "inventory-backup-"
        const val KEEP_SAFETY_BACKUPS = 3
        const val KEEP_AUTO_BACKUPS = 7
    }
}
