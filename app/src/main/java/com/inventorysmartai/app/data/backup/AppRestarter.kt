package com.inventorysmartai.app.data.backup

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Process
import kotlin.system.exitProcess

/**
 * يعيد تشغيل التطبيق: يطلق شاشة البدء في مهمة جديدة ثم ينهي العملية الحالية بعد لحظة ليصل الطلب إلى النظام قبل الإنهاء.
 * مطلوب لإكمال الاستعادة (استبدال القاعدة يحدث عند بدء العملية التالية قبل أن يفتحها Room). حتى لو فشلت إعادة التشغيل نفسها،
 * تُطبَّق الاستعادة في أول مرة يُفتح فيها التطبيق بعدها.
 */
object AppRestarter {
    fun restart(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) }
        if (launch != null) context.startActivity(launch)
        Handler(Looper.getMainLooper()).postDelayed({
            Process.killProcess(Process.myPid())
            exitProcess(0)
        }, 250)
    }
}
