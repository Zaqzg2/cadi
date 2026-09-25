package com.inventorysmartai.backend.auth

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File

class FileTokenStoreTest {

    private lateinit var tempFile: File
    private lateinit var store: FileTokenStore

    @Before
    fun setUp() {
        tempFile = File.createTempFile("tokens-test", ".json")
        tempFile.delete() // exercise the "file does not exist yet" path too
        // Explicit path — never touches AppConfig (and therefore never requires GEMINI_API_KEY /
        // GOOGLE_OAUTH_CLIENT_ID / GOOGLE_OAUTH_CLIENT_SECRET to be set), so this test runs
        // cleanly in CI with no backend secrets configured.
        store = FileTokenStore(path = tempFile.absolutePath)
    }

    @After
    fun tearDown() {
        tempFile.delete()
    }

    @Test
    fun `get on an empty store returns null`() = runBlocking {
        assertNull(store.get("session-1"))
    }

    @Test
    fun `put then get round-trips the stored tokens`() = runBlocking {
        val tokens = StoredGoogleTokens(
            sessionId = "session-1",
            refreshToken = "refresh-abc",
            accessToken = "access-abc",
            accessTokenExpiresAt = 123456789L,
            grantedScopes = listOf("https://www.googleapis.com/auth/drive.file"),
            linkedAt = 111L
        )
        store.put(tokens)
        assertEquals(tokens, store.get("session-1"))
    }

    @Test
    fun `remove deletes only the requested session`() = runBlocking {
        val a = StoredGoogleTokens("session-a", "r-a", "a-a", 1L, emptyList(), 1L)
        val b = StoredGoogleTokens("session-b", "r-b", "a-b", 1L, emptyList(), 1L)
        store.put(a)
        store.put(b)

        store.remove("session-a")

        assertNull(store.get("session-a"))
        assertEquals(b, store.get("session-b"))
    }

    @Test
    fun `a second store instance pointed at the same file sees previously persisted tokens`() = runBlocking {
        val tokens = StoredGoogleTokens("session-1", "refresh-abc", "access-abc", 1L, listOf("scope-a"), 1L)
        store.put(tokens)

        val reopened = FileTokenStore(path = tempFile.absolutePath)
        assertEquals(tokens, reopened.get("session-1"))
    }
}
