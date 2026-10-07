package com.inventorysmartai.app.data.google

import com.inventorysmartai.app.data.backend.BackendCredentials
import com.inventorysmartai.app.data.local.datastore.DeviceSessionLocalDataSource
import com.inventorysmartai.app.data.remote.BackendApi
import com.inventorysmartai.app.data.remote.BackendFailure
import com.inventorysmartai.app.data.remote.dto.CalendarEventRequestDto
import com.inventorysmartai.app.data.remote.dto.DocCreateRequestDto
import com.inventorysmartai.app.data.remote.dto.LinkGoogleAccountRequest
import com.inventorysmartai.app.data.remote.dto.SendEmailRequestDto
import com.inventorysmartai.app.data.remote.dto.SessionIdRequest
import com.inventorysmartai.app.data.remote.dto.SheetsExportRequestDto
import com.inventorysmartai.app.data.remote.dto.UnlinkGoogleAccountRequest
import com.inventorysmartai.app.data.remote.safeApiCall
import com.inventorysmartai.app.domain.repository.CalendarEventResult
import com.inventorysmartai.app.domain.repository.DeviceSessionRepository
import com.inventorysmartai.app.domain.repository.DriveUploadResult
import com.inventorysmartai.app.domain.repository.GoogleAuthRepository
import com.inventorysmartai.app.domain.repository.GoogleAuthStatus
import com.inventorysmartai.app.domain.repository.GoogleServiceStatus
import com.inventorysmartai.app.domain.repository.GoogleServiceStatusRepository
import com.inventorysmartai.app.domain.repository.GoogleWorkspaceRepository
import com.squareup.moshi.Moshi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceSessionRepositoryImpl @Inject constructor(
    private val localDataSource: DeviceSessionLocalDataSource
) : DeviceSessionRepository {
    override suspend fun getSessionId(): String = localDataSource.getOrCreateSessionId()
}

@Singleton
class GoogleAuthRepositoryImpl @Inject constructor(
    private val api: BackendApi,
    private val sessionRepository: DeviceSessionRepository,
    private val credentials: BackendCredentials,
    private val moshi: Moshi
) : GoogleAuthRepository {

    override suspend fun getStatus(): Result<GoogleAuthStatus> = safeApiCall(moshi) {
        val sessionId = sessionRepository.getSessionId()
        val dto = api.googleAuthStatus(sessionId)
        GoogleAuthStatus(dto.linked, dto.grantedScopes)
    }

    override suspend fun completeLinking(serverAuthCode: String): Result<GoogleAuthStatus> = safeApiCall(moshi) {
        val sessionId = sessionRepository.getSessionId()
        val dto = api.linkGoogleAccount(LinkGoogleAccountRequest(sessionId, serverAuthCode))
        // The server keeps nothing: the sealed token it returns IS the link. Without storing it there is no link.
        val token = dto.linkToken ?: throw BackendFailure.Structured("GOOGLE_LINK_TOKEN_MISSING", "لم يُرجع الخادم رمز الربط. تأكد أن الخادم محدّث ثم أعد المحاولة.")
        credentials.saveGoogleLinkToken(token)
        GoogleAuthStatus(dto.linked, dto.grantedScopes)
    }

    override suspend fun unlink(): Result<Unit> = safeApiCall(moshi) {
        val sessionId = sessionRepository.getSessionId()
        try {
            api.unlinkGoogleAccount(UnlinkGoogleAccountRequest(sessionId))
        } finally {
            // Forget the link on this phone whether or not the server could be reached to revoke it at Google.
            credentials.clearGoogleLinkToken()
        }
        Unit
    }
}

@Singleton
class GoogleWorkspaceRepositoryImpl @Inject constructor(
    private val api: BackendApi,
    private val sessionRepository: DeviceSessionRepository,
    private val credentials: BackendCredentials,
    private val moshi: Moshi
) : GoogleWorkspaceRepository {

    /** Like [safeApiCall], plus: if Google says the grant is gone (revoked / expired), forget it so the status screen shows "غير متصل". */
    private suspend fun <T> call(block: suspend () -> T): Result<T> {
        val result = safeApiCall(moshi, block)
        val failure = result.exceptionOrNull()
        if (failure is BackendFailure.Structured && failure.code == "GOOGLE_AUTH_REFRESH_FAILED") credentials.clearGoogleLinkToken()
        return result
    }

    override suspend fun saveReportToDrive(title: String, reportMarkdown: String, folder: String?): Result<DriveUploadResult> =
        call {
            val sessionId = sessionRepository.getSessionId()
            val dto = api.saveReportToDrive(DocCreateRequestDto(sessionId, title, reportMarkdown))
            DriveUploadResult(dto.fileId, dto.webViewLink)
        }

    override suspend fun createGoogleDoc(title: String, reportMarkdown: String): Result<DriveUploadResult> = call {
        val sessionId = sessionRepository.getSessionId()
        val dto = api.createGoogleDoc(DocCreateRequestDto(sessionId, title, reportMarkdown))
        DriveUploadResult(dto.fileId, dto.webViewLink)
    }

    override suspend fun sendEmail(to: String, subject: String, body: String, attachmentDriveFileId: String?): Result<Unit> =
        call {
            val sessionId = sessionRepository.getSessionId()
            api.sendEmail(SendEmailRequestDto(sessionId, to, subject, body, attachmentDriveFileId))
        }

    override suspend fun createCalendarEvent(title: String, startIso: String, endIso: String, description: String?): Result<CalendarEventResult> =
        call {
            val sessionId = sessionRepository.getSessionId()
            val dto = api.createCalendarEvent(CalendarEventRequestDto(sessionId, title, startIso, endIso, description))
            CalendarEventResult(dto.eventId, dto.htmlLink)
        }

    override suspend fun exportSheet(sheetName: String, headerRow: List<String>, rows: List<List<String>>): Result<String> =
        call {
            val sessionId = sessionRepository.getSessionId()
            api.ensureMasterSpreadsheet(SessionIdRequest(sessionId))
            api.exportSheet(SheetsExportRequestDto(sessionId, sheetName, headerRow, rows)).spreadsheetId
        }
}

@Singleton
class GoogleServiceStatusRepositoryImpl @Inject constructor(
    private val api: BackendApi,
    private val sessionRepository: DeviceSessionRepository,
    private val moshi: Moshi
) : GoogleServiceStatusRepository {
    override suspend fun getStatus(verifyAi: Boolean): Result<GoogleServiceStatus> = safeApiCall(moshi) {
        val sessionId = sessionRepository.getSessionId()
        val dto = api.getServiceStatus(sessionId, verifyAi)
        GoogleServiceStatus(dto.ai, dto.aiProviders, dto.googleConfigured, dto.drive, dto.sheets, dto.docs, dto.gmail, dto.calendar)
    }
}
