package com.lunatic.miniclaw.data.remote.model.minimax.mapper

import com.lunatic.miniclaw.domain.model.model.ChatStreamEvent
import com.lunatic.miniclaw.domain.model.model.ModelCallError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniMaxStreamMapperTest {
    private val mapper = MiniMaxStreamMapper()

    @Test
    fun `toEvents parses sse delta and done`() {
        val raw = """
            data: {"delta":"你"}
            data: {"delta":"好"}
            data: [DONE]
        """.trimIndent()

        val events = mapper.toEvents(raw)

        assertEquals(3, events.size)
        assertEquals(ChatStreamEvent.Delta("你"), events[0])
        assertEquals(ChatStreamEvent.Delta("好"), events[1])
        assertEquals(ChatStreamEvent.Completed, events[2])
    }

    @Test
    fun `toEvents maps error payload to failed`() {
        val raw = """data: {"error":{"code":"401","message":"unauthorized"}}"""

        val events = mapper.toEvents(raw)

        assertEquals(1, events.size)
        assertEquals(ChatStreamEvent.Failed(ModelCallError.AuthFailed), events.first())
    }

    @Test
    fun `toEvents parses non_sse json and appends completed`() {
        val raw = """{"choices":[{"message":{"content":"hello"}}]}"""

        val events = mapper.toEvents(raw)

        assertEquals(2, events.size)
        assertEquals(ChatStreamEvent.Delta("hello"), events[0])
        assertTrue(events.last() is ChatStreamEvent.Completed)
    }
}
