package com.inventorysmartai.app.data.importing

import com.inventorysmartai.app.data.ai.provider.AiProviderSettings
import com.inventorysmartai.app.domain.importing.ai.AiExtractionDocument
import com.inventorysmartai.app.domain.importing.ai.AiExtractionDocumentType
import com.inventorysmartai.app.domain.repository.AiDocumentImportRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the rest of the app talks to. While Firebase AI Logic still exists it stays as the safety net:
 *  - no provider key saved  -> Firebase (exactly the old behaviour);
 *  - keys saved             -> the direct providers first; if ALL of them fail, Gemini through Firebase gets one try.
 * The direct path's error is the one shown when both fail, since that is the path the user configured.
 * Deleting Firebase later means deleting the two marked lines and the `firebase` constructor parameter.
 */
@Singleton
class RoutingAiDocumentImportRepository @Inject constructor(
    private val direct: DirectAiDocumentImportRepositoryImpl,
    private val firebase: FirebaseAiDocumentImportRepositoryImpl, // remove with Firebase
    private val settings: AiProviderSettings
) : AiDocumentImportRepository {

    override suspend fun extract(
        fileBytes: ByteArray,
        mimeType: String,
        documentType: AiExtractionDocumentType,
        sessionId: String
    ): Result<AiExtractionDocument> {
        if (!settings.hasAnyActive()) return firebase.extract(fileBytes, mimeType, documentType, sessionId) // remove with Firebase

        val viaProviders = direct.extract(fileBytes, mimeType, documentType, sessionId)
        if (viaProviders.isSuccess) return viaProviders

        val viaFirebase = firebase.extract(fileBytes, mimeType, documentType, sessionId) // remove with Firebase
        return if (viaFirebase.isSuccess) viaFirebase else viaProviders // remove with Firebase
    }
}
