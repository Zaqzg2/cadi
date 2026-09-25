package com.inventorysmartai.app.data.importing

import com.inventorysmartai.app.data.remote.BackendApi
import com.inventorysmartai.app.data.remote.safeApiCall
import com.inventorysmartai.app.domain.importing.ai.AiExtractionDocument
import com.inventorysmartai.app.domain.importing.ai.AiExtractionDocumentType
import com.inventorysmartai.app.domain.repository.AiDocumentImportRepository
import com.squareup.moshi.Moshi
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiDocumentImportRepositoryImpl @Inject constructor(
    private val api: BackendApi,
    private val moshi: Moshi
) : AiDocumentImportRepository {

    override suspend fun extract(
        fileBytes: ByteArray,
        mimeType: String,
        documentType: AiExtractionDocumentType,
        sessionId: String
    ): Result<AiExtractionDocument> = safeApiCall(moshi) {
        val filePart = MultipartBody.Part.createFormData(
            "file",
            "document",
            fileBytes.toRequestBody(mimeType.toMediaTypeOrNull())
        )
        val documentTypePart = documentType.name.toRequestBody("text/plain".toMediaTypeOrNull())
        val sessionIdPart = sessionId.toRequestBody("text/plain".toMediaTypeOrNull())

        api.extractDocument(filePart, documentTypePart, sessionIdPart).result
    }
}
