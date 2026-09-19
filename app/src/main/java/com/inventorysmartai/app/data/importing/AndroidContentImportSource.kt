package com.inventorysmartai.app.data.importing

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.inventorysmartai.app.domain.importing.ImportSource
import com.inventorysmartai.app.domain.importing.OpenedFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Step 1 (File) of the pipeline: wraps whatever the Storage Access Framework document picker
 * (spec section 2 — "modern Android document picker / Activity Result APIs") returned into the
 * platform-agnostic [OpenedFile] the rest of the pipeline reads. [reference] is the picked
 * document's content:// URI, as a string (Compose state shouldn't hold a raw [Uri] directly).
 */
@Singleton
class AndroidContentImportSource @Inject constructor(
    @ApplicationContext private val context: Context
) : ImportSource {

    override suspend fun open(reference: String): OpenedFile = withContext(Dispatchers.IO) {
        val uri = Uri.parse(reference)
        val resolver = context.contentResolver

        var resolvedName = uri.lastPathSegment ?: "ملف"
        var resolvedSize = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) cursor.getString(nameIndex)?.let { resolvedName = it }
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) resolvedSize = cursor.getLong(sizeIndex)
            }
        }
        if (resolvedSize < 0) {
            // A handful of providers never populate OpenableColumns.SIZE — read the stream once
            // to get a real length rather than showing the user an unknown/zero file size.
            resolvedSize = runCatching {
                resolver.openInputStream(uri)?.use { it.readBytes().size.toLong() } ?: 0L
            }.getOrDefault(0L)
        }
        val resolvedMimeType = resolver.getType(uri)

        object : OpenedFile {
            override val displayName: String = resolvedName
            override val sizeBytes: Long = resolvedSize
            override val mimeType: String? = resolvedMimeType
            override fun inputStream(): InputStream =
                resolver.openInputStream(uri) ?: throw IOException("تعذّر فتح الملف المحدد")
        }
    }
}
