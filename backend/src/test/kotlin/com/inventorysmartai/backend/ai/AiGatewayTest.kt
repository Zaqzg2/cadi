package com.inventorysmartai.backend.ai

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AiGatewayTest {
    private var now = 1_000_000L

    private fun gateway(transport: FakeTransport, vararg ids: String, providerTimeoutMs: Long = 5_000, totalTimeoutMs: Long = 20_000) =
        AiGateway(ids.map { testProvider(it) }, transport, providerTimeoutMs, totalTimeoutMs, clock = { now })

    private suspend fun AiGateway.ask(): AiGateway.ChatReply = chat(listOf(userMessage("hi")), emptyList(), 0.2, 100)

    @Test
    fun `the first provider answers`() = runBlocking {
        val transport = FakeTransport { _, _ -> chatResponse("answer") }
        val reply = gateway(transport, "a", "b").ask()
        assertEquals("answer", reply.text)
        assertEquals("a", reply.providerId)
        assertEquals("text-a", reply.model)
        assertEquals(listOf("a"), transport.chatCalls)
    }

    @Test
    fun `a rate limited provider is skipped and then remembered`() = runBlocking {
        val transport = FakeTransport { p, _ ->
            if (p.id == "a") throw ProviderHttpException("a", 429, "slow down", 30) else chatResponse("from ${p.id}")
        }
        val gateway = gateway(transport, "a", "b", "c")

        assertEquals("b", gateway.ask().providerId)
        assertEquals("b", gateway.ask().providerId)
        assertEquals("a is only tried once while it cools down", listOf("a", "b", "b"), transport.chatCalls)

        now += 31_000
        gateway.ask()
        assertEquals("after the cooldown a is tried again", "a", transport.chatCalls[3])
    }

    @Test
    fun `a rejected key cools down for ten minutes`() = runBlocking {
        val transport = FakeTransport { p, _ ->
            if (p.id == "a") throw ProviderHttpException("a", 401, "bad key") else chatResponse("ok")
        }
        val gateway = gateway(transport, "a", "b")
        gateway.ask()
        now += 5 * 60_000
        gateway.ask()
        assertEquals(listOf("a", "b", "b"), transport.chatCalls)
        now += 6 * 60_000
        gateway.ask()
        assertEquals("a", transport.chatCalls[3])
    }

    @Test
    fun `an unusable answer falls through without a cooldown`() = runBlocking {
        val transport = FakeTransport { p, _ ->
            if (p.id == "a") throw ProviderEmptyException("a") else chatResponse("ok")
        }
        val gateway = gateway(transport, "a", "b")
        gateway.ask()
        gateway.ask()
        assertEquals(listOf("a", "b", "a", "b"), transport.chatCalls)
    }

    @Test
    fun `when everything fails the failures are reported`() = runBlocking {
        val transport = FakeTransport { p, _ -> throw ProviderHttpException(p.id, 429, "limit", if (p.id == "a") 40 else 15) }
        try {
            gateway(transport, "a", "b").ask()
            fail("expected AiUnavailableException")
        } catch (e: AiUnavailableException) {
            assertEquals(2, e.failures.size)
            assertTrue(e.allRateLimited)
            assertEquals(15L, e.retryAfterSeconds)
        }
    }

    @Test
    fun `when everything is cooling down it fails fast without calling anyone`() = runBlocking {
        val transport = FakeTransport { p, _ -> throw ProviderHttpException(p.id, 429, "limit", 60) }
        val gateway = gateway(transport, "a")
        try { gateway.ask() } catch (e: AiUnavailableException) { /* first failure */ }
        val callsBefore = transport.chatCalls.size
        try {
            gateway.ask()
            fail("expected AiUnavailableException")
        } catch (e: AiUnavailableException) {
            assertTrue(e.allRateLimited)
        }
        assertEquals(callsBefore, transport.chatCalls.size)
    }

    @Test
    fun `a slow provider times out and the next one answers`() = runBlocking {
        val transport = FakeTransport { p, _ ->
            if (p.id == "a") { delay(10_000); chatResponse("too late") } else chatResponse("on time")
        }
        val gateway = gateway(transport, "a", "b", providerTimeoutMs = 150)
        assertEquals("b", gateway.ask().providerId)
        val a = gateway.status().first { it.id == "a" }
        assertFalse("a timed-out provider cools down briefly", a.ready)
        assertEquals("TIMEOUT", a.lastFailure)
    }

    @Test
    fun `status reports ready providers`() = runBlocking {
        val transport = FakeTransport { _, _ -> chatResponse("ok") }
        val gateway = gateway(transport, "a", "b")
        assertTrue(gateway.status().all { it.ready })
        assertNull(gateway.status().first().lastFailure)
    }

    @Test
    fun `tools reach the provider and tool calls come back`() = runBlocking {
        val transport = FakeTransport { _, _ ->
            kotlinx.serialization.json.Json.parseToJsonElement(
                """{"choices":[{"message":{"content":null,"tool_calls":[{"id":"c1","type":"function","function":{"name":"getX","arguments":"{}"}}]},"finish_reason":"tool_calls"}]}"""
            ) as kotlinx.serialization.json.JsonObject
        }
        val tool = kotlinx.serialization.json.Json.parseToJsonElement("""{"type":"function","function":{"name":"getX"}}""") as kotlinx.serialization.json.JsonObject
        val reply = gateway(transport, "a").chat(listOf(userMessage("go")), listOf(tool), 0.2, 100)
        assertNull(reply.text)
        assertEquals(1, reply.toolCalls.size)
        assertTrue(transport.chatBodies.single().containsKey("tools"))
    }
}
