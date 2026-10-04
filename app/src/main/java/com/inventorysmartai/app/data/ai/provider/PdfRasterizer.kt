package com.inventorysmartai.app.data.ai.provider

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/** A page image ready to be sent to a vision model. */
class PreparedImage(val bytes: ByteArray, val mimeType: String)

/**
 * Vision models (Groq, OpenRouter, Mistral chat) take images, not PDFs, and only JPEG/PNG/WebP/GIF —
 * not the HEIC a phone camera may produce. This turns whatever the user picked into 1..N such images.
 * Mistral OCR does NOT need this: it reads PDFs directly.
 */
@Singleton
class PdfRasterizer @Inject constructor(@ApplicationContext private val context: Context) {

    suspend fun prepare(bytes: ByteArray, mimeType: String, maxPages: Int): List<PreparedImage> =
        withContext(Dispatchers.IO) {
            when (mimeType) {
                "application/pdf" -> rasterizePdf(bytes, maxPages)
                "image/jpeg", "image/png", "image/webp" ->
                    listOf(if (bytes.size > LARGE_IMAGE_BYTES) reencode(bytes) else PreparedImage(bytes, mimeType))
                else -> listOf(reencode(bytes)) // HEIC/HEIF and anything else the platform can decode
            }
        }

    private fun rasterizePdf(pdf: ByteArray, maxPages: Int): List<PreparedImage> {
        val file = File.createTempFile("ai_pdf_", ".pdf", context.cacheDir)
        try {
            file.writeBytes(pdf)
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                val renderer = PdfRenderer(descriptor)
                try {
                    return (0 until minOf(renderer.pageCount, maxPages)).map { index ->
                        val page = renderer.openPage(index)
                        try {
                            val scale = TARGET_SIDE / max(page.width, page.height).toFloat()
                            val bitmap = Bitmap.createBitmap(
                                (page.width * scale).toInt().coerceAtLeast(1),
                                (page.height * scale).toInt().coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888
                            )
                            bitmap.eraseColor(Color.WHITE) // PDFs are transparent by default; JPEG would turn that black
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            PreparedImage(bitmap.toJpeg(), "image/jpeg")
                        } finally {
                            page.close()
                        }
                    }
                } finally {
                    renderer.close()
                }
            } finally {
                descriptor.close()
            }
        } finally {
            file.delete()
        }
    }

    private fun reencode(bytes: ByteArray): PreparedImage {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > TARGET_SIDE * 2) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Image could not be decoded")
        return PreparedImage(bitmap.toJpeg(), "image/jpeg")
    }

    private fun Bitmap.toJpeg(): ByteArray {
        val out = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 85, out)
        recycle()
        return out.toByteArray()
    }

    private companion object {
        const val TARGET_SIDE = 1800f
        const val LARGE_IMAGE_BYTES = 3 * 1024 * 1024
    }
}
