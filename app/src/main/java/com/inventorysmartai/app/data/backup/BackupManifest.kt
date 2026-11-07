package com.inventorysmartai.app.data.backup

import java.io.StringReader
import java.util.Properties

/**
 * بيان النسخة الاحتياطية: يُكتب أول عنصر في الأرشيف كي يُقرأ وحده دون استخراج القاعدة (معاينة قبل الاستعادة).
 * صيغة key=value عمدًا (java.util.Properties) فلا يلزم JSON ولا مكتبة، ويبقى التحليل قابلًا للاختبار على JVM.
 */
data class BackupManifest(
    val format: Int = FORMAT,
    /** رقم إصدار مخطط Room وقت النسخ. الاستعادة تُقبل فقط عند تطابقه مع الإصدار الحالي (انظر [BackupRules]). */
    val dbVersion: Int,
    val appVersion: String,
    val createdAt: Long,
    val productCount: Int,
    val invoiceCount: Int
) {
    fun serialize(): String = buildString {
        appendLine("format=$format")
        appendLine("dbVersion=$dbVersion")
        appendLine("appVersion=$appVersion")
        appendLine("createdAt=$createdAt")
        appendLine("productCount=$productCount")
        appendLine("invoiceCount=$invoiceCount")
    }

    companion object {
        const val FORMAT = 1
        const val ENTRY_MANIFEST = "manifest.properties"
        const val ENTRY_DATABASE = "inventory_smart.db"
        const val ENTRY_WAL = "inventory_smart.db-wal"

        /** null إن كان النص ليس بيان نسخة صالحًا (حقل أساسي ناقص أو تالف). */
        fun parse(text: String): BackupManifest? {
            val props = Properties()
            try {
                props.load(StringReader(text))
            } catch (e: Exception) {
                return null
            }
            fun intOf(key: String): Int? = props.getProperty(key)?.trim()?.toIntOrNull()
            fun longOf(key: String): Long? = props.getProperty(key)?.trim()?.toLongOrNull()
            return BackupManifest(
                format = intOf("format") ?: return null,
                dbVersion = intOf("dbVersion") ?: return null,
                appVersion = props.getProperty("appVersion")?.trim().orEmpty(),
                createdAt = longOf("createdAt") ?: return null,
                productCount = intOf("productCount") ?: 0,
                invoiceCount = intOf("invoiceCount") ?: 0
            )
        }
    }
}

sealed interface RestoreCheck {
    data object Ok : RestoreCheck
    data class Rejected(val reason: String) : RestoreCheck
}

/**
 * متى تُرفض الاستعادة. القاعدة تستعمل fallbackToDestructiveMigration: لو دخلت قاعدة بإصدار مخطط مختلف لمسحتها Room بصمت عند أول فتح،
 * فالرفض الصريح هنا أسلم من «استعادة» تنتهي بقاعدة فارغة.
 */
object BackupRules {
    fun check(manifest: BackupManifest?, hasDatabase: Boolean, currentDbVersion: Int): RestoreCheck = when {
        manifest == null -> RestoreCheck.Rejected("هذا الملف ليس نسخة احتياطية من هذا التطبيق (لا يحتوي بيان النسخة).")
        manifest.format != BackupManifest.FORMAT -> RestoreCheck.Rejected("صيغة هذه النسخة غير مدعومة في هذا الإصدار من التطبيق.")
        !hasDatabase -> RestoreCheck.Rejected("النسخة ناقصة: لا تحتوي قاعدة البيانات.")
        manifest.dbVersion != currentDbVersion -> RestoreCheck.Rejected(
            "النسخة من إصدار مختلف لقاعدة البيانات (${manifest.dbVersion}) والتطبيق الحالي يستعمل الإصدار $currentDbVersion. " +
                "حدّث التطبيق إلى الإصدار الذي أنشأها، أو أنشئ نسخة جديدة."
        )
        else -> RestoreCheck.Ok
    }
}
