package com.inventorysmartai.app.data.google

import com.inventorysmartai.app.data.local.datastore.DeviceSessionLocalDataSource
import com.inventorysmartai.app.data.remote.BackendApi
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
        GoogleAuthStatus(dto.linked, dto.grantedScopes)
    }

    override suspend fun unlink(): Result<Unit> = safeApiCall(moshi) {
        val sessionId = sessionRepository.getSessionId()
        api.unlinkGoogleAccount(UnlinkGoogleAccountRequest(sessionId))
        Unit
    }
}

@Singleton
class GoogleWorkspaceRepositoryImpl @Inject constructor(
    private val api: BackendApi,
    private val sessionRepository: DeviceSessionRepository,
    private val moshi: Moshi
) : GoogleWorkspaceRepository {

    override suspend fun saveReportToDrive(title: String, reportMarkdown: String, folder: String?): Result<DriveUploadResult> =
        safeApiCall(moshi) {
            val sessionId = sessionRepository.getSessionId()
            val dto = api.saveReportToDrive(DocCreateRequestDto(sessionId, title, reportMarkdown))
            DriveUploadResult(dto.fileId, dto.webViewLink)
        }

    override suspend fun createGoogleDoc(title: String, reportMarkdown: String): Result<DriveUploadResult> = safeApiCall(moshi) {
        val sessionId = sessionRepository.getSessionId()
        val dto = api.createGoogleDoc(DocCreateRequestDto(sessionId, title, reportMarkdown))
        DriveUploadResult(dto.fileId, dto.webViewLink)
    }

    override suspend fun sendEmail(to: String, subject: String, body: String, attachmentDriveFileId: String?): Result<Unit> =
        safeApiCall(moshi) {
            val sessionId = sessionRepository.getSessionId()
            api.sendEmail(SendEmailRequestDto(sessionId, to, subject, body, attachmentDriveFileId))
        }

    override suspend fun createCalendarEvent(title: String, startIso: String, endIso: String, description: String?): Result<CalendarEventResult> =
        safeApiCall(moshi) {
            val sessionId = sessionRepository.getSessionId()
            val dto = api.createCalendarEvent(CalendarEventRequestDto(sessionId, title, startIso, endIso, description))
            CalendarEventResult(dto.eventId, dto.htmlLink)
        }

    override suspend fun exportSheet(sheetName: String, headerRow: List<String>, rows: List<List<String>>): Result<String> =
        safeApiCall(moshi) {
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
    override suspend fun getStatus(verifyGemini: Boolean): Result<GoogleServiceStatus> = safeApiCall(moshi) {
        val sessionId = sessionRepository.getSessionId()
        val dto = api.getServiceStatus(sessionId, verifyGemini)
        GoogleServiceStatus(dto.gemini, dto.drive, dto.sheets, dto.docs, dto.gmail, dto.calendar)
    }
}
