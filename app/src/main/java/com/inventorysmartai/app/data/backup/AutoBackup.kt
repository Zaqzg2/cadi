package com.inventorysmartai.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** الـ Worker يُنشأ بمُنشئ WorkManager القياسي (لا HiltWorkerFactory)، فيأخذ اعتماداته من هنا. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface BackupEntryPoint {
    fun backupManager(): BackupManager
    fun backupSettings(): BackupSettings
}

/** نسخة تلقائية يومية إلى المجلد الذي اختاره المستخدم (يُبقي آخر 7 نسخ). فشل مؤقت (مجلد غير متاح) يُعاد 3 مرات ثم يُترك لليوم التالي. */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val entry = EntryPointAccessors.fromApplication(applicationContext, BackupEntryPoint::class.java)
        val settings = entry.backupSettings()
        if (!settings.autoEnabled.first()) return Result.success()
        val folder = settings.autoFolderUri.first() ?: return Result.success()
        return try {
            entry.backupManager().writeBackupToFolder(Uri.parse(folder))
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}

object BackupScheduler {
    private const val UNIQUE_NAME = "auto_backup"

    /** يفعّل الجدولة اليومية أو يلغيها. WorkManager يحفظ الجدولة عبر إعادة تشغيل الهاتف والتطبيق، فلا حاجة لإعادتها عند كل بدء. */
    fun apply(context: Context, enabled: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(UNIQUE_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        manager.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
