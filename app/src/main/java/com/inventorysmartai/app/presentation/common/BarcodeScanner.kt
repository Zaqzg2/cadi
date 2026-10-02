package com.inventorysmartai.app.presentation.common

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Opens Google's code-scanner screen (shipped through Google Play services — no CAMERA permission and no
 * camera code of our own). Exactly one of [onResult] / [onError] is called; cancelling calls neither.
 */
fun startBarcodeScan(context: Context, onResult: (String) -> Unit, onError: (String?) -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
        .enableAutoZoom()
        .build()
    GmsBarcodeScanning.getClient(context, options)
        .startScan()
        .addOnSuccessListener { barcode -> barcode.rawValue?.let(onResult) ?: onError(null) }
        .addOnFailureListener { e -> onError(e.message) }
}
