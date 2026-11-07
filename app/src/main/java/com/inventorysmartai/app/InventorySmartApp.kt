package com.inventorysmartai.app

import android.app.Application
import android.content.Context
import com.inventorysmartai.app.data.backend.BackendWarmUp
import com.inventorysmartai.app.data.backup.PendingRestore
import com.inventorysmartai.app.data.local.database.InventorySmartDatabase
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class InventorySmartApp : Application() {

    @Inject
    lateinit var backendWarmUp: BackendWarmUp

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // استعادة جُهّزت في التشغيل السابق تستبدل ملف القاعدة الآن، قبل أي شيء آخر: Room لم يفتحها بعد (يفتحها عند أول حقن).
        // إن فشل الاستبدال لأي سبب يبقى ملف القاعدة الحالي كما هو (لا يُحذف إلا بعد نسخ المجهَّز بجواره).
        try {
            PendingRestore.applyIfPresent(
                databaseFile = base.getDatabasePath(InventorySmartDatabase.DATABASE_NAME),
                pendingDir = File(base.filesDir, PendingRestore.DIR_NAME)
            )
        } catch (e: Exception) {
            // لا نمنع إقلاع التطبيق بسبب استعادة فاشلة
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Free hosting puts an idle server to sleep; waking it takes up to a minute. Pinging it now, in the
        // background, means it is usually awake by the time the user opens the assistant or imports a document.
        backendWarmUp.start()
    }
}
