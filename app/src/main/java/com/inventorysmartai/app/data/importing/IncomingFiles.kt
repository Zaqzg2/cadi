package com.inventorysmartai.app.data.importing

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * صندوق وارد للملفات التي يصل بها المستخدم من خارج التطبيق («فتح بواسطة» أو «مشاركة»).
 *
 * الـ Activity تنسخ الملف إلى ذاكرة التطبيق المؤقتة وتضع رابطه هنا؛ [com.inventorysmartai.app.navigation.AppNavHost] يفتح شاشة
 * الاستيراد حين يظهر رابط جديد، و ImportFlowViewModel يسحبه عبر [take] عند إنشائه فيبدأ تحليل الملف بدل أن يطلب من المستخدم
 * اختياره من جديد. السحب يفرغ الصندوق فلا يُستورد الملف مرتين.
 */
@Singleton
class IncomingFileInbox @Inject constructor() {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun offer(uriString: String) {
        _pending.value = uriString
    }

    /** يعيد الرابط المنتظر ويفرغ الصندوق، أو null إن لم يكن هناك شيء. */
    fun take(): String? {
        while (true) {
            val current = _pending.value ?: return null
            if (_pending.compareAndSet(current, null)) return current
        }
    }
}

/** قراءة الملف القادم من Intent ونسخه إلى مجلد التطبيق المؤقت (صلاحية القراءة الممنوحة للـ Activity مؤقتة). */
object IncomingFiles {

    private const val DIR = "incoming"
    private const val MAX_KEPT_FILES = 5

    /** رابط الملف في Intent من نوع VIEW أو SEND أو SEND_MULTIPLE (أول ملف إن تعدّدت)، أو null إن لم يكن الـ Intent ملفًا. */
    fun sourceUri(intent: Intent?): Uri? {
        intent ?: return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND ->
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: firstClipUri(intent.clipData)
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.firstOrNull()
                    ?: firstClipUri(intent.clipData)
            else -> null
        }
    }

    private fun firstClipUri(clip: ClipData?): Uri? =
        if (clip != null && clip.itemCount > 0) clip.getItemAt(0).uri else null

    /**
     * ينسخ [source] إلى cache/incoming ويعيد رابط FileProvider للنسخة، أو null عند الفشل. يُستدعى خارج الخيط الرئيسي.
     * يُبقى آخر [MAX_KEPT_FILES] ملفات فقط كي لا تتراكم الملفات المشتركة في التخزين.
     */
    fun copyToCache(context: Context, source: Uri): Uri? = runCatching {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(MAX_KEPT_FILES - 1)?.forEach { it.delete() }

        val name = safeName(displayName(context, source) ?: source.lastPathSegment)
        val target = File(dir, "${System.currentTimeMillis()}_$name")
        val input = context.contentResolver.openInputStream(source) ?: return null
        input.use { stream -> target.outputStream().use { out -> stream.copyTo(out) } }
        if (target.length() == 0L) {
            target.delete()
            return null
        }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
    }.getOrNull()

    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    /** اسم ملف آمن للكتابة على القرص: بلا فواصل مسارات ولا محارف ممنوعة، وبامتداده إن وُجد. */
    internal fun safeName(raw: String?): String {
        val cleaned = (raw ?: "").substringAfterLast('/')
            .map { c -> if (c < ' ' || c in "\\/:*?\"<>|") '-' else c }
            .joinToString("")
            .trim()
            .trimStart('.')
            .take(80)
        return cleaned.ifEmpty { "ملف" }
    }
}
