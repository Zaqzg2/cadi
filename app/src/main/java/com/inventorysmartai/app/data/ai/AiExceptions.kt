package com.inventorysmartai.app.data.ai

/**
 * Failures the AI extraction path raises itself (the backend path reports server-side problems as `BackendFailure`s
 * directly; the direct-provider path maps these through AiProviderFailureMapper), so the rest of the import flow sees one
 * failure type either way.
 */

/** Refused locally: the whole file is sent inline in one request. */
class AiFileTooLargeException(val sizeBytes: Int, val limitBytes: Int) :
    Exception("File is $sizeBytes bytes; the inline limit is $limitBytes")

/** The model answered, but not with usable, schema-shaped JSON. */
class AiInvalidOutputException(message: String) : Exception(message)

/** No answer within [AiConfig.REQUEST_TIMEOUT_MS] — raised so the analysis screen can never spin forever. */
class AiTimeoutException : Exception("AI request timed out")
