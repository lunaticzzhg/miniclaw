package com.lunatic.miniclaw.data.remote.datasource

import com.lunatic.miniclaw.data.remote.model.ChatStreamEvent
import com.lunatic.miniclaw.data.remote.model.ChatStreamRequest
import kotlinx.coroutines.flow.Flow

interface ChatRemoteDataSource {
    suspend fun startChat(request: ChatStreamRequest)
    fun streamChat(request: ChatStreamRequest): Flow<ChatStreamEvent>
}
