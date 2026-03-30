package com.lunatic.miniclaw.domain.chat.repository

import com.lunatic.miniclaw.domain.chat.model.ChatMessage
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeMessages(sessionId: String): Flow<List<ChatMessage>>
    suspend fun sendUserMessage(sessionId: String, text: String)
    suspend fun retryUserMessage(messageId: String)
    suspend fun retryAssistantMessage(messageId: String)
    suspend fun stopStreaming(requestId: String)
}
