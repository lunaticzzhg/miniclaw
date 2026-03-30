package com.lunatic.miniclaw.data.remote.datasource

import com.lunatic.miniclaw.data.remote.model.ChatStreamEvent
import com.lunatic.miniclaw.data.remote.model.ChatStreamRequest
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeChatRemoteDataSource : ChatRemoteDataSource {
    override suspend fun startChat(request: ChatStreamRequest) {
        delay(120)
        if (request.userText.contains(SEND_FAIL_FLAG, ignoreCase = true)) {
            throw IOException("Simulated send failure")
        }
    }

    override fun streamChat(request: ChatStreamRequest): Flow<ChatStreamEvent> = flow {
        emit(ChatStreamEvent.Started)
        delay(250)

        val response = "我收到了你的消息：${request.userText}。这是 MiniClaw P1 的模拟流式回复。"
        val chunks = response.chunked(CHUNK_SIZE)
        chunks.forEachIndexed { index, chunk ->
            emit(ChatStreamEvent.Delta(chunk))
            delay(CHUNK_DELAY_MS)
            if (
                request.userText.contains(ASSISTANT_FAIL_FLAG, ignoreCase = true) &&
                index >= chunks.lastIndex / 2
            ) {
                emit(ChatStreamEvent.Failed("Simulated assistant stream failure"))
                return@flow
            }
        }

        emit(ChatStreamEvent.Completed)
    }

    private companion object {
        private const val SEND_FAIL_FLAG = "[send_fail]"
        private const val ASSISTANT_FAIL_FLAG = "[assistant_fail]"
        private const val CHUNK_SIZE = 6
        private const val CHUNK_DELAY_MS = 120L
    }
}
