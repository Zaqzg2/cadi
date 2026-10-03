package com.inventorysmartai.app.presentation.common

import android.content.Context
import android.widget.Toast
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Opens Google's code-scanner screen (shipped through Google Play services — no CAMERA permission and no
 * camera code of our own).
 *
 * Every outcome is reported — nothing is swallowed any more:
 *  - [onResult]: a non-blank code was read (already trimmed).
 *  - [onError]: the scanner could not start / failed (message already user-readable, Arabic).
 *  - [onCancel]: the user closed the scanner without scanning (default: nothing).
 *
 * The default [onError] shows a Toast so a caller that forgot to handle errors still gives feedback
 * (InventoryScreen used to pass `{}` and the user saw nothing at all).
 */
fun startBarcodeScan(
    context: Context,
    onResult: (String) -> Unit,
    onError: (String?) -> Unit = { msg -> Toast.makeText(context, msg ?: "تعذّر تشغيل ماسح الباركود", Toast.LENGTH_LONG).show() },
    onCancel: () -> Unit = {}
) {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
        .enableAutoZoom()
        .build()
    try {
        GmsBarcodeScanning.getClient(context, options)
            .startScan()
            .addOnSuccessListener { barcode ->
                val value = barcode.rawValue?.trim().orEmpty()
                if (value.isEmpty()) onError("لم يتم التعرّف على الباركود، حاول مرة أخرى") else onResult(value)
            }
            .addOnCanceledListener { onCancel() }
            .addOnFailureListener { e -> onError(friendlyScanError(e)) }
    } catch (e: Exception) {
        onError(friendlyScanError(e))
    }
}

private fun friendlyScanError(e: Exception): String {
    val raw = e.message.orEmpty()
    return when {
        raw.contains("MlKitException", ignoreCase = true) || raw.contains("module", ignoreCase = true) ->
            "وحدة الماسح غير جاهزة بعد. تأكد من الاتصال بالإنترنت وتحديث خدمات Google Play ثم أعد المحاولة"
        raw.contains("camera", ignoreCase = true) -> "تعذّر الوصول إلى الكاميرا"
        raw.isBlank() -> "تعذّر تشغيل ماسح الباركود"
        else -> "تعذّر تشغيل ماسح الباركود: $raw"
    }
}
