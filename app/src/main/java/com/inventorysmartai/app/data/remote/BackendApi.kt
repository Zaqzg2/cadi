package com.inventorysmartai.app.data.remote

import com.inventorysmartai.app.data.remote.dto.AssistantTurnResponseDto
import com.inventorysmartai.app.data.remote.dto.CalendarEventRequestDto
import com.inventorysmartai.app.data.remote.dto.CalendarEventResultDto
import com.inventorysmartai.app.data.remote.dto.ContinueRequest
import com.inventorysmartai.app.data.remote.dto.DocCreateRequestDto
import com.inventorysmartai.app.data.remote.dto.DocumentExtractionResponseDto
import com.inventorysmartai.app.data.remote.dto.DriveUploadResultDto
import com.inventorysmartai.app.data.remote.dto.ExecuteBackendToolRequest
import com.inventorysmartai.app.data.remote.dto.GoogleAuthStatusDto
import com.inventorysmartai.app.data.remote.dto.LinkGoogleAccountRequest
import com.inventorysmartai.app.data.remote.dto.SendEmailRequestDto
import com.inventorysmartai.app.data.remote.dto.SendMessageRequest
import com.inventorysmartai.app.data.remote.dto.ServiceStatusDto
import com.inventorysmartai.app.data.remote.dto.SessionIdRequest
import com.inventorysmartai.app.data.remote.dto.SheetsExportRequestDto
import com.inventorysmartai.app.data.remote.dto.SpreadsheetIdDto
import com.inventorysmartai.app.data.remote.dto.UnlinkGoogleAccountRequest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

/**
 * One method per backend route (see backend/README.md's "API surface" table for the full
 * contract). Every write action here (sendEmail, createCalendarEvent, ...) is called by the app
 * only after the user has already confirmed it — this interface has no opinion on that, it is
 * pure transport.
 */
interface BackendApi {
    @POST("v1/assistant/message")
    suspend fun sendAssistantMessage(@Body request: SendMessageRequest): AssistantTurnResponseDto

    @POST("v1/assistant/continue")
    suspend fun continueAssistant(@Body request: ContinueRequest): AssistantTurnResponseDto

    @POST("v1/assistant/executeBackendTool")
    suspend fun executeBackendTool(@Body request: ExecuteBackendToolRequest): AssistantTurnResponseDto

    @Multipart
    @POST("v1/documents/extract")
    suspend fun extractDocument(
        @Part file: MultipartBody.Part,
        @Part("documentType") documentType: RequestBody,
        @Part("sessionId") sessionId: RequestBody
    ): DocumentExtractionResponseDto

    @POST("v1/auth/google/link")
    suspend fun linkGoogleAccount(@Body request: LinkGoogleAccountRequest): GoogleAuthStatusDto

    @GET("v1/auth/google/status")
    suspend fun googleAuthStatus(@Query("sessionId") sessionId: String): GoogleAuthStatusDto

    @POST("v1/auth/google/unlink")
    suspend fun unlinkGoogleAccount(@Body request: UnlinkGoogleAccountRequest): GoogleAuthStatusDto

    @POST("v1/sheets/ensureMaster")
    suspend fun ensureMasterSpreadsheet(@Body request: SessionIdRequest): SpreadsheetIdDto

    @POST("v1/sheets/export")
    suspend fun exportSheet(@Body request: SheetsExportRequestDto): SpreadsheetIdDto

    @POST("v1/drive/saveReport")
    suspend fun saveReportToDrive(@Body request: DocCreateRequestDto): DriveUploadResultDto

    @POST("v1/docs/create")
    suspend fun createGoogleDoc(@Body request: DocCreateRequestDto): DriveUploadResultDto

    @POST("v1/gmail/send")
    suspend fun sendEmail(@Body request: SendEmailRequestDto): Unit

    @POST("v1/calendar/events")
    suspend fun createCalendarEvent(@Body request: CalendarEventRequestDto): CalendarEventResultDto

    @GET("v1/status")
    suspend fun getServiceStatus(@Query("sessionId") sessionId: String, @Query("verifyGemini") verifyGemini: Boolean = false): ServiceStatusDto
}
