package com.inventorysmartai.app.data.ai

/**
 * Failures the AI extraction path raises itself, before or after the Firebase SDK is involved. The SDK's
 * own failures are FirebaseAIException subclasses; FirebaseAiDocumentImportRepositoryImpl turns
 * both kinds into user-facing Arabic `BackendFailure`s, so the rest of the import flow is unchanged.
 */

/** The build had no google-services.json, so Firebase never initialised. Nothing was sent. */
class AiNotConfiguredException : Exception("Firebase is not initialised in this build (no google-services.json)")

/** Refused locally: the whole file is sent inline in one request. */
class AiFileTooLargeException(val sizeBytes: Int, val limitBytes: Int) :
    Exception("File is $sizeBytes bytes; the inline limit is $limitBytes")

/** The model answered, but not with usable, schema-shaped JSON. */
class AiInvalidOutputException(message: String) : Exception(message)
