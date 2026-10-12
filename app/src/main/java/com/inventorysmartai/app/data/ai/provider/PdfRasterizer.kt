package com.inventorysmartai.app.data.ai.provider

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.os.ParcelFileDescriptor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
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
 * Mistral OCR does NOT need this: it reads PDFs directly. OCR.space does (one small JPEG/PNG per page): see [prepareForOcr].
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

    /**
     * Page images for OCR.space's free plan: at most [maxPages] images, each at most [maxBytes] (a bigger file is refused).
     * A small JPEG/PNG goes through untouched; a PDF becomes one image per page; WebP, HEIC and anything bigger than the limit
     * is re-encoded as JPEG, lowering the quality and then the size until it fits.
     */
    suspend fun prepareForOcr(bytes: ByteArray, mimeType: String, maxPages: Int, maxBytes: Int): List<PreparedImage> =
        withContext(Dispatchers.IO) {
            val pages = when (mimeType) {
                "application/pdf" -> rasterizePdf(bytes, maxPages)
                "image/jpeg", "image/png" -> listOf(PreparedImage(bytes, mimeType))
                else -> listOf(reencode(bytes))
            }
            pages.map { if (it.bytes.size <= maxBytes) it else shrinkToLimit(it.bytes, maxBytes) }
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

    private fun reencode(bytes: ByteArray): PreparedImage = PreparedImage(decodeUpright(bytes).toJpeg(), "image/jpeg")

    /**
     * Decodes (down-sampled when huge) and turns the picture upright. A camera photo is stored sideways with an EXIF "rotate me"
     * tag, which re-encoding drops — so without this a portrait invoice would reach the OCR engine lying on its side.
     */
    private fun decodeUpright(bytes: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > TARGET_SIDE * 2) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Image could not be decoded")
        val degrees = exifRotation(bytes)
        if (degrees == 0) return decoded
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
        if (rotated !== decoded) decoded.recycle()
        return rotated
    }

    private fun exifRotation(bytes: ByteArray): Int = try {
        when (ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (e: Exception) {
        0 // not a JPEG, or no readable tag: leave the picture as it is
    }

    private fun shrinkToLimit(bytes: ByteArray, maxBytes: Int): PreparedImage {
        var bitmap = decodeUpright(bytes)
        var smallest: ByteArray? = null
        repeat(MAX_SHRINK_ROUNDS) {
            for (quality in SHRINK_QUALITIES) {
                val out = bitmap.compressJpeg(quality)
                if (out.size <= maxBytes) {
                    bitmap.recycle()
                    return PreparedImage(out, "image/jpeg")
                }
                if (smallest == null || out.size < smallest!!.size) smallest = out
            }
            // still too big at the lowest quality: take 20% off each side and go round again
            val scaled = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * SHRINK_FACTOR).toInt().coerceAtLeast(1),
                (bitmap.height * SHRINK_FACTOR).toInt().coerceAtLeast(1),
                true
            )
            if (scaled !== bitmap) {
                bitmap.recycle()
                bitmap = scaled
            }
        }
        bitmap.recycle()
        return PreparedImage(smallest ?: bytes, "image/jpeg")
    }

    private fun Bitmap.compressJpeg(quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
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
        const val MAX_SHRINK_ROUNDS = 6
        const val SHRINK_FACTOR = 0.8f
        val SHRINK_QUALITIES = intArrayOf(80, 65, 50)
    }
}
