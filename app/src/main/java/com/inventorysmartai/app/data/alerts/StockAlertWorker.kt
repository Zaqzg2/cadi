package com.inventorysmartai.app.data.alerts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.inventorysmartai.app.R
import com.inventorysmartai.app.domain.alerts.StockAlertCounts
import com.inventorysmartai.app.domain.alerts.StockAlertMessage
import com.inventorysmartai.app.domain.alerts.StockAlertMessageBuilder
import com.inventorysmartai.app.domain.alerts.StockAlertSchedule
import com.inventorysmartai.app.domain.repository.ProductRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@EntryPoint
@InstallIn(SingletonComponent::class)
interface StockAlertEntryPoint {
    fun stockAlertSettings(): StockAlertSettings
    fun stockAlertChecker(): StockAlertChecker
}

/** يحسب أعداد الأصناف حسب حالتها الآن (بنفس عتبات الإعدادات التي تعتمدها لوحة التحكم) ويرسل الإشعار إن وُجد ما يستدعيه. */
@Singleton
class StockAlertChecker @Inject constructor(private val productRepository: ProductRepository) {

    /** يعيد الرسالة التي أُرسلت، أو null إن لم يكن هناك ما يستدعي التنبيه أو كانت الإشعارات معطّلة في النظام. */
    suspend fun checkAndNotify(context: Context): StockAlertMessage? {
        val statuses = productRepository.observeProductsWithStock().first().map { it.status }
        val message = StockAlertMessageBuilder.build(StockAlertCounts.from(statuses)) ?: return null
        return if (StockAlertNotifier.show(context, message)) message else null
    }
}

/** يعمل مرة في اليوم (الساعة التي يختارها المستخدم تقريبًا: WorkManager لا يضمن الدقة). */
class StockAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val entry = EntryPointAccessors.fromApplication(applicationContext, StockAlertEntryPoint::class.java)
        if (!entry.stockAlertSettings().enabled.first()) return Result.success()
        return try {
            entry.stockAlertChecker().checkAndNotify(applicationContext)
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

object StockAlertScheduler {
    private const val UNIQUE_NAME = "stock_alerts"

    /** يفعّل الجدولة اليومية عند [hour] أو يلغيها. UPDATE يعيد ضبط الموعد إن غيّر المستخدم الساعة دون أن يضيع الدور الجاري. */
    fun apply(context: Context, enabled: Boolean, hour: Int) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(UNIQUE_NAME)
            return
        }
        val delay = StockAlertSchedule.delayMillisUntilNext(hour, ZonedDateTime.now())
        val request = PeriodicWorkRequestBuilder<StockAlertWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        manager.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

object StockAlertNotifier {
    const val CHANNEL_ID = "stock_alerts"
    private const val NOTIFICATION_ID = 1001

    /** ينشئ قناة الإشعارات (مطلوبة من أندرويد 8). آمن عند التكرار. */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "تنبيهات المخزون", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "تنبيه يومي بالأصناف المنتهية أو القريبة من الانتهاء أو النافدة أو المنخفضة"
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** true إن أُرسل الإشعار؛ false إن كانت الإشعارات معطّلة (لم يُمنح الإذن في أندرويد 13+ أو أُغلقت من النظام). */
    fun show(context: Context, message: StockAlertMessage): Boolean {
        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false

        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) }
        val open = launch?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_stock)
            .setContentTitle(message.title)
            .setContentText(message.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        return try {
            manager.notify(NOTIFICATION_ID, notification)
            true
        } catch (e: SecurityException) {
            false
        }
    }
}
