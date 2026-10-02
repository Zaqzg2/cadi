package com.inventorysmartai.app.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCheckDiagnosticsTest {

    private val secret = "123a4567-b89c-12d3-e456-789012345678"

    @Test
    fun `recognises the App Check refusals Firebase AI Logic sends, by their text`() {
        listOf(
            "To access this model, you must enforce Firebase App Check.",
            "Firebase AI Logic has been deactivated in this project. To resume using Firebase AI Logic, you must enforce Firebase App Check.",
            "This AI Logic Project is inactive. Please complete onboarding and enable App Check to allow API Calls.",
            "AppCheck token fetch failed"
        ).forEach { text ->
            assertTrue(text, AppCheckDiagnostics.isAppCheckRejection(RuntimeException(text)))
        }
    }

    @Test
    fun `looks through the cause chain`() {
        val wrapped = RuntimeException("Server error", IllegalStateException("Firebase App Check token is invalid"))
        assertTrue(AppCheckDiagnostics.isAppCheckRejection(wrapped))
    }

    @Test
    fun `ignores failures that have nothing to do with App Check`() {
        assertFalse(AppCheckDiagnostics.isAppCheckRejection(RuntimeException("Quota exceeded for this project")))
        assertFalse(AppCheckDiagnostics.isAppCheckRejection(RuntimeException("API key not valid")))
        assertFalse(AppCheckDiagnostics.isAppCheckRejection(RuntimeException()))
    }

    @Test
    fun `finds the debug secret in the sentence Google documents`() {
        val line = "D DebugAppCheckProvider: Enter this debug secret into the allow list in the Firebase Console for your project: $secret"
        assertEquals(secret, AppCheckDiagnostics.findDebugSecret(line))
    }

    @Test
    fun `works on raw logcat output and returns the newest secret`() {
        val older = "aaaaaaaa-1111-2222-3333-bbbbbbbbbbbb"
        val log = listOf(
            "some unrelated line 11111111-2222-3333-4444-555555555555",
            "Enter this debug secret into the allow list in the Firebase Console for your project: $older",
            "another unrelated line",
            "Enter this debug secret into the allow list in the Firebase Console for your project: ${secret.uppercase()}"
        ).joinToString("\n")
        assertEquals(secret.uppercase(), AppCheckDiagnostics.findDebugSecret(log))
    }

    @Test
    fun `returns null when the line or the token is missing`() {
        assertNull(AppCheckDiagnostics.findDebugSecret(""))
        assertNull(AppCheckDiagnostics.findDebugSecret("a UUID alone is not enough: $secret"))
        assertNull(AppCheckDiagnostics.findDebugSecret("Enter this debug secret into the allow list: not-a-token"))
    }
}
