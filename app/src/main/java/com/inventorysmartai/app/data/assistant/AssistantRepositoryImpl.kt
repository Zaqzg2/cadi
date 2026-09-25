package com.inventorysmartai.app.data.assistant

import com.inventorysmartai.app.data.remote.BackendApi
import com.inventorysmartai.app.data.remote.BackendFailure
import com.inventorysmartai.app.data.remote.safeApiCall
import com.inventorysmartai.app.data.remote.dto.AssistantTurnResponseDto
import com.inventorysmartai.app.data.remote.dto.ContinueRequest
import com.inventorysmartai.app.data.remote.dto.ExecuteBackendToolRequest
import com.inventorysmartai.app.data.remote.dto.PendingToolCallDto
import com.inventorysmartai.app.data.remote.dto.SendMessageRequest
import com.inventorysmartai.app.data.remote.dto.ToolResultDto
import com.inventorysmartai.app.domain.assistant.AssistantContext
import com.inventorysmartai.app.domain.assistant.AssistantStepResult
import com.inventorysmartai.app.domain.assistant.LocalToolExecutor
import com.inventorysmartai.app.domain.assistant.ToolExecutionSite
import com.inventorysmartai.app.domain.repository.AssistantRepository
import com.inventorysmartai.app.domain.repository.DeviceSessionRepository
import com.squareup.moshi.Moshi
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * See domain/repository/AiRepositories.kt's [AssistantRepository] doc for the overall design.
 * Every call to the backend can come back one of two ways (`AssistantTurnResponseDto.type`):
 *  - "final": done, show the text.
 *  - "toolCalls": one or more tools need to run first. Read-only [ToolExecutionSite.LOCAL] calls
 *    are executed immediately and silently (the app is simply answering Gemini's own question
 *    about the user's data — nothing to confirm); anything else (a write, local or backend) stops
 *    here and surfaces [AssistantStepResult.ConfirmationRequired] instead, per the spec's
 *    "ACTION CONFIRMATION" rule that only read-only actions run without asking first.
 */
@Singleton
class AssistantRepositoryImpl @Inject constructor(
    private val api: BackendApi,
    private val sessionRepository: DeviceSessionRepository,
    private val localToolExecutor: LocalToolExecutor,
    private val moshi: Moshi
) : AssistantRepository {

    /** The one pending write call per conversation, remembered between [sendMessage]/its
     *  follow-ups returning [AssistantStepResult.ConfirmationRequired] and the matching
     *  [confirmPendingAction] — a conversation only ever has one open confirmation at a time
     *  (the UI blocks on it before the person can send anything else). */
    private val pendingByConversation = ConcurrentHashMap<String, PendingToolCallDto>()

    override suspend fun sendMessage(conversationId: String, message: String, context: AssistantContext?): AssistantStepResult {
        return runCatching {
            val sessionId = sessionRepository.getSessionId()
            val response = safeApiCall(moshi) {
                api.sendAssistantMessage(SendMessageRequest(sessionId, conversationId, message, context?.toPromptText()))
            }.getOrThrow()
            continueLoop(conversationId, response, usedLocalData = false, roundsLeft = MAX_ROUNDS)
        }.getOrElse { AssistantStepResult.Error(mapErrorMessage(it)) }
    }

    override suspend fun confirmPendingAction(conversationId: String, approved: Boolean): AssistantStepResult {
        val pending = pendingByConversation.remove(conversationId)
            ?: return AssistantStepResult.Error("لا يوجد إجراء بانتظار التأكيد لهذه المحادثة")

        return runCatching {
            val sessionId = sessionRepository.getSessionId()
            val response = safeApiCall(moshi) {
                if (pending.site == "LOCAL") {
                    val resultText = if (approved) {
                        localToolExecutor.execute(pending.name, JSONObject(pending.arguments).toString())
                    } else {
                        DECLINED_RESULT_JSON
                    }
                    api.continueAssistant(ContinueRequest(conversationId, listOf(ToolResultDto(pending.callId, pending.name, resultText))))
                } else {
                    api.executeBackendTool(
                        ExecuteBackendToolRequest(conversationId, sessionId, pending.callId, pending.name, pending.arguments, approved)
                    )
                }
            }.getOrThrow()
            continueLoop(conversationId, response, usedLocalData = pending.site == "LOCAL" && approved, roundsLeft = MAX_ROUNDS)
        }.getOrElse { AssistantStepResult.Error(mapErrorMessage(it)) }
    }

    private suspend fun continueLoop(
        conversationId: String,
        response: AssistantTurnResponseDto,
        usedLocalData: Boolean,
        roundsLeft: Int
    ): AssistantStepResult {
        val calls = response.calls.orEmpty()
        if (response.type != "toolCalls" || calls.isEmpty()) {
            return AssistantStepResult.Final(response.text.orEmpty(), usedLocalData)
        }
        if (roundsLeft <= 0) {
            // A genuinely pathological conversation (Gemini keeps asking for more tools without
            // ever reaching a final answer) — stop rather than loop forever burning backend calls.
            return AssistantStepResult.Error("تعذّر إكمال الطلب بعد عدة محاولات، يرجى إعادة صياغة السؤال")
        }

        val readOnlyLocal = calls.filter { it.site == "LOCAL" && it.risk == "READ_ONLY" }
        if (readOnlyLocal.isNotEmpty()) {
            val results = readOnlyLocal.map { call ->
                val resultText = localToolExecutor.execute(call.name, JSONObject(call.arguments).toString())
                ToolResultDto(call.callId, call.name, resultText)
            }
            val next = safeApiCall(moshi) { api.continueAssistant(ContinueRequest(conversationId, results)) }.getOrThrow()
            return continueLoop(conversationId, next, usedLocalData = true, roundsLeft = roundsLeft - 1)
        }

        // Nothing left that can run silently — the next call (local write or any backend call,
        // which is always a write per the tool catalog) needs the person's explicit confirmation.
        val nextCall = calls.first()
        pendingByConversation[conversationId] = nextCall
        val site = if (nextCall.site == "LOCAL") ToolExecutionSite.LOCAL else ToolExecutionSite.BACKEND
        val descriptionAr = if (site == ToolExecutionSite.LOCAL) {
            localToolExecutor.describeForConfirmation(nextCall.name, JSONObject(nextCall.arguments).toString())
        } else {
            describeBackendAction(nextCall)
        }
        return AssistantStepResult.ConfirmationRequired(nextCall.callId, nextCall.name, site, descriptionAr)
    }

    private fun describeBackendAction(call: PendingToolCallDto): String {
        fun arg(key: String) = call.arguments[key]?.toString().orEmpty()
        return when (call.name) {
            "saveReportToDrive" -> "حفظ التقرير \"${arg("title")}\" في Google Drive"
            "createGoogleDoc" -> "إنشاء مستند Google Docs بعنوان \"${arg("title")}\""
            "sendEmail" -> "إرسال بريد إلكتروني إلى ${arg("to")} بعنوان \"${arg("subject")}\""
            "createCalendarEvent" -> "إضافة موعد \"${arg("title")}\" إلى تقويم Google"
            else -> "تنفيذ العملية: ${call.name}"
        }
    }

    private fun mapErrorMessage(e: Throwable): String = when (e) {
        is BackendFailure -> e.messageAr
        else -> "حدث خطأ غير متوقع، يرجى المحاولة مرة أخرى"
    }

    private companion object {
        const val MAX_ROUNDS = 6
        const val DECLINED_RESULT_JSON = """{"status":"declined_by_user"}"""
    }
}
