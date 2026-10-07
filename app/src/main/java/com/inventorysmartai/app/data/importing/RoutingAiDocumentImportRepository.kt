package com.inventorysmartai.app.data.importing

import com.inventorysmartai.app.data.ai.provider.AiProviderSettings
import com.inventorysmartai.app.data.backend.BackendConfig
import com.inventorysmartai.app.data.backend.isBackendOutage
import com.inventorysmartai.app.domain.importing.ai.AiExtractionDocument
import com.inventorysmartai.app.domain.importing.ai.AiExtractionDocumentType
import com.inventorysmartai.app.domain.repository.AiDocumentImportRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the rest of the app talks to — the BACKEND is the default route:
 *  1. "use my own keys" is on AND at least one key is saved  -> direct providers.
 *  2. Otherwise -> the backend.
 *  3. If the backend is not set up in this build, or it is down / unreachable / out of quota, and the person DID save keys of
 *     their own -> those keys get one try. Whatever the person configured last is the error shown when both fail.
 */
@Singleton
class RoutingAiDocumentImportRepository @Inject constructor(
    private val backend: BackendAiDocumentImportRepositoryImpl,
    private val direct: DirectAiDocumentImportRepositoryImpl,
    private val settings: AiProviderSettings
) : AiDocumentImportRepository {

    override suspend fun extract(
        fileBytes: ByteArray,
        mimeType: String,
        documentType: AiExtractionDocumentType,
        sessionId: String
    ): Result<AiExtractionDocument> {
        val hasOwnKeys = settings.hasAnyActive()
        if (hasOwnKeys && settings.current().preferDirect) return direct.extract(fileBytes, mimeType, documentType, sessionId)
        if (hasOwnKeys && BackendConfig.configurationProblem() != null) return direct.extract(fileBytes, mimeType, documentType, sessionId)

        val viaBackend = backend.extract(fileBytes, mimeType, documentType, sessionId)
        if (viaBackend.isSuccess || !hasOwnKeys) return viaBackend

        val failure = viaBackend.exceptionOrNull()
        if (failure == null || !failure.isBackendOutage()) return viaBackend

        val viaDirect = direct.extract(fileBytes, mimeType, documentType, sessionId)
        return if (viaDirect.isSuccess) viaDirect else viaBackend
    }
}
