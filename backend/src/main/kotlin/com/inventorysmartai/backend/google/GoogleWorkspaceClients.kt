package com.inventorysmartai.backend.google

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.get
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.util.Base64

/** One error type shared by every Google Workspace client — mirrors the Phase 4 spec's
 *  "Drive failure / Sheets failure / Docs failure / Gmail failure / Calendar failure" error
 *  handling list. [service] and [statusCode] let the route layer log/report which integration
 *  actually failed without five near-identical exception classes. */
class GoogleApiException(val service: String, val statusCode: Int, message: String) : Exception("$service API error ($statusCode): $message")

/** Shared plumbing every Google REST client below needs: a Bearer-token JSON call plus one place
 *  that turns a non-2xx response into [GoogleApiException]. Each real client stays a thin, focused
 *  wrapper over the handful of endpoints Phase 4 actually needs — not a full generated SDK. */
internal abstract class BaseGoogleApiClient(protected val http: HttpClient, private val serviceName: String) {

    protected suspend fun getJson(url: String, accessToken: String): JsonObject =
        parse(http.get(url) { bearerJson(accessToken) })

    protected suspend fun postJson(url: String, accessToken: String, body: JsonObject): JsonObject =
        parse(http.post(url) { bearerJson(accessToken); setBody(body) })

    protected suspend fun putJson(url: String, accessToken: String, body: JsonObject): JsonObject =
        parse(http.put(url) { bearerJson(accessToken); setBody(body) })

    protected suspend fun patchJson(url: String, accessToken: String, body: JsonObject): JsonObject =
        parse(http.patch(url) { bearerJson(accessToken); setBody(body) })

    /** Simple (non-multipart) media upload — raw bytes, no JSON metadata in the same call. Drive
     *  metadata (name/parent folder) is set with a follow-up [patchJson], per this file's class
     *  doc: two plain requests instead of a hand-built multipart/related body. */
    protected suspend fun postBytes(url: String, accessToken: String, bytes: ByteArray, mimeType: String): JsonObject =
        parse(
            http.post(url) {
                header("Authorization", "Bearer $accessToken")
                contentType(ContentType.parse(mimeType))
                setBody(bytes)
            }
        )

    private fun HttpRequestBuilder.bearerJson(accessToken: String) {
        header("Authorization", "Bearer $accessToken")
        contentType(ContentType.Application.Json)
    }

    private suspend fun parse(response: HttpResponse): JsonObject {
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw GoogleApiException(serviceName, response.status.value, text)
        }
        if (text.isBlank()) return JsonObject(emptyMap())
        return runCatching { Json.parseToJsonElement(text).jsonObject }
            .getOrElse { throw GoogleApiException(serviceName, response.status.value, "Non-JSON response: $text") }
    }
}

data class DriveFolderStructure(val rootFolderId: String, val subfolderIds: Map<String, String>)

/** Drive v3 — app data lives entirely under one root folder ("Inventory Smart AI") with the
 *  spec's exact subfolder layout, created lazily the first time each is needed. Requested scope
 *  is `drive.file` only (per Phase 4's "least privilege" requirement): this backend can only see
 *  files it itself created (or the user explicitly opened with this app), never the user's whole
 *  Drive. */
class DriveClient(http: HttpClient) : BaseGoogleApiClient(http, "Drive") {
    companion object {
        const val ROOT_FOLDER_NAME = "Inventory Smart AI"
        val SUBFOLDERS = listOf("Imports", "Invoices", "PurchaseRequests", "Counting", "Goals", "Reports", "Images", "Backups")
        private const val FOLDER_MIME = "application/vnd.google-apps.folder"
        private const val FILES_URL = "https://www.googleapis.com/drive/v3/files"
    }

    /** Finds the app's root folder by name (drive.file only shows files this app created, so a
     *  name search here cannot collide with an unrelated folder of the same name owned by
     *  someone else / created by another app) or creates it, then does the same for every
     *  subfolder. Safe to call every time — never creates a duplicate (spec: "do not duplicate
     *  files unnecessarily"). */
    suspend fun ensureFolderStructure(accessToken: String): DriveFolderStructure {
        val rootId = findOrCreateFolder(accessToken, ROOT_FOLDER_NAME, parentId = null)
        val subfolders = SUBFOLDERS.associateWith { name -> findOrCreateFolder(accessToken, name, parentId = rootId) }
        return DriveFolderStructure(rootId, subfolders)
    }

    private suspend fun findOrCreateFolder(accessToken: String, name: String, parentId: String?): String {
        val parentClause = if (parentId != null) " and '$parentId' in parents" else " and 'root' in parents"
        val query = "name = '${name.replace("'", "\\'")}' and mimeType = '$FOLDER_MIME' and trashed = false$parentClause"
        val listUrl = "$FILES_URL?q=${urlEncode(query)}&fields=files(id,name)&spaces=drive"
        val existing = getJson(listUrl, accessToken)["files"]?.jsonArray?.firstOrNull()?.jsonObject
        existing?.get("id")?.jsonPrimitive?.contentOrNull?.let { return it }

        val body = buildJsonObject {
            put("name", name)
            put("mimeType", FOLDER_MIME)
            if (parentId != null) putJsonArray("parents") { add(parentId) }
        }
        val created = postJson(FILES_URL, accessToken, body)
        return created["id"]!!.jsonPrimitive.content
    }

    /** Uploads [bytes] into [folderId] and returns the new file's id + a user-openable link. */
    suspend fun uploadFile(accessToken: String, folderId: String, fileName: String, mimeType: String, bytes: ByteArray): DriveUploadResult {
        val uploaded = postBytes("https://www.googleapis.com/upload/drive/v3/files?uploadType=media", accessToken, bytes, mimeType)
        val fileId = uploaded["id"]!!.jsonPrimitive.content
        val metadata = buildJsonObject { put("name", fileName); putJsonArray("parents") { add(folderId) } }
        patchJson("$FILES_URL/$fileId?fields=id,webViewLink", accessToken, metadata)
        val withLink = getJson("$FILES_URL/$fileId?fields=id,webViewLink", accessToken)
        return DriveUploadResult(
            fileId = fileId,
            webViewLink = withLink["webViewLink"]?.jsonPrimitive?.contentOrNull
        )
    }

    /** Moves an existing file (e.g. a just-created Google Doc, which the Docs API creates at
     *  Drive root) into one of this app's subfolders. */
    suspend fun moveIntoFolder(accessToken: String, fileId: String, folderId: String) {
        patchJson("$FILES_URL/$fileId?addParents=$folderId&fields=id,parents", accessToken, JsonObject(emptyMap()))
    }

    suspend fun listFiles(accessToken: String, folderId: String): JsonObject =
        getJson("$FILES_URL?q=${urlEncode("'$folderId' in parents and trashed = false")}&fields=files(id,name,mimeType,webViewLink,modifiedTime)", accessToken)

    private fun urlEncode(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")
}

data class DriveUploadResult(val fileId: String, val webViewLink: String?)

/** Sheets v4 — one master workbook, detected by [findMasterSpreadsheetId] (looked up as a plain
 *  Drive file by name inside the app's own folder — `drive.file` scope is sufficient for the
 *  Sheets API to then read/write its values, per Google's own Sheets API auth guide, as long as
 *  the file was created or opened through this app's `drive.file` grant). */
class SheetsClient(http: HttpClient, private val drive: DriveClient) : BaseGoogleApiClient(http, "Sheets") {
    companion object {
        const val MASTER_SPREADSHEET_NAME = "Inventory Smart AI — Master"
        val SHEET_NAMES = listOf(
            "Products", "Branches", "Categories", "Inventory", "Counting", "Goals",
            "Commissions", "PurchaseRequests", "PurchaseReceipts", "Sales", "Customers", "Suppliers", "AIReports"
        )
    }

    suspend fun createMasterSpreadsheet(accessToken: String, folderId: String): String {
        val body = buildJsonObject {
            put("properties", buildJsonObject { put("title", MASTER_SPREADSHEET_NAME) })
            putJsonArray("sheets") {
                SHEET_NAMES.forEach { name -> add(buildJsonObject { put("properties", buildJsonObject { put("title", name) }) }) }
            }
        }
        val created = postJson("https://sheets.googleapis.com/v4/spreadsheets", accessToken, body)
        val spreadsheetId = created["spreadsheetId"]!!.jsonPrimitive.content
        drive.moveIntoFolder(accessToken, spreadsheetId, folderId)
        return spreadsheetId
    }

    /** Overwrites [sheetName]'s content starting at A1 with [headerRow] + [rows] — simplest
     *  correct "export" semantics (spec just asks to "export Products/Inventory/..."; overwriting
     *  the whole sheet on every export avoids ever-growing duplicate rows from repeated exports,
     *  which [appendRows] is for instead). */
    suspend fun exportSheet(accessToken: String, spreadsheetId: String, sheetName: String, headerRow: List<String>, rows: List<List<String>>) {
        clearSheet(accessToken, spreadsheetId, sheetName)
        val range = "$sheetName!A1"
        val body = buildJsonObject {
            put("range", range)
            put("majorDimension", "ROWS")
            putJsonArray("values") {
                add(buildJsonArray { headerRow.forEach { add(it) } })
                rows.forEach { row -> add(buildJsonArray { row.forEach { add(it) } }) }
            }
        }
        putJson(
            "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/${urlEncode(range)}?valueInputOption=USER_ENTERED",
            accessToken,
            body
        )
    }

    suspend fun appendRows(accessToken: String, spreadsheetId: String, sheetName: String, rows: List<List<String>>) {
        val body = buildJsonObject {
            putJsonArray("values") { rows.forEach { row -> add(buildJsonArray { row.forEach { add(it) } }) } }
        }
        postJson(
            "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/${urlEncode("$sheetName!A1")}:append?valueInputOption=USER_ENTERED",
            accessToken,
            body
        )
    }

    suspend fun readSheet(accessToken: String, spreadsheetId: String, sheetName: String): JsonObject =
        getJson("https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/${urlEncode(sheetName)}", accessToken)

    private suspend fun clearSheet(accessToken: String, spreadsheetId: String, sheetName: String) {
        postJson(
            "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/${urlEncode(sheetName)}:clear",
            accessToken,
            JsonObject(emptyMap())
        )
    }

    private fun urlEncode(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")
}

/** Docs v1 — a report is created as a brand-new Google Doc (title + body text via one
 *  batchUpdate insertText request), then filed into the Reports/Invoices/... folder via Drive. */
class DocsClient(http: HttpClient, private val drive: DriveClient) : BaseGoogleApiClient(http, "Docs") {
    suspend fun createReportDocument(accessToken: String, folderId: String, title: String, bodyText: String): DriveUploadResult {
        val created = postJson("https://docs.googleapis.com/v1/documents", accessToken, buildJsonObject { put("title", title) })
        val documentId = created["documentId"]!!.jsonPrimitive.content

        val batchUpdateBody = buildJsonObject {
            putJsonArray("requests") {
                add(
                    buildJsonObject {
                        put(
                            "insertText",
                            buildJsonObject {
                                put("location", buildJsonObject { put("index", 1) })
                                put("text", bodyText)
                            }
                        )
                    }
                )
            }
        }
        postJson("https://docs.googleapis.com/v1/documents/$documentId:batchUpdate", accessToken, batchUpdateBody)
        drive.moveIntoFolder(accessToken, documentId, folderId)

        val fileMeta = getJson("https://www.googleapis.com/drive/v3/files/$documentId?fields=id,webViewLink", accessToken)
        return DriveUploadResult(fileId = documentId, webViewLink = fileMeta["webViewLink"]?.jsonPrimitive?.contentOrNull)
    }
}

/** Gmail v1 — send-only (`gmail.send` scope: this app can never read the user's inbox). Builds a
 *  minimal RFC 2822 message by hand (To/Subject/Content-Type headers + a blank line + body) since
 *  a plain-text email with at most one optional link needs nothing heavier than that. */
class GmailClient(http: HttpClient) : BaseGoogleApiClient(http, "Gmail") {
    suspend fun sendEmail(accessToken: String, to: String, subject: String, body: String, attachmentLink: String? = null) {
        val fullBody = if (attachmentLink != null) "$body\n\n---\n$attachmentLink" else body
        val message = buildString {
            append("To: $to\r\n")
            append("Subject: $subject\r\n")
            append("Content-Type: text/plain; charset=\"UTF-8\"\r\n")
            append("\r\n")
            append(fullBody)
        }
        val raw = Base64.getUrlEncoder().withoutPadding().encodeToString(message.toByteArray(Charsets.UTF_8))
        postJson(
            "https://gmail.googleapis.com/gmail/v1/users/me/messages/send",
            accessToken,
            buildJsonObject { put("raw", raw) }
        )
    }
}

/** Calendar v3 — events on the user's primary calendar only (`calendar.events` scope: this app
 *  can create/edit events but cannot read or change calendar settings/other calendars). */
class CalendarClient(http: HttpClient) : BaseGoogleApiClient(http, "Calendar") {
    suspend fun createEvent(accessToken: String, title: String, startIso: String, endIso: String, description: String?): JsonObject {
        val body = buildJsonObject {
            put("summary", title)
            description?.let { put("description", it) }
            put("start", buildJsonObject { put("dateTime", startIso) })
            put("end", buildJsonObject { put("dateTime", endIso) })
        }
        return postJson("https://www.googleapis.com/calendar/v3/calendars/primary/events", accessToken, body)
    }
}
