package com.inventorysmartai.app.domain.assistant

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantUiBridgeTest {

    @Test
    fun `queued commands are counted`() {
        val bridge = AssistantUiBridge()
        assertEquals(0, bridge.queuedCount)
        assertTrue(bridge.post(UiCommand.Navigate("inventory")))
        assertTrue(bridge.post(UiCommand.Navigate("home")))
        assertEquals(2, bridge.queuedCount)
    }

    @Test
    fun `commands arrive in the order they were posted, each once`() = runBlocking {
        val bridge = AssistantUiBridge()
        bridge.post(UiCommand.Navigate("first"))
        bridge.post(UiCommand.Navigate("second"))
        assertEquals(UiCommand.Navigate("first"), bridge.commands.first())
        assertEquals(UiCommand.Navigate("second"), bridge.commands.first())
    }

    @Test
    fun `a notice is kept until it is cleared, and a new one replaces it`() {
        val bridge = AssistantUiBridge()
        assertNull(bridge.notice.value)
        bridge.postNotice("الأول")
        val first = bridge.notice.value!!
        assertEquals("الأول", first.text)
        bridge.postNotice("الثاني")
        val second = bridge.notice.value!!
        assertEquals("الثاني", second.text)
        assertNotEquals("each notice has its own id, so the auto-hide timer restarts", first.id, second.id)
        bridge.clearNotice()
        assertNull(bridge.notice.value)
    }

    @Test
    fun `a blank notice is ignored`() {
        val bridge = AssistantUiBridge()
        bridge.postNotice("   ")
        assertNull(bridge.notice.value)
    }
}
