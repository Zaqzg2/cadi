package com.inventorysmartai.backend.routes

import com.inventorysmartai.backend.assistant.AssistantOrchestrator
import com.inventorysmartai.backend.assistant.AssistantTurnResult
import com.inventorysmartai.backend.gemini.GeminiFunctionResultInput
import com.inventorysmartai.backend.gemini.ToolCatalog
import com.inventorysmartai.backend.google.BackendToolExecutor
import com.inventorysmartai.backend.routes.dto.AssistantTurnResponseDto
import com.inventorysmartai.backend.routes.dto.ContinueRequest
import com.inventorysmartai.backend.routes.dto.ExecuteBackendToolRequest
import com.inventorysmartai.backend.routes.dto.PendingToolCallDto
import com.inventorysmartai.backend.routes.dto.SendMessageRequest
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

private fun AssistantTurnResult.toDto(conversationId: String): AssistantTurnResponseDto = when (this) {
    is AssistantTurnResult.Final -> AssistantTurnResponseDto(type = "final", conversationId = conversationId, text = text)
    is AssistantTurnResult.ToolCallsRequested -> AssistantTurnResponseDto(
        type = "toolCalls",
        conversationId = conversationId,
        calls = calls.map { PendingToolCallDto(it.callId, it.name, it.arguments, it.site.name, it.risk.name) }
    )
}

fun Route.assistantRoutes(orchestrator: AssistantOrchestrator, backendToolExecutor: BackendToolExecutor) {
    post("/v1/assistant/message") {
        val request = call.receive<SendMessageRequest>()
        val result = orchestrator.sendUserMessage(
            conversationId = request.conversationId,
            sessionId = request.sessionId,
            message = request.message,
            contextText = request.context
        )
        call.respond(result.toDto(request.conversationId))
    }

    /** The app calls this after it silently executed one or more [com.inventorysmartai.backend.
     *  gemini.ToolExecutionSite.LOCAL] read-only tool calls, or after the user confirmed and the
     *  app performed a LOCAL write (createPurchaseRequest) against Room. */
    post("/v1/assistant/continue") {
        val request = call.receive<ContinueRequest>()
        val results = request.results.map { GeminiFunctionResultInput(it.callId, it.name, it.resultText) }
        val result = orchestrator.continueWithResults(request.conversationId, results)
        call.respond(result.toDto(request.conversationId))
    }

    /** The app calls this after the user has already been shown (and answered) the Arabic
     *  confirmation dialog for one [com.inventorysmartai.backend.gemini.ToolExecutionSite.BACKEND]
     *  write tool (saveReportToDrive / createGoogleDoc / createCalendarEvent / sendEmail). If
     *  [ExecuteBackendToolRequest.approved] is false, the action is never performed — Gemini is
     *  simply told the user declined, so it can respond appropriately in text. */
    post("/v1/assistant/executeBackendTool") {
        val request = call.receive<ExecuteBackendToolRequest>()
        require(request.name in ToolCatalog.backendToolNames) { "${request.name} is not a backend tool" }

        val resultText = if (request.approved) {
            val outcome = backendToolExecutor.execute(request.name, request.sessionId, request.arguments)
            outcome.toString()
        } else {
            """{"status":"declined_by_user"}"""
        }

        val result = orchestrator.continueWithResults(
            request.conversationId,
            listOf(GeminiFunctionResultInput(request.callId, request.name, resultText))
        )
        call.respond(result.toDto(request.conversationId))
    }
}
