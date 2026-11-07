package com.inventorysmartai.app.data.backup

import java.io.File

/**
 * الاستعادة تتم على مرحلتين كي لا نستبدل ملف قاعدة مفتوحة وهي تُستعمل:
 *  1) أثناء التشغيل: تُستخرج النسخة وتُتحقق إلى مجلد مؤقت (BackupManager.stageRestore).
 *  2) عند بدء العملية التالية، قبل أن يفتح Room القاعدة: [applyIfPresent] يستبدل ملف القاعدة بالنسخة المجهّزة.
 * فإن أُغلق التطبيق قبل إعادة التشغيل اليدوية تُطبَّق الاستعادة في المرة التالية التي يُفتح فيها.
 */
object PendingRestore {
    const val DIR_NAME = "pending_restore"

    /** true إن طُبِّقت استعادة. آمنة عند التكرار: لا يُحذف المجهَّز إلا بعد نجاح الاستبدال، وإن انقطعت العملية في المنتصف تُكمَل في التشغيل التالي. */
    fun applyIfPresent(databaseFile: File, pendingDir: File): Boolean {
        val staged = File(pendingDir, BackupManifest.ENTRY_DATABASE)
        if (!staged.isFile || staged.length() == 0L) {
            // لا شيء مجهَّز (أو ملف ناقص من محاولة فاشلة): ننظّف ولا نمسّ القاعدة الحالية.
            if (pendingDir.exists()) pendingDir.deleteRecursively()
            return false
        }
        val parent = databaseFile.parentFile ?: return false
        parent.mkdirs()

        // النسخة تُكتب أولًا بجوار القاعدة بامتداد مؤقت، ثم تُحذف القاعدة القديمة، ثم إعادة تسمية (ذرّية داخل المجلد نفسه).
        val temporary = File(parent, databaseFile.name + ".restoring")
        staged.copyTo(temporary, overwrite = true)
        listOf("", "-wal", "-shm", "-journal").forEach { suffix -> File(databaseFile.path + suffix).delete() }
        if (!temporary.renameTo(databaseFile)) {
            temporary.copyTo(databaseFile, overwrite = true)
            temporary.delete()
        }
        pendingDir.deleteRecursively()
        return true
    }
}
