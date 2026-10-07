package com.inventorysmartai.backend.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ServerConfigTest {
    private val appKey = "k".repeat(24)

    private fun problemsOf(env: Map<String, String>): List<String> = try {
        ServerConfig.fromEnv(env)
        fail("expected a ConfigException")
        emptyList()
    } catch (e: ConfigException) {
        e.problems
    }

    @Test
    fun `a minimal environment is enough`() {
        val config = ServerConfig.fromEnv(mapOf("APP_API_KEY" to appKey, "GROQ_API_KEY" to "gsk_secret_value"))
        assertEquals(listOf("groq"), config.providers.map { it.id })
        assertEquals(8080, config.port)
        assertNull("Google is optional", config.google)
        assertEquals(appKey, config.appApiKey)
    }

    @Test
    fun `provider order follows AI_PROVIDER_ORDER and unlisted providers come last`() {
        val config = ServerConfig.fromEnv(
            mapOf(
                "APP_API_KEY" to appKey,
                "MISTRAL_API_KEY" to "m", "GROQ_API_KEY" to "g", "OPENROUTER_API_KEY" to "o",
                "AI_PROVIDER_ORDER" to "openrouter, groq"
            )
        )
        assertEquals(listOf("openrouter", "groq", "mistral"), config.providers.map { it.id })
    }

    @Test
    fun `default order is mistral then groq then openrouter`() {
        val config = ServerConfig.fromEnv(
            mapOf("APP_API_KEY" to appKey, "OPENROUTER_API_KEY" to "o", "GROQ_API_KEY" to "g", "MISTRAL_API_KEY" to "m")
        )
        assertEquals(listOf("mistral", "groq", "openrouter"), config.providers.map { it.id })
    }

    @Test
    fun `only mistral has an OCR model by default`() {
        val config = ServerConfig.fromEnv(
            mapOf("APP_API_KEY" to appKey, "MISTRAL_API_KEY" to "m", "GROQ_API_KEY" to "g")
        )
        assertTrue(config.providers.first { it.id == "mistral" }.supportsOcr)
        assertFalse(config.providers.first { it.id == "groq" }.supportsOcr)
    }

    @Test
    fun `model ids can be overridden from the environment`() {
        val config = ServerConfig.fromEnv(
            mapOf("APP_API_KEY" to appKey, "GROQ_API_KEY" to "g", "GROQ_TEXT_MODEL" to "my/text", "GROQ_VISION_MODEL" to "my/vision")
        )
        val groq = config.providers.single()
        assertEquals("my/text", groq.textModel)
        assertEquals("my/vision", groq.visionModel)
    }

    @Test
    fun `everything wrong is reported at once`() {
        val problems = problemsOf(emptyMap())
        assertTrue(problems.any { it.contains("APP_API_KEY") })
        assertTrue(problems.any { it.contains("AI provider") })
    }

    @Test
    fun `app auth can be disabled only explicitly`() {
        val config = ServerConfig.fromEnv(mapOf("APP_AUTH_DISABLED" to "true", "GROQ_API_KEY" to "g"))
        assertNull(config.appApiKey)
    }

    @Test
    fun `a short app key is rejected`() {
        assertTrue(problemsOf(mapOf("APP_API_KEY" to "short", "GROQ_API_KEY" to "g")).any { it.contains("16") })
    }

    @Test
    fun `google needs its encryption key`() {
        val problems = problemsOf(
            mapOf("APP_API_KEY" to appKey, "GROQ_API_KEY" to "g", "GOOGLE_OAUTH_CLIENT_ID" to "id", "GOOGLE_OAUTH_CLIENT_SECRET" to "secret")
        )
        assertTrue(problems.any { it.contains("TOKEN_ENCRYPTION_KEY") })
    }

    @Test
    fun `google needs both id and secret`() {
        val problems = problemsOf(
            mapOf("APP_API_KEY" to appKey, "GROQ_API_KEY" to "g", "GOOGLE_OAUTH_CLIENT_ID" to "id", "TOKEN_ENCRYPTION_KEY" to "x".repeat(40))
        )
        assertTrue(problems.any { it.contains("GOOGLE_OAUTH_CLIENT_SECRET") })
    }

    @Test
    fun `full google configuration is accepted`() {
        val config = ServerConfig.fromEnv(
            mapOf(
                "APP_API_KEY" to appKey, "GROQ_API_KEY" to "g",
                "GOOGLE_OAUTH_CLIENT_ID" to "id", "GOOGLE_OAUTH_CLIENT_SECRET" to "secret",
                "TOKEN_ENCRYPTION_KEY" to "x".repeat(40)
            )
        )
        assertNotNull(config.google)
        assertEquals("", config.google?.redirectUri)
    }

    @Test
    fun `a custom provider needs all three settings`() {
        val problems = problemsOf(mapOf("APP_API_KEY" to appKey, "CUSTOM_AI_BASE_URL" to "https://x.test/v1"))
        assertTrue(problems.any { it.contains("CUSTOM_AI") })
    }

    @Test
    fun `a custom provider gets a trailing slash`() {
        val config = ServerConfig.fromEnv(
            mapOf(
                "APP_API_KEY" to appKey,
                "CUSTOM_AI_BASE_URL" to "https://x.test/v1", "CUSTOM_AI_API_KEY" to "c", "CUSTOM_AI_TEXT_MODEL" to "m1"
            )
        )
        val custom = config.providers.single()
        assertEquals("https://x.test/v1/", custom.baseUrl)
        assertEquals("m1", custom.visionModel)
    }

    @Test
    fun `a non numeric number is reported`() {
        assertTrue(problemsOf(mapOf("APP_API_KEY" to appKey, "GROQ_API_KEY" to "g", "PORT" to "abc")).any { it.contains("PORT") })
    }

    @Test
    fun `no secret ever appears in toString or describe`() {
        val config = ServerConfig.fromEnv(
            mapOf(
                "APP_API_KEY" to "super-secret-app-key-123", "GROQ_API_KEY" to "gsk_secret_value",
                "GOOGLE_OAUTH_CLIENT_ID" to "id", "GOOGLE_OAUTH_CLIENT_SECRET" to "google-client-secret",
                "TOKEN_ENCRYPTION_KEY" to "t".repeat(40)
            )
        )
        val printed = config.toString() + config.describe().joinToString("\n") + config.providers.joinToString() + config.google.toString()
        listOf("super-secret-app-key-123", "gsk_secret_value", "google-client-secret", "t".repeat(40)).forEach {
            assertFalse("leaked: $it", printed.contains(it))
        }
    }
}
