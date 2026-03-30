package com.lunatic.miniclaw.data.remote.datasource

import com.lunatic.miniclaw.data.remote.model.ChatStreamEvent
import com.lunatic.miniclaw.data.remote.model.ChatStreamRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeChatRemoteDataSource : ChatRemoteDataSource {
    override fun streamChat(request: ChatStreamRequest): Flow<ChatStreamEvent> = flow {
        emit(ChatStreamEvent.Started)
        delay(250)

        val response = "我收到了你的消息：${request.userText}。这是 MiniClaw P1 的模拟流式回复。"
        response.chunked(CHUNK_SIZE).forEach { chunk ->
            emit(ChatStreamEvent.Delta(chunk))
            delay(CHUNK_DELAY_MS)
        }

        emit(ChatStreamEvent.Completed)
    }

    private companion object {
        private const val CHUNK_SIZE = 6
        private const val CHUNK_DELAY_MS = 120L
    }
}
